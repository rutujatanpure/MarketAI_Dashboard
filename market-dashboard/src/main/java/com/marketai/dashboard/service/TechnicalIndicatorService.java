package com.marketai.dashboard.service;

import com.marketai.dashboard.model.CryptoPriceEvent;
import com.marketai.dashboard.model.MarketAlert;
import com.marketai.dashboard.model.TechnicalIndicator;
import com.marketai.dashboard.repository.MarketAlertRepository;
import com.marketai.dashboard.repository.TechnicalIndicatorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TechnicalIndicatorService {

    private static final Logger log = LoggerFactory.getLogger(TechnicalIndicatorService.class);

    // ── Thresholds ────────────────────────────────────────────────────────────
    private static final double RSI_OVERSOLD        = 30.0;
    private static final double RSI_OVERBOUGHT      = 70.0;
    private static final double VOLUME_SPIKE_RATIO  = 2.0;
    private static final double ZSCORE_THRESHOLD    = 2.5;
    private static final double PUMP_DUMP_THRESHOLD = 60.0;
    private static final int    WINDOW_SIZE         = 100; // rolling window size

    // ── Rolling windows per symbol ────────────────────────────────────────────
    // These maintain in-memory price/volume history for Z-Score calculation
    private final ConcurrentHashMap<String, List<Double>> priceWindows  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Double>> volumeWindows = new ConcurrentHashMap<>();

    private final TechnicalIndicatorRepository indicatorRepository;
    private final MarketAlertRepository        alertRepository;
    private final SimpMessagingTemplate        messaging;

    public TechnicalIndicatorService(
            TechnicalIndicatorRepository indicatorRepository,
            MarketAlertRepository alertRepository,
            SimpMessagingTemplate messaging) {
        this.indicatorRepository = indicatorRepository;
        this.alertRepository     = alertRepository;
        this.messaging           = messaging;
    }

    // ════════════════════════════════════════════════════════════════════════
    // MAIN METHOD — Call this from Kafka consumer on every price event
    // Replaces old: analyze(symbol, history)
    // ════════════════════════════════════════════════════════════════════════
    public TechnicalIndicator analyze(String symbol,
                                      CryptoPriceEvent event,
                                      List<CryptoPriceEvent> history) {
        // Update rolling windows with latest price & volume
        updateWindows(symbol, event.getPrice(), event.getVolume());

        TechnicalIndicator ind = new TechnicalIndicator();
        ind.setSymbol(symbol);
        ind.setTimestamp(Instant.now());

        // ── RSI ───────────────────────────────────────────────────────────────
        double rsi = calculateRSI(history, 14);
        ind.setRsi(rsi);
        ind.setRsiSignal(
                rsi < RSI_OVERSOLD   ? "OVERSOLD" :
                        rsi > RSI_OVERBOUGHT ? "OVERBOUGHT" : "NEUTRAL"
        );

        // ── MACD ──────────────────────────────────────────────────────────────
        double[] macd = calculateMACD(history);
        ind.setMacdLine(macd[0]);
        ind.setSignalLine(macd[1]);
        ind.setMacdHistogram(macd[2]);
        ind.setMacdSignal(macd[0] > macd[1] ? "BULLISH" : "BEARISH");

        // ── Bollinger Bands ───────────────────────────────────────────────────
        double[] bb     = calculateBollingerBands(history, 20);
        double   bbPos  = (bb[0] != bb[2])
                ? clamp((event.getPrice() - bb[2]) / (bb[0] - bb[2]), 0, 1)
                : 0.5;
        ind.setUpperBand(bb[0]);
        ind.setMiddleBand(bb[1]);
        ind.setLowerBand(bb[2]);
        ind.setBollingerPosition(bbPos);
        ind.setBollingerSignal(
                bbPos > 0.9 ? "UPPER_TOUCH" :
                        bbPos < 0.1 ? "LOWER_TOUCH" : "INSIDE"
        );

        // ── ATR ───────────────────────────────────────────────────────────────
        double atr = calculateATR(history, 14);
        ind.setAtr(atr);
        ind.setAtrPercent(event.getPrice() > 0 ? (atr / event.getPrice()) * 100 : 0);

        // ── Trend ─────────────────────────────────────────────────────────────
        ind.setTrend(detectTrend(rsi, macd[2]));

        // ── Volume Analysis ───────────────────────────────────────────────────
        double volRatio = calculateVolumeRatio(symbol, event.getVolume());
        ind.setVolumeRatio(volRatio);
        ind.setVolumeSpike(volRatio > VOLUME_SPIKE_RATIO);
        ind.setVolumeSignal(
                volRatio > 3.0 ? "SPIKE" :
                        volRatio > 2.0 ? "HIGH"  :
                                volRatio < 0.5 ? "LOW"   : "NORMAL"
        );

        // ── Z-Score Anomaly ───────────────────────────────────────────────────
        double[] zData = calculateZScore(symbol, event.getPrice());
        double   zScore = zData[0];
        ind.setZScore(zScore);
        ind.setRollingMean(zData[1]);
        ind.setRollingStdDev(zData[2]);
        ind.setAnomaly(zScore > ZSCORE_THRESHOLD);
        ind.setAnomalySeverity(
                zScore > 4.0 ? "CRITICAL" :
                        zScore > 3.0 ? "HIGH"     :
                                zScore > 2.5 ? "MEDIUM"   : "NORMAL"
        );

        // ── Multi-Factor Risk Score ───────────────────────────────────────────
        int[] risk = calculateRiskScore(rsi, volRatio, zScore, event.getPriceChange());
        ind.setRiskScore(risk[0]);
        ind.setVolatilityScore(risk[1]);
        ind.setManipulationScore(risk[2]);
        ind.setRiskLevel(
                risk[0] > 75 ? "CRITICAL" :
                        risk[0] > 50 ? "HIGH"     :
                                risk[0] > 25 ? "MEDIUM"   : "LOW"
        );

        // ── Pump & Dump Detection ─────────────────────────────────────────────
        double[] pd = detectPumpDump(event.getPriceChange(), volRatio, zScore);
        ind.setPumpDumpProbability(pd[0]);
        ind.setPumpDumpSuspected(pd[0] > PUMP_DUMP_THRESHOLD);
        ind.setPumpDumpPhase(phaseLabel((int) pd[1]));

        // ── Save + Broadcast ──────────────────────────────────────────────────
        saveAndBroadcast(ind);

        // ── Fire Alerts ───────────────────────────────────────────────────────
        fireAlerts(ind, event);

        return ind;
    }

    // ════════════════════════════════════════════════════════════════════════
    // BACKWARD COMPATIBLE — old analyze(symbol, history) still works
    // Called by existing code that doesn't have CryptoPriceEvent
    // ════════════════════════════════════════════════════════════════════════
    public TechnicalIndicator analyze(String symbol, List<CryptoPriceEvent> history) {
        TechnicalIndicator ind = new TechnicalIndicator();
        ind.setSymbol(symbol);
        ind.setTimestamp(Instant.now());

        double   rsi   = calculateRSI(history, 14);
        double[] macd  = calculateMACD(history);
        double[] bands = calculateBollingerBands(history, 20);

        ind.setRsi(rsi);
        ind.setRsiSignal(rsi < 30 ? "OVERSOLD" : rsi > 70 ? "OVERBOUGHT" : "NEUTRAL");
        ind.setMacdLine(macd[0]);
        ind.setSignalLine(macd[1]);
        ind.setMacdHistogram(macd[2]);
        ind.setMacdSignal(macd[0] > macd[1] ? "BULLISH" : "BEARISH");
        ind.setUpperBand(bands[0]);
        ind.setMiddleBand(bands[1]);
        ind.setLowerBand(bands[2]);
        ind.setTrend(detectTrend(rsi, macd[2]));

        // Z-Score if window data available
        if (!history.isEmpty()) {
            double[] z = calculateZScore(symbol, history.get(0).getPrice());
            ind.setZScore(z[0]);
            ind.setRollingMean(z[1]);
            ind.setRollingStdDev(z[2]);
            ind.setAnomaly(z[0] > ZSCORE_THRESHOLD);
            ind.setAnomalySeverity(z[0] > 4 ? "CRITICAL" : z[0] > 3 ? "HIGH" :
                    z[0] > 2.5 ? "MEDIUM" : "NORMAL");
        }

        return ind;
    }

    // ════════════════════════════════════════════════════════════════════════
    // RSI — Relative Strength Index (14 period)
    // < 30 = Oversold (BUY signal)
    // > 70 = Overbought (SELL signal)
    // ════════════════════════════════════════════════════════════════════════
    public double calculateRSI(List<CryptoPriceEvent> history, int period) {
        if (history == null || history.size() < period + 1) return 50.0;

        double gains = 0, losses = 0;
        for (int i = 1; i <= period; i++) {
            double change = history.get(i - 1).getPrice() - history.get(i).getPrice();
            if (change > 0) gains  += change;
            else            losses += Math.abs(change);
        }

        double avgGain = gains  / period;
        double avgLoss = losses / period;
        if (avgLoss == 0) return 100.0;

        double rs = avgGain / avgLoss;
        return clamp(100.0 - (100.0 / (1.0 + rs)), 0, 100);
    }

    // ════════════════════════════════════════════════════════════════════════
    // MACD — Moving Average Convergence Divergence
    // Returns: [macdLine, signalLine, histogram]
    // Positive histogram = bullish momentum
    // ════════════════════════════════════════════════════════════════════════
    public double[] calculateMACD(List<CryptoPriceEvent> history) {
        if (history == null || history.size() < 26) return new double[]{0, 0, 0};

        double ema12      = calculateEMA(history, 12);
        double ema26      = calculateEMA(history, 26);
        double macdLine   = ema12 - ema26;
        double signalLine = macdLine * (2.0 / (9 + 1)); // 9-period EMA of MACD
        double histogram  = macdLine - signalLine;

        return new double[]{macdLine, signalLine, histogram};
    }

    // ════════════════════════════════════════════════════════════════════════
    // Bollinger Bands (20 period, 2 std dev)
    // Returns: [upper, middle, lower]
    // ════════════════════════════════════════════════════════════════════════
    public double[] calculateBollingerBands(List<CryptoPriceEvent> history, int period) {
        if (history == null || history.size() < period) {
            double p = (history != null && !history.isEmpty())
                    ? history.get(0).getPrice() : 0;
            return new double[]{p * 1.02, p, p * 0.98};
        }

        double sum = 0;
        for (int i = 0; i < period; i++) sum += history.get(i).getPrice();
        double middle = sum / period;

        double variance = 0;
        for (int i = 0; i < period; i++) {
            double d = history.get(i).getPrice() - middle;
            variance += d * d;
        }
        double stdDev = Math.sqrt(variance / period);

        return new double[]{
                middle + (2 * stdDev),  // Upper
                middle,                  // Middle (SMA)
                middle - (2 * stdDev)   // Lower
        };
    }

    // ════════════════════════════════════════════════════════════════════════
    // ATR — Average True Range (14 period)
    // Measures volatility — higher ATR = more volatile
    // ════════════════════════════════════════════════════════════════════════
    public double calculateATR(List<CryptoPriceEvent> history, int period) {
        if (history == null || history.size() < period) return 0.0;

        double sum = 0;
        for (int i = 0; i < period; i++) {
            CryptoPriceEvent curr      = history.get(i);
            double           prevClose = (i + 1 < history.size())
                    ? history.get(i + 1).getPrice()
                    : curr.getPrice();

            // True Range = max of 3 values
            double tr = Math.max(
                    curr.getHigh24h() - curr.getLow24h(),
                    Math.max(
                            Math.abs(curr.getHigh24h() - prevClose),
                            Math.abs(curr.getLow24h()  - prevClose)
                    )
            );
            sum += tr;
        }
        return sum / period;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Z-Score — Statistical Anomaly Detection
    // Inspired by Two Sigma quant strategies
    // Z > 2.5 = Statistically significant anomaly
    // Returns: [zScore, rollingMean, rollingStdDev]
    // ════════════════════════════════════════════════════════════════════════
    public double[] calculateZScore(String symbol, double currentPrice) {
        List<Double> window = priceWindows.getOrDefault(symbol, new ArrayList<>());

        if (window.size() < 20) {
            // Not enough data yet
            return new double[]{0.0, currentPrice, 0.0};
        }

        // Rolling mean
        double sum = 0;
        for (double p : window) sum += p;
        double mean = sum / window.size();

        // Rolling standard deviation
        double variance = 0;
        for (double p : window) {
            double d = p - mean;
            variance += d * d;
        }
        double stdDev = Math.sqrt(variance / window.size());

        // Z-Score = how many std devs from mean
        double zScore = stdDev > 0
                ? Math.abs((currentPrice - mean) / stdDev)
                : 0.0;

        return new double[]{zScore, mean, stdDev};
    }

    // ════════════════════════════════════════════════════════════════════════
    // Volume Ratio — current vs rolling average
    // > 2.0 = Volume spike detected
    // ════════════════════════════════════════════════════════════════════════
    public double calculateVolumeRatio(String symbol, double currentVolume) {
        List<Double> window = volumeWindows.getOrDefault(symbol, new ArrayList<>());
        if (window.isEmpty() || currentVolume <= 0) return 1.0;

        double avgVolume = window.stream()
                .mapToDouble(Double::doubleValue)
                .filter(v -> v > 0)
                .average()
                .orElse(currentVolume);

        return avgVolume > 0 ? currentVolume / avgVolume : 1.0;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Multi-Factor Risk Score
    // Returns: [riskScore 0-100, volatilityScore 0-100, manipScore 0-100]
    // ════════════════════════════════════════════════════════════════════════
    private int[] calculateRiskScore(double rsi, double volRatio,
                                     double zScore, double priceChange) {
        // Price risk (0-30 points)
        double priceRisk = Math.min(Math.abs(priceChange) * 3, 30);

        // RSI risk — extreme values = risky (0-25 points)
        double rsiRisk = rsi < 30 ? (30 - rsi) * 0.8
                : rsi > 70 ? (rsi - 70) * 0.8
                : 0;
        rsiRisk = Math.min(rsiRisk, 25);

        // Volume risk (0-20 points)
        double volRisk = Math.min(Math.max((volRatio - 1.0) * 10, 0), 20);

        // Z-Score risk (0-25 points)
        double zRisk = Math.min(zScore * 5, 25);

        int riskScore = (int) clamp(priceRisk + rsiRisk + volRisk + zRisk, 0, 100);

        // Volatility score — price movement based
        int volatility = (int) clamp(Math.abs(priceChange) * 5, 0, 100);

        // Manipulation probability score
        int manipulation = 0;
        if (volRatio > 2.0) manipulation += 30;
        if (zScore   > 2.5) manipulation += 40;
        if (volRatio > 3.0) manipulation += 30;
        manipulation = (int) clamp(manipulation, 0, 100);

        return new int[]{riskScore, volatility, manipulation};
    }

    // ════════════════════════════════════════════════════════════════════════
    // Pump & Dump Detection
    // Returns: [probability 0-100, phaseCode 0-3]
    // ════════════════════════════════════════════════════════════════════════
    private double[] detectPumpDump(double priceChange, double volRatio, double zScore) {
        double prob  = 0;
        double phase = 0; // 0=NONE, 1=ACCUMULATION, 2=PUMP, 3=DUMP

        // Slow accumulation phase
        if (priceChange > 1.0 && priceChange < 5.0 && volRatio < 1.2) {
            prob  = 25;
            phase = 1;
        }

        // Pump phase — sudden spike + volume + z-score anomaly
        if (priceChange > 5.0 && volRatio > 2.0 && zScore > 2.0) {
            prob  = 40 + Math.min(zScore * 10, 40);
            phase = 2;
        }

        // Strong pump
        if (priceChange > 10.0 && volRatio > 3.0) {
            prob  = Math.min(prob + 30, 95);
            phase = 2;
        }

        // Dump phase — sudden crash after spike
        if (priceChange < -5.0 && volRatio > 2.0) {
            prob  = 50 + Math.min(Math.abs(priceChange) * 2, 40);
            phase = 3;
        }

        return new double[]{clamp(prob, 0, 100), phase};
    }

    // ════════════════════════════════════════════════════════════════════════
    // Trend Detection — upgraded with priceChange
    // ════════════════════════════════════════════════════════════════════════
    public String detectTrend(double rsi, double macdHistogram) {
        if (rsi > 60 && macdHistogram > 0) return "UPTREND";
        if (rsi < 40 && macdHistogram < 0) return "DOWNTREND";
        return "SIDEWAYS";
    }

    // ════════════════════════════════════════════════════════════════════════
    // Alert Firing — fires MarketAlert to DB + WebSocket
    // ════════════════════════════════════════════════════════════════════════
    private void fireAlerts(TechnicalIndicator ind, CryptoPriceEvent event) {
        List<MarketAlert> toFire = new ArrayList<>();

        // Z-Score anomaly
        if (ind.isAnomaly()) {
            toFire.add(MarketAlert.anomaly(
                    ind.getSymbol(), ind.getZScore(),
                    event.getPrice(), event.getPriceChange()
            ));
        }

        // RSI extreme
        if (ind.getRsi() < 28 || ind.getRsi() > 72) {
            toFire.add(MarketAlert.rsiExtreme(
                    ind.getSymbol(), ind.getRsi(), event.getPrice()
            ));
        }

        // Volume spike
        if (ind.getVolumeRatio() > 3.0) {
            toFire.add(MarketAlert.volumeSpike(
                    ind.getSymbol(), ind.getVolumeRatio(), event.getPrice()
            ));
        }

        // Pump & Dump
        if (ind.isPumpDumpSuspected()) {
            toFire.add(MarketAlert.pumpDump(
                    ind.getSymbol(), ind.getPumpDumpProbability(),
                    event.getPrice(), ind.getPumpDumpPhase()
            ));
        }

        // High risk
        if (ind.getRiskScore() > 75) {
            toFire.add(MarketAlert.riskHigh(
                    ind.getSymbol(), ind.getRiskScore(), event.getPrice()
            ));
        }

        // Save & broadcast each alert async
        toFire.forEach(alert -> CompletableFuture.runAsync(() -> {
            try {
                alertRepository.save(alert);
                messaging.convertAndSend("/topic/alerts/" + ind.getSymbol(), alert);
                messaging.convertAndSend("/topic/alerts/all", alert);
                log.warn("🚨 ALERT [{}][{}] {} — {}",
                        alert.getSeverity(), ind.getSymbol(),
                        alert.getAlertType(), alert.getMessage());
            } catch (Exception e) {
                log.error("Alert fire failed: {}", e.getMessage());
            }
        }));
    }

    // ════════════════════════════════════════════════════════════════════════
    // Save indicator + broadcast via WebSocket
    // ════════════════════════════════════════════════════════════════════════
    private void saveAndBroadcast(TechnicalIndicator ind) {
        CompletableFuture.runAsync(() -> {
            try {
                indicatorRepository.save(ind);
                messaging.convertAndSend("/topic/indicators/" + ind.getSymbol(), ind);
            } catch (Exception e) {
                log.error("Indicator save failed for {}: {}", ind.getSymbol(), e.getMessage());
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // Rolling window updater
    // ════════════════════════════════════════════════════════════════════════
    private void updateWindows(String symbol, double price, double volume) {
        priceWindows.compute(symbol, (k, list) -> {
            if (list == null) list = new ArrayList<>();
            list.add(0, price);                              // newest first
            if (list.size() > WINDOW_SIZE) list.remove(list.size() - 1);
            return list;
        });

        volumeWindows.compute(symbol, (k, list) -> {
            if (list == null) list = new ArrayList<>();
            list.add(0, volume);
            if (list.size() > WINDOW_SIZE) list.remove(list.size() - 1);
            return list;
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════════
    private double calculateEMA(List<CryptoPriceEvent> history, int period) {
        if (history.size() < period) return history.get(0).getPrice();
        double mult = 2.0 / (period + 1);
        double ema  = history.get(period - 1).getPrice();
        for (int i = period - 2; i >= 0; i--) {
            ema = (history.get(i).getPrice() - ema) * mult + ema;
        }
        return ema;
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private String phaseLabel(int code) {
        return code == 1 ? "ACCUMULATION" :
                code == 2 ? "PUMP"         :
                        code == 3 ? "DUMP"         : "NONE";
    }
}