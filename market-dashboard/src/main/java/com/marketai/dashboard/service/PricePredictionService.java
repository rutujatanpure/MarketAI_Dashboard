package com.marketai.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketai.dashboard.model.HistoricalPrice;
import com.marketai.dashboard.model.PricePrediction;
import com.marketai.dashboard.repository.PricePredictionRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * PricePredictionService — Budget-safe design
 *
 *  ┌─────────────────────────────────────────────────────────────┐
 *  │  1 Gemini call/day  (Mon–Fri 11:00 AM IST)                 │
 *  │  ALL symbols batched into ONE prompt → ONE response        │
 *  │  Technical indicators (RSI/MACD/BB/SR) → FREE, real-time  │
 *  │  Cache: AI predictions valid 24h                           │
 *  │  Weekend → technical-only mode (0 API calls)               │
 *  └─────────────────────────────────────────────────────────────┘
 */
@Service
public class PricePredictionService {

    private static final Logger log = LoggerFactory.getLogger(PricePredictionService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // ── Gemini config ─────────────────────────────────────────────────────
    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemma-3-27b-it}")
    private String geminiModel;

    @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String geminiBaseUrl;

    // ── Dependencies ──────────────────────────────────────────────────────
    private final HistoricalDataService     historicalDataService;
    private final PricePredictionRepository predictionRepository;
    private final MarketPriceService        marketPriceService;
    private final ObjectMapper              mapper     = new ObjectMapper();
    private final HttpClient                httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    // ── State ─────────────────────────────────────────────────────────────
    private final Map<String, PricePrediction> cache = new ConcurrentHashMap<>();
    private volatile String lastAiBatchDate = "";

    // ── Symbols ───────────────────────────────────────────────────────────
    private static final List<String> CRYPTO_SYMBOLS = List.of(
            "BTCUSDT","ETHUSDT","BNBUSDT","SOLUSDT","XRPUSDT",
            "ADAUSDT","DOGEUSDT","AVAXUSDT","DOTUSDT","MATICUSDT",
            "LINKUSDT","LTCUSDT","BCHUSDT","ATOMUSDT","TRXUSDT",
            "ETCUSDT","FILUSDT","ICPUSDT","APTUSDT","ARBUSDT",
            "OPUSDT","SUIUSDT","INJUSDT","NEARUSDT","HBARUSDT",
            "ALGOUSDT","VETUSDT","EGLDUSDT","AAVEUSDT","THETAUSDT",
            "XTZUSDT","SANDUSDT","MANAUSDT","AXSUSDT","GALAUSDT",
            "FLOWUSDT","KAVAUSDT","RUNEUSDT","CAKEUSDT","FTMUSDT",
            "CHZUSDT","DYDXUSDT","SNXUSDT","LDOUSDT","PEPEUSDT",
            "SHIBUSDT","BONKUSDT","BLURUSDT","SEIUSDT","TIAUSDT"
    );

    private static final List<String> STOCK_SYMBOLS = List.of(
            "RELIANCE","TCS","INFY","HDFCBANK","ICICIBANK",
            "HINDUNILVR","SBIN","BAJFINANCE","BHARTIARTL","KOTAKBANK",
            "ITC","LT","AXISBANK","ASIANPAINT","MARUTI",
            "SUNPHARMA","TITAN","ULTRACEMCO","WIPRO","POWERGRID",
            "NTPC","TECHM","NESTLEIND","DIVISLAB","BAJAJ-AUTO",
            "ADANIGREEN","ADANIENT","TATAMOTORS","TATASTEEL","JSWSTEEL"
    );

    public PricePredictionService(HistoricalDataService historicalDataService,
                                  PricePredictionRepository predictionRepository,
                                  MarketPriceService marketPriceService) {
        this.historicalDataService = historicalDataService;
        this.predictionRepository  = predictionRepository;
        this.marketPriceService    = marketPriceService;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Startup
    // ═════════════════════════════════════════════════════════════════════
    @PostConstruct
    public void init() {
        log.info("════════════════════════════════════════════════════════");
        log.info("🔮  PricePredictionService  Ready");
        log.info("    AI Provider  : Gemini ({})", geminiModel);
        log.info("    API Key      : {}", masked(geminiApiKey));
        log.info("    AI Schedule  : Mon–Fri 11:00 AM IST");
        log.info("    Gemini calls : 1 per day (ALL {} symbols in 1 call)",
                CRYPTO_SYMBOLS.size() + STOCK_SYMBOLS.size());
        log.info("    Technicals   : FREE, always-on (RSI/MACD/BB/SR)");
        log.info("════════════════════════════════════════════════════════");
    }

    // ═════════════════════════════════════════════════════════════════════
    // Scheduler — Mon–Fri 11:00 AM IST only
    // ═════════════════════════════════════════════════════════════════════
    @Scheduled(cron = "0 0 11 * * MON-FRI", zone = "Asia/Kolkata")
    public void dailyAiBatch() {
        String today = LocalDate.now(IST).toString();
        if (today.equals(lastAiBatchDate)) {
            log.info("⏭️  AI batch already ran today ({})", today);
            return;
        }
        log.info("🚀 Daily AI batch starting — 1 Gemini call for {} symbols",
                allSymbols().size());
        runAiBatch();
        lastAiBatchDate = today;
    }

    /** Admin REST trigger */
    public Map<String, Object> triggerManualBatch() {
        log.info("🔧 Manual AI batch triggered");
        int updated = runAiBatch();
        return Map.of("symbolsUpdated", updated, "timestamp", Instant.now().toString());
    }

    // ═════════════════════════════════════════════════════════════════════
    // Core: ONE Gemini call for ALL symbols
    // ═════════════════════════════════════════════════════════════════════
    private int runAiBatch() {
        List<String> all = allSymbols();

        // Step 1: build technical snapshots (FREE)
        List<SymbolSnapshot> snapshots = all.stream()
                .map(this::buildSnapshot)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (snapshots.isEmpty()) {
            log.warn("⚠️ No snapshots — skipping AI batch");
            return 0;
        }
        log.info("📊 Technical snapshots ready: {}/{}", snapshots.size(), all.size());

        // Step 2: ONE Gemini call with all symbols
        Map<String, AiSignal> signals;
        try {
            String prompt = buildBatchPrompt(snapshots);
            signals = callGeminiBatch(prompt, snapshots.size());
            log.info("✅ Gemini returned {} AI signals", signals.size());
        } catch (Exception e) {
            log.error("❌ Gemini batch failed: {} — technical-only mode", e.getMessage());
            signals = Collections.emptyMap();
        }

        // Step 3: merge AI + technical → save
        int count = 0;
        for (SymbolSnapshot snap : snapshots) {
            PricePrediction pred = buildFullPrediction(snap, signals.get(snap.symbol));
            predictionRepository.save(pred);
            cache.put(snap.symbol, pred);
            count++;
        }

        log.info("💾 {} predictions saved", count);
        return count;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Snapshot — technical data per symbol (FREE)
    // ═════════════════════════════════════════════════════════════════════
    private SymbolSnapshot buildSnapshot(String symbol) {
        try {
            List<HistoricalPrice> candles = historicalDataService.getCandles(symbol, "1d", 30);
            if (candles == null || candles.size() < 5) return null;

            double currentPrice = marketPriceService.getLatest(symbol)
                    .map(e -> e.getPrice())
                    .orElse(candles.get(candles.size() - 1).getClose());

            SymbolSnapshot s = new SymbolSnapshot();
            s.symbol       = symbol;
            s.type         = symbol.endsWith("USDT") ? "CRYPTO" : "STOCK";
            s.currentPrice = currentPrice;
            s.candles      = candles;

            double[] closes = candles.stream().mapToDouble(HistoricalPrice::getClose).toArray();

            s.high30 = Arrays.stream(closes).max().orElse(currentPrice);
            s.low30  = Arrays.stream(closes).min().orElse(currentPrice);
            s.avg30  = Arrays.stream(closes).average().orElse(currentPrice);
            s.chg30  = pct(candles.get(0).getClose(), currentPrice);
            s.chg7   = candles.size() >= 7
                    ? pct(candles.get(candles.size() - 7).getClose(), currentPrice) : 0;

            double mean = s.avg30;
            s.volatility = Math.sqrt(Arrays.stream(closes)
                    .map(c -> Math.pow(c - mean, 2)).average().orElse(0)) / mean * 100;

            s.rsi14 = calcRSI(closes, 14);

            double[] macd = calcMACD(closes);
            s.macdLine = macd[0]; s.macdSignal = macd[1]; s.macdHist = macd[2];

            double[] bb = calcBB(closes, 20, 2.0);
            s.bbUpper = bb[0]; s.bbMid = bb[1]; s.bbLower = bb[2];

            s.support    = s.low30  * 1.015;
            s.resistance = s.high30 * 0.985;

            int up = 0;
            for (int i = closes.length - 7; i < closes.length - 1; i++)
                if (i >= 0 && closes[i + 1] > closes[i]) up++;
            s.trendScore = up / 6.0;

            s.avgVolume = candles.stream().mapToDouble(HistoricalPrice::getVolume).average().orElse(0);

            return s;
        } catch (Exception e) {
            log.warn("⚠️ Snapshot failed {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Batch prompt — ALL symbols in ONE compact table
    // ═════════════════════════════════════════════════════════════════════
    private String buildBatchPrompt(List<SymbolSnapshot> snapshots) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
You are an expert financial analyst. Analyze the market data table below.
For EACH symbol, provide a JSON prediction object. Return a JSON ARRAY only.
No markdown, no explanation — ONLY the JSON array.

Required format:
[
  {
    "symbol": "BTCUSDT",
    "predictionToday": <price>,
    "predictionWeek": <price>,
    "predictionMonth": <price>,
    "changePercentToday": <number>,
    "changePercentWeek": <number>,
    "changePercentMonth": <number>,
    "direction": "INCREASE|DECREASE|STABLE",
    "recommendation": "BUY|SELL|HOLD",
    "riskLevel": "LOW|MEDIUM|HIGH",
    "confidence": <0.0-1.0>,
    "summary": "<max 12 words>"
  }
]

Rules:
- Every symbol in the table MUST appear in the output array.
- changePercent is relative to currentPrice (positive=up, negative=down).
- Use RSI, MACD_hist, BB_position, trend to determine direction.

=== MARKET DATA %s IST ===
Symbol|Type|Price|RSI14|MACD_hist|BB_pos|chg7d%%|chg30d%%|Volatility|Trend(0-1)
""".formatted(LocalDate.now(IST)));

        for (SymbolSnapshot s : snapshots) {
            double bbRange = s.bbUpper - s.bbLower;
            double bbPos   = bbRange > 0 ? (s.currentPrice - s.bbLower) / bbRange : 0.5;
            sb.append(String.format(
                    "%s|%s|%.4f|%.1f|%.5f|%.2f|%.2f|%.2f|%.2f|%.2f%n",
                    s.symbol, s.type, s.currentPrice,
                    s.rsi14, s.macdHist, bbPos,
                    s.chg7, s.chg30, s.volatility, s.trendScore
            ));
        }
        return sb.toString();
    }

    // ═════════════════════════════════════════════════════════════════════
    // ONE Gemini API call
    // ═════════════════════════════════════════════════════════════════════
    private Map<String, AiSignal> callGeminiBatch(String prompt, int symbolCount) throws Exception {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            log.warn("⚠️ Gemini API key not set");
            return Collections.emptyMap();
        }

        String requestBody = mapper.writeValueAsString(Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature",     0.1,
                        "maxOutputTokens", 8192,
                        "topP",            0.8,
                        "topK",            10),
                "safetySettings", List.of(
                        Map.of("category", "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold", "BLOCK_NONE"),
                        Map.of("category", "HARM_CATEGORY_HARASSMENT",        "threshold", "BLOCK_NONE"))
        ));

        String url = geminiBaseUrl + "/" + geminiModel + ":generateContent?key=" + geminiApiKey;
        log.info("📡 Gemini batch call ({} symbols, {} chars)...", symbolCount, prompt.length());

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(90))
                .build();

        HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 429) { log.warn("🚦 Gemini rate limit"); return Collections.emptyMap(); }
        if (response.statusCode() != 200) {
            log.error("❌ Gemini {} : {}", response.statusCode(),
                    response.body().substring(0, Math.min(300, response.body().length())));
            return Collections.emptyMap();
        }

        JsonNode candidates = mapper.readTree(response.body()).path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) return Collections.emptyMap();
        if ("SAFETY".equals(candidates.get(0).path("finishReason").asText())) return Collections.emptyMap();

        String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText("")
                .replaceAll("(?s)```json\\s*", "").replaceAll("```\\s*", "").trim();

        int arrStart = text.indexOf('[');
        int arrEnd   = text.lastIndexOf(']');
        if (arrStart == -1 || arrEnd == -1) {
            log.error("❌ No JSON array in response");
            return Collections.emptyMap();
        }

        JsonNode arr = mapper.readTree(text.substring(arrStart, arrEnd + 1));
        Map<String, AiSignal> signals = new HashMap<>();
        for (JsonNode node : arr) {
            String sym = node.path("symbol").asText("");
            if (sym.isBlank()) continue;
            AiSignal sig = new AiSignal();
            sig.predictionToday    = node.path("predictionToday").asDouble(0);
            sig.predictionWeek     = node.path("predictionWeek").asDouble(0);
            sig.predictionMonth    = node.path("predictionMonth").asDouble(0);
            sig.changePercentToday = node.path("changePercentToday").asDouble(0);
            sig.changePercentWeek  = node.path("changePercentWeek").asDouble(0);
            sig.changePercentMonth = node.path("changePercentMonth").asDouble(0);
            sig.direction          = node.path("direction").asText("STABLE");
            sig.recommendation     = node.path("recommendation").asText("HOLD");
            sig.riskLevel          = node.path("riskLevel").asText("MEDIUM");
            sig.confidence         = node.path("confidence").asDouble(0.5);
            sig.summary            = node.path("summary").asText("");
            signals.put(sym, sig);
        }
        log.info("📊 Parsed {} AI signals", signals.size());
        return signals;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Merge AI + technical → PricePrediction
    // ═════════════════════════════════════════════════════════════════════
    private PricePrediction buildFullPrediction(SymbolSnapshot s, AiSignal ai) {
        PricePrediction p = new PricePrediction();
        p.setSymbol(s.symbol);
        p.setType(s.type);
        p.setCurrentPrice(s.currentPrice);
        p.setHistoryDaysUsed(s.candles.size());
        p.setModelUsed(ai != null ? geminiModel : "TECHNICAL_ONLY");
        p.setTimestamp(Instant.now());

        if (ai != null) {
            p.setPredictionToday(ai.predictionToday > 0 ? ai.predictionToday
                    : s.currentPrice * (1 + ai.changePercentToday / 100));
            p.setPredictionWeek(ai.predictionWeek > 0 ? ai.predictionWeek
                    : s.currentPrice * (1 + ai.changePercentWeek / 100));
            p.setPredictionMonth(ai.predictionMonth > 0 ? ai.predictionMonth
                    : s.currentPrice * (1 + ai.changePercentMonth / 100));
            p.setChangePercentToday(ai.changePercentToday);
            p.setChangePercentWeek(ai.changePercentWeek);
            p.setChangePercentMonth(ai.changePercentMonth);
            p.setDirectionToday(ai.direction);
            p.setDirectionWeek(ai.direction);
            p.setDirectionMonth(ai.direction);
            p.setRecommendation(ai.recommendation);
            p.setRiskLevel(ai.riskLevel);
            p.setConfidenceToday(ai.confidence);
            p.setConfidenceWeek(Math.max(0.1, ai.confidence - 0.1));
            p.setConfidenceMonth(Math.max(0.1, ai.confidence - 0.2));
            p.setSummary(ai.summary);
        } else {
            // pure technical fallback
            String dir    = technicalDirection(s);
            double chg    = techTodayChange(s);
            p.setPredictionToday(s.currentPrice * (1 + chg / 100));
            p.setPredictionWeek(s.currentPrice * (1 + chg * 3 / 100));
            p.setPredictionMonth(s.currentPrice * (1 + chg * 8 / 100));
            p.setChangePercentToday(chg);
            p.setChangePercentWeek(chg * 3);
            p.setChangePercentMonth(chg * 8);
            p.setDirectionToday(dir); p.setDirectionWeek(dir); p.setDirectionMonth(dir);
            p.setRecommendation(techRecommendation(s));
            p.setRiskLevel(techRiskLevel(s));
            p.setConfidenceToday(0.45); p.setConfidenceWeek(0.35); p.setConfidenceMonth(0.25);
            p.setSummary("Technical analysis only");
        }

        // Always-on technical indicators (FREE)
        p.setRsi14(s.rsi14);
        p.setMacdLine(s.macdLine);
        p.setMacdSignal(s.macdSignal);
        p.setMacdHistogram(s.macdHist);
        p.setBbUpper(s.bbUpper);
        p.setBbMiddle(s.bbMid);
        p.setBbLower(s.bbLower);
        p.setSupportLevel(s.support);
        p.setResistanceLevel(s.resistance);
        p.setVolatility(s.volatility);
        p.setTrendScore(s.trendScore);
        p.setChangePercent7d(s.chg7);
        p.setChangePercent30d(s.chg30);

        return p;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Public API
    // ═════════════════════════════════════════════════════════════════════
    public PricePrediction getPrediction(String symbol) {
        PricePrediction cached = cache.get(symbol);
        if (cached != null) return cached;
        SymbolSnapshot snap = buildSnapshot(symbol);
        if (snap == null) return null;
        PricePrediction pred = buildFullPrediction(snap, null);
        cache.put(symbol, pred);
        return pred;
    }

    public List<PricePrediction> getAllPredictions()    { return new ArrayList<>(cache.values()); }
    public List<PricePrediction> getCryptoPredictions() {
        return cache.entrySet().stream().filter(e -> e.getKey().endsWith("USDT"))
                .map(Map.Entry::getValue).collect(Collectors.toList());
    }
    public List<PricePrediction> getStockPredictions() {
        return cache.entrySet().stream().filter(e -> !e.getKey().endsWith("USDT"))
                .map(Map.Entry::getValue).collect(Collectors.toList());
    }

    // ═════════════════════════════════════════════════════════════════════
    // Technical indicators (FREE — no API)
    // ═════════════════════════════════════════════════════════════════════
    private double calcRSI(double[] c, int period) {
        if (c.length < period + 1) return 50.0;
        double gain = 0, loss = 0;
        for (int i = 1; i <= period; i++) {
            double d = c[i] - c[i - 1];
            if (d > 0) gain += d; else loss -= d;
        }
        gain /= period; loss /= period;
        for (int i = period + 1; i < c.length; i++) {
            double d = c[i] - c[i - 1];
            gain = (gain * (period - 1) + Math.max(d, 0)) / period;
            loss = (loss * (period - 1) + Math.max(-d, 0)) / period;
        }
        return loss == 0 ? 100 : 100 - (100 / (1 + gain / loss));
    }

    private double[] calcMACD(double[] c) {
        if (c.length < 26) return new double[]{0, 0, 0};
        double e12 = ema(c, 12), e26 = ema(c, 26);
        double line = e12 - e26, signal = line * 0.9;
        return new double[]{line, signal, line - signal};
    }

    private double ema(double[] c, int period) {
        double k = 2.0 / (period + 1), e = c[0];
        for (int i = 1; i < c.length; i++) e = c[i] * k + e * (1 - k);
        return e;
    }

    private double[] calcBB(double[] c, int period, double mult) {
        if (c.length < period) period = c.length;
        double[] sl = Arrays.copyOfRange(c, c.length - period, c.length);
        double m  = Arrays.stream(sl).average().orElse(0);
        double sd = Math.sqrt(Arrays.stream(sl).map(x -> Math.pow(x - m, 2)).average().orElse(0));
        return new double[]{m + mult * sd, m, m - mult * sd};
    }

    private String technicalDirection(SymbolSnapshot s) {
        int score = 0;
        if (s.rsi14 > 55) score++; if (s.rsi14 < 45) score--;
        if (s.macdHist > 0) score++; if (s.macdHist < 0) score--;
        if (s.trendScore > 0.6) score++; if (s.trendScore < 0.4) score--;
        if (s.currentPrice > s.bbMid) score++; if (s.currentPrice < s.bbMid) score--;
        return score > 1 ? "INCREASE" : score < -1 ? "DECREASE" : "STABLE";
    }

    private double techTodayChange(SymbolSnapshot s) {
        double base = s.volatility * 0.1;
        if (s.rsi14 > 55 && s.macdHist > 0) return +base;
        if (s.rsi14 < 45 && s.macdHist < 0) return -base;
        return 0;
    }

    private String techRecommendation(SymbolSnapshot s) {
        if (s.rsi14 < 35 && s.trendScore > 0.5) return "BUY";
        if (s.rsi14 > 70 || (s.rsi14 > 60 && s.trendScore < 0.3)) return "SELL";
        return "HOLD";
    }

    private String techRiskLevel(SymbolSnapshot s) {
        if (s.volatility > 8) return "HIGH";
        if (s.volatility > 4) return "MEDIUM";
        return "LOW";
    }

    // ═════════════════════════════════════════════════════════════════════
    // Utilities
    // ═════════════════════════════════════════════════════════════════════
    private List<String> allSymbols() {
        List<String> all = new ArrayList<>(CRYPTO_SYMBOLS);
        all.addAll(STOCK_SYMBOLS);
        return all;
    }

    private double pct(double from, double to) { return from == 0 ? 0 : (to - from) / from * 100; }

    private String masked(String k) {
        if (k == null || k.isBlank()) return "❌ NOT SET";
        return "✅ " + k.substring(0, Math.min(8, k.length())) + "...";
    }

    // ── Internal DTOs ─────────────────────────────────────────────────────
    private static class SymbolSnapshot {
        String symbol, type;
        double currentPrice;
        List<HistoricalPrice> candles;
        double high30, low30, avg30, chg30, chg7, volatility;
        double rsi14, macdLine, macdSignal, macdHist;
        double bbUpper, bbMid, bbLower;
        double support, resistance, trendScore, avgVolume;
    }

    private static class AiSignal {
        double predictionToday, predictionWeek, predictionMonth;
        double changePercentToday, changePercentWeek, changePercentMonth;
        String direction, recommendation, riskLevel, summary;
        double confidence;
    }
}