package com.marketai.dashboard.service;

import com.marketai.dashboard.model.CryptoPriceEvent;
import com.marketai.dashboard.model.TimeframeWindow;
import com.marketai.dashboard.model.TimeframeWindow.Timeframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Service
public class MultiTimeframeService {

    private static final Logger log = LoggerFactory.getLogger(MultiTimeframeService.class);

    // ── Per-symbol, per-timeframe candle buffers ───────────────────────────
    // symbol → timeframe → deque of price events (aggregated candles)
    private final Map<String, Map<Timeframe, Deque<CryptoPriceEvent>>> candles
            = new ConcurrentHashMap<>();

    // ── Last computed windows per symbol ──────────────────────────────────
    private final Map<String, Map<Timeframe, TimeframeWindow>> lastWindows
            = new ConcurrentHashMap<>();

    private static final int MAX_CANDLES = 50; // 50 candles per timeframe = enough for RSI(14)

    private final TechnicalIndicatorService technicalIndicatorService;
    private final SimpMessagingTemplate     messagingTemplate;

    public MultiTimeframeService(TechnicalIndicatorService technicalIndicatorService,
                                 SimpMessagingTemplate messagingTemplate) {
        this.technicalIndicatorService = technicalIndicatorService;
        this.messagingTemplate         = messagingTemplate;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAIN ENTRY — called from TechnicalIndicatorService on every tick
    // Returns ConfluenceResult with combined signal + individual windows
    // ═══════════════════════════════════════════════════════════════════════
    public ConfluenceResult computeConfluence(String symbol,
                                              CryptoPriceEvent event,
                                              List<CryptoPriceEvent> history) {
        try {
            // ── Aggregate tick into all timeframe candles ──────────────────
            aggregateTick(symbol, event);

            // ── Compute indicators per timeframe ───────────────────────────
            Map<Timeframe, TimeframeWindow> windows = new EnumMap<>(Timeframe.class);

            for (Timeframe tf : Timeframe.values()) {
                TimeframeWindow window = computeWindowForTimeframe(symbol, tf, event, history);
                windows.put(tf, window);
            }

            // ── Cache computed windows ─────────────────────────────────────
            lastWindows.computeIfAbsent(symbol, k -> new EnumMap<>(Timeframe.class));
            lastWindows.get(symbol).putAll(windows);

            // ── Compute confluence ─────────────────────────────────────────
            ConfluenceResult result = computeConfluenceFromWindows(symbol, windows);

            // ── Broadcast via WebSocket ────────────────────────────────────
            messagingTemplate.convertAndSend("/topic/confluence/" + symbol, result);

            return result;

        } catch (Exception e) {
            log.warn("⚠️ MultiTimeframe error for {}: {}", symbol, e.getMessage());
            return buildFallbackConfluence(symbol);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Aggregate tick into timeframe candles
    // For demo/real-time: each event IS a candle for now
    // In production: aggregate by timestamp buckets
    // ═══════════════════════════════════════════════════════════════════════
    private void aggregateTick(String symbol, CryptoPriceEvent event) {
        candles.computeIfAbsent(symbol, k -> new EnumMap<>(Timeframe.class));

        for (Timeframe tf : Timeframe.values()) {
            Deque<CryptoPriceEvent> deque = candles.get(symbol)
                    .computeIfAbsent(tf, k -> new ArrayDeque<>());
            deque.addLast(event);
            if (deque.size() > MAX_CANDLES) deque.pollFirst();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Compute indicators for one timeframe
    // Uses sampled history appropriate for each timeframe
    // ═══════════════════════════════════════════════════════════════════════
    private TimeframeWindow computeWindowForTimeframe(String symbol,
                                                      Timeframe tf,
                                                      CryptoPriceEvent event,
                                                      List<CryptoPriceEvent> fullHistory) {
        TimeframeWindow window = new TimeframeWindow(tf, symbol);

        // Sample history based on timeframe (simulate aggregation)
        int sampleStep = switch (tf) {
            case ONE_MIN      -> 1;
            case FIVE_MIN     -> 5;
            case FIFTEEN_MIN  -> 15;
            case ONE_HOUR     -> 60;
        };

        List<CryptoPriceEvent> sampledHistory = sampleHistory(fullHistory, sampleStep);
        if (sampledHistory.size() < 5) {
            // Not enough data — use full history
            sampledHistory = fullHistory;
        }

        // ── RSI ──────────────────────────────────────────────────────────
        double rsi = computeRSI(sampledHistory, 14);
        window.setRsi(rsi);

        // ── MACD Histogram ────────────────────────────────────────────────
        double macdHist = computeMACDHistogram(sampledHistory, 12, 26, 9);
        window.setMacdHistogram(macdHist);

        // ── Z-Score ───────────────────────────────────────────────────────
        double zScore = computeZScore(sampledHistory, 20, event.getPrice());
        window.setZScore(zScore);

        // ── Volume Ratio ──────────────────────────────────────────────────
        double volRatio = computeVolumeRatio(sampledHistory, event.getVolume());
        window.setVolumeRatio(volRatio);

        // ── Bollinger Position ────────────────────────────────────────────
        double bbPos = computeBollingerPosition(sampledHistory, 20, event.getPrice());
        window.setBollingerPosition(bbPos);

        // ── Risk Score (simplified) ───────────────────────────────────────
        int risk = computeSimpleRisk(rsi, Math.abs(zScore), volRatio);
        window.setRiskScore(risk);

        // ── Derive signal from all indicators ─────────────────────────────
        window.deriveSignal();

        return window;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONFLUENCE CALCULATION
    // Count how many timeframes agree on the same signal
    // ═══════════════════════════════════════════════════════════════════════
    private ConfluenceResult computeConfluenceFromWindows(String symbol,
                                                          Map<Timeframe, TimeframeWindow> windows) {
        // Count signals
        Map<String, Integer> signalCounts = new HashMap<>();
        signalCounts.put("BUY",  0);
        signalCounts.put("SELL", 0);
        signalCounts.put("HOLD", 0);

        double totalStrength = 0;
        int    anomalyCount  = 0;

        for (TimeframeWindow w : windows.values()) {
            String sig = w.getSignal() != null ? w.getSignal() : "HOLD";
            signalCounts.merge(sig, 1, Integer::sum);
            totalStrength += w.getSignalStrength();
            if (w.isAnomaly()) anomalyCount++;
        }

        // Determine dominant signal
        String dominant = Collections.max(signalCounts.entrySet(),
                Map.Entry.comparingByValue()).getKey();
        int dominantCount = signalCounts.get(dominant);

        // Confluence level
        int    confluenceCount    = dominantCount;
        String confluenceSignal   = dominantCount >= 2 ? dominant : "CONFLICTED";
        double avgStrength        = totalStrength / windows.size();

        // Multiplier: 4→2.0x, 3→1.5x, 2→1.0x, <2→0.5x
        double multiplier = switch (dominantCount) {
            case 4  -> 2.0;
            case 3  -> 1.5;
            case 2  -> 1.0;
            default -> 0.5;
        };

        // Confidence text
        String confidenceText = switch (dominantCount) {
            case 4  -> "STRONG — all 4 timeframes agree";
            case 3  -> "CONFIRMED — 3 of 4 timeframes agree";
            case 2  -> "WEAK — 2 of 4 timeframes agree";
            default -> "CONFLICTED — no clear direction";
        };

        return new ConfluenceResult(
                symbol, confluenceSignal, confluenceCount, multiplier,
                avgStrength, confidenceText, anomalyCount, windows
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Technical Calculations — standalone (no external dependency)
    // ═══════════════════════════════════════════════════════════════════════

    private double computeRSI(List<CryptoPriceEvent> history, int period) {
        if (history.size() < period + 1) return 50.0;
        List<Double> prices = history.stream()
                .map(CryptoPriceEvent::getPrice).collect(Collectors.toList());
        Collections.reverse(prices);

        double gain = 0, loss = 0;
        for (int i = 1; i <= period; i++) {
            double d = prices.get(i) - prices.get(i - 1);
            if (d > 0) gain += d; else loss += Math.abs(d);
        }
        gain /= period;
        loss /= period;
        if (loss == 0) return 100.0;
        double rs = gain / loss;
        return clamp(100.0 - (100.0 / (1 + rs)), 0, 100);
    }

    private double computeMACDHistogram(List<CryptoPriceEvent> history,
                                        int fast, int slow, int signal) {
        if (history.size() < slow + signal) return 0.0;
        List<Double> prices = history.stream()
                .map(CryptoPriceEvent::getPrice).collect(Collectors.toList());
        Collections.reverse(prices);

        double emaFast = computeEMA(prices, fast);
        double emaSlow = computeEMA(prices, slow);
        double macdLine = emaFast - emaSlow;
        // simplified: use macdLine as histogram proxy
        return macdLine;
    }

    private double computeEMA(List<Double> prices, int period) {
        if (prices.size() < period) return prices.isEmpty() ? 0 : prices.get(prices.size()-1);
        double k   = 2.0 / (period + 1);
        double ema = prices.get(0);
        for (int i = 1; i < Math.min(prices.size(), period * 2); i++) {
            ema = prices.get(i) * k + ema * (1 - k);
        }
        return ema;
    }

    private double computeZScore(List<CryptoPriceEvent> history, int window, double current) {
        if (history.size() < window) return 0.0;
        List<Double> prices = history.subList(0, Math.min(window, history.size()))
                .stream().mapToDouble(CryptoPriceEvent::getPrice).boxed()
                .collect(Collectors.toList());
        double mean = prices.stream().mapToDouble(Double::doubleValue).average().orElse(current);
        double variance = prices.stream().mapToDouble(p -> (p - mean) * (p - mean)).average().orElse(0);
        double std = Math.sqrt(variance);
        return std > 0 ? (current - mean) / std : 0.0;
    }

    private double computeVolumeRatio(List<CryptoPriceEvent> history, double currentVol) {
        if (history.isEmpty() || currentVol <= 0) return 1.0;
        double avgVol = history.stream().mapToDouble(CryptoPriceEvent::getVolume)
                .filter(v -> v > 0).average().orElse(currentVol);
        return avgVol > 0 ? currentVol / avgVol : 1.0;
    }

    private double computeBollingerPosition(List<CryptoPriceEvent> history,
                                            int period, double current) {
        if (history.size() < period) return 0.5;
        double[] prices = history.subList(0, period).stream()
                .mapToDouble(CryptoPriceEvent::getPrice).toArray();
        double mean = Arrays.stream(prices).average().orElse(current);
        double std  = Math.sqrt(Arrays.stream(prices).map(p -> (p-mean)*(p-mean)).average().orElse(0));
        double upper = mean + 2 * std;
        double lower = mean - 2 * std;
        if (upper == lower) return 0.5;
        return clamp((current - lower) / (upper - lower), 0.0, 1.0);
    }

    private int computeSimpleRisk(double rsi, double absZ, double volRatio) {
        int r = 20;
        if (rsi < 30 || rsi > 70)    r += 25;
        if (absZ > 2.5)              r += 30;
        else if (absZ > 1.5)         r += 15;
        if (volRatio > 2.0)          r += 20;
        else if (volRatio > 1.5)     r += 10;
        return Math.min(r, 100);
    }

    private List<CryptoPriceEvent> sampleHistory(List<CryptoPriceEvent> history, int step) {
        if (step <= 1) return history;
        List<CryptoPriceEvent> sampled = new ArrayList<>();
        for (int i = 0; i < history.size(); i += step) {
            sampled.add(history.get(i));
        }
        return sampled;
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private ConfluenceResult buildFallbackConfluence(String symbol) {
        return new ConfluenceResult(symbol, "HOLD", 0, 1.0, 0.3,
                "Insufficient data for confluence", 0, Collections.emptyMap());
    }

    // ── Public query ──────────────────────────────────────────────────────
    public Optional<ConfluenceResult> getLastConfluence(String symbol) {
        Map<Timeframe, TimeframeWindow> windows = lastWindows.get(symbol);
        if (windows == null || windows.isEmpty()) return Optional.empty();
        return Optional.of(computeConfluenceFromWindows(symbol, windows));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ConfluenceResult — returned to callers, broadcast via WebSocket
    // ═══════════════════════════════════════════════════════════════════════
    public static class ConfluenceResult {
        public final String symbol;
        public final String confluenceSignal;   // BUY / SELL / HOLD / CONFLICTED
        public final int    confluenceCount;    // 0–4 timeframes agreeing
        public final double multiplier;         // signal strength multiplier
        public final double avgSignalStrength;  // 0.0–1.0
        public final String confidenceText;     // human readable
        public final int    anomalyCount;       // how many TFs show anomaly
        public final Map<Timeframe, TimeframeWindow> windows; // per-TF details

        public ConfluenceResult(String symbol, String signal, int count,
                                double mult, double strength, String text,
                                int anomalies,
                                Map<Timeframe, TimeframeWindow> windows) {
            this.symbol            = symbol;
            this.confluenceSignal  = signal;
            this.confluenceCount   = count;
            this.multiplier        = mult;
            this.avgSignalStrength = strength;
            this.confidenceText    = text;
            this.anomalyCount      = anomalies;
            this.windows           = windows;
        }

        public boolean isStrongSignal()     { return confluenceCount >= 3; }
        public boolean isConfirmedSignal()  { return confluenceCount >= 2; }

        @Override public String toString() {
            return String.format("Confluence[%s %d/4 %s %.1fx]",
                    symbol, confluenceCount, confluenceSignal, multiplier);
        }
    }
}