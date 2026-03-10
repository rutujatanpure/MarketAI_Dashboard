package com.marketai.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketai.dashboard.model.AiAnalysisResult;
import com.marketai.dashboard.model.CryptoPriceEvent;
import com.marketai.dashboard.model.RiskProfile;
import com.marketai.dashboard.model.TechnicalIndicator;
import com.marketai.dashboard.repository.AiAnalysisRepository;
import com.marketai.dashboard.repository.MarketPriceRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ═══════════════════════════════════════════════════════════════════════
 * AiAnalysisService — PRODUCTION v3.0
 * ═══════════════════════════════════════════════════════════════════════
 *
 * What this file does (for Google interview):
 *
 * EVERY price tick triggers this pipeline:
 *
 *   TICK ─► [1] TechnicalIndicatorService  → RSI/MACD/BB/ATR/Z-Score
 *        ─► [2] SmartRiskEngine            → 6-factor risk score (0-100)
 *        ─► [3] MultiTimeframeService      → 1m/5m/15m/1h confluence
 *        ─► [4] Gemini AI (12h cache)      → Sentiment + Signal
 *        ─► [5] AlertService               → Fire alerts if thresholds hit
 *        ─► [6] WebSocket broadcast        → Real-time to frontend
 *
 * Steps 1-3 run EVERY tick (zero API cost, pure math).
 * Step 4 runs max twice per symbol per day (12h cache + daily budget guard).
 *
 * Production Safety:
 *   - Daily budget guard (hard cap 200 calls/day, free tier 14,400)
 *   - Per-minute rate limiter (25 calls/min)
 *   - Circuit breaker per symbol (opens after 3 failures, resets after 5min)
 *   - Exponential backoff retry (1s → 2s → 3rd attempt)
 *   - All exceptions caught and logged — service never crashes
 * ═══════════════════════════════════════════════════════════════════════
 */
@Service
public class AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    // ── Rate / timing constants ────────────────────────────────────────────
    private static final long   AI_RATE_LIMIT_MS     = 12 * 60 * 60_000L; // 12 hours
    private static final long   ANOMALY_WINDOW_MS    = 5  * 60_000L;       // 5 min dedup
    private static final int    MAX_CALLS_PER_MINUTE = 25;
    private static final int    MAX_FAILURES         = 3;
    private static final long   CIRCUIT_OPEN_MS      = 300_000;            // 5 min
    private static final int    MAX_RETRY_ATTEMPTS   = 3;
    private static final long   RETRY_BASE_DELAY_MS  = 1_000;
    private static final double ANOMALY_THRESHOLD    = 5.0;                // fallback %

    // ── Market Hours IST ───────────────────────────────────────────────────
    private static final ZoneId IST            = ZoneId.of("Asia/Kolkata");
    private static final int    NSE_OPEN_HOUR  = 9,  NSE_OPEN_MIN  = 15;
    private static final int    NSE_CLOSE_HOUR = 15, NSE_CLOSE_MIN = 30;

    // ── NSE Symbols ────────────────────────────────────────────────────────
    private static final Set<String> NSE_SYMBOLS = Set.of(
            "RELIANCE","HDFCBANK","INFY","TCS","WIPRO","ICICIBANK","AXISBANK",
            "ITC","SBIN","ADANIENT","KOTAKBANK","LT","HINDUNILVR","BAJFINANCE",
            "MARUTI","SUNPHARMA","TITAN","ULTRACEMCO","ASIANPAINT","BAJAJFINSV",
            "NTPC","POWERGRID","TECHM","HCLTECH","DIVISLAB","DRREDDY","CIPLA",
            "BRITANNIA","NESTLEIND","EICHERMOT","COALINDIA","ONGC","BPCL","IOC",
            "TATASTEEL","JSWSTEEL","HINDALCO","VEDL","GRASIM","HEROMOTOCO",
            "APOLLOHOSP","ADANIPORTS","INDUSINDBK","BAJAJ-AUTO","TATACONSUM",
            "LTIM","SBILIFE","HDFCLIFE","UPL","KWIL","NIFTY 50"
    );

    // ── BSE Symbols ────────────────────────────────────────────────────────
    private static final Set<String> BSE_SYMBOLS = Set.of(
            "RELIANCE-BSE","HDFCBANK-BSE","INFY-BSE","TCS-BSE","WIPRO-BSE",
            "ICICIBANK-BSE","AXISBANK-BSE","ITC-BSE","SBIN-BSE","KOTAKBANK-BSE",
            "LT-BSE","HINDUNILVR-BSE","BAJFINANCE-BSE","MARUTI-BSE","SUNPHARMA-BSE",
            "TITAN-BSE","BAJAJFINSV-BSE","NTPC-BSE","POWERGRID-BSE","TATASTEEL-BSE",
            "HINDALCO-BSE","BHARTIARTL-BSE","HEROMOTOCO-BSE","MM-BSE",
            "INDUSINDBK-BSE","ONGC-BSE","ASIANPAINT-BSE","TATACONSUM-BSE",
            "BAJAJ-AUTO-BSE","ADANIENT-BSE"
    );

    private static final Set<String> VALID_SENTIMENTS = Set.of("BULLISH","BEARISH","NEUTRAL");
    private static final Set<String> VALID_SIGNALS    = Set.of("BUY","SELL","HOLD");

    // ── Gemini Config ──────────────────────────────────────────────────────
    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemma-3-27b-it}")
    private String geminiModel;

    @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String geminiBaseUrl;

    @Value("${gemini.daily-limit:200}")
    private int dailyLimit;

    // ── Daily Budget ───────────────────────────────────────────────────────
    private final AtomicInteger dailyCallCount = new AtomicInteger(0);
    private volatile String     lastResetDate  = LocalDate.now().toString();

    // ── Injected Services ──────────────────────────────────────────────────
    private final TechnicalIndicatorService technicalIndicatorService; // Step 1
    private final SmartRiskEngine           smartRiskEngine;           // Step 2 — NEW
    private final MultiTimeframeService     multiTimeframeService;     // Step 3 — NEW
    private final AlertService              alertService;
    private final MarketPriceRepository     priceRepository;
    private final AiAnalysisRepository      aiRepository;
    private final SimpMessagingTemplate     messagingTemplate;

    private final ObjectMapper mapper     = new ObjectMapper();
    private final HttpClient   httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    // ── Per-symbol state ───────────────────────────────────────────────────
    private final Map<String, Long>             lastAnalysisTime  = new ConcurrentHashMap<>();
    private final Map<String, AiAnalysisResult> lastResultCache   = new ConcurrentHashMap<>();
    private final Map<String, Boolean>          pendingAnomaly    = new ConcurrentHashMap<>();
    private final Map<String, Double>           pendingAnomalyPct = new ConcurrentHashMap<>();
    private final Map<String, Long>             lastAnomalyTime   = new ConcurrentHashMap<>();
    private final Map<String, Integer>          failureCount      = new ConcurrentHashMap<>();
    private final Map<String, Long>             circuitOpenTime   = new ConcurrentHashMap<>();

    // ── Rate limiter ───────────────────────────────────────────────────────
    private final AtomicLong    minuteWindowStart = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger minuteCallCount   = new AtomicInteger(0);

    // ── Performance counters ───────────────────────────────────────────────
    private final AtomicLong totalRequests         = new AtomicLong(0);
    private final AtomicLong apiCallsMade          = new AtomicLong(0);
    private final AtomicLong cacheHits             = new AtomicLong(0);
    private final AtomicLong anomalyDetected       = new AtomicLong(0);
    private final AtomicLong anomalyCacheServed    = new AtomicLong(0);
    private final AtomicLong marketClosedHits      = new AtomicLong(0);
    private final AtomicLong rateLimitHits         = new AtomicLong(0);
    private final AtomicLong circuitBreaks         = new AtomicLong(0);
    private final AtomicLong totalLatencyMs        = new AtomicLong(0);
    private final AtomicLong retryCount            = new AtomicLong(0);
    private final AtomicLong validationFails       = new AtomicLong(0);
    private final AtomicLong budgetExhausted       = new AtomicLong(0);
    private final AtomicLong technicalAnalysisDone = new AtomicLong(0);
    private final AtomicLong zScoreAnomalies       = new AtomicLong(0);
    private final AtomicLong pumpDumpDetected      = new AtomicLong(0);
    private final AtomicLong highRiskDetected      = new AtomicLong(0);
    // NEW counters
    private final AtomicLong riskProfilesComputed  = new AtomicLong(0);
    private final AtomicLong confluenceSignals      = new AtomicLong(0);
    private final AtomicLong criticalRiskEvents     = new AtomicLong(0);
    private final AtomicLong strongConfluenceEvents = new AtomicLong(0);

    // ── Constructor ────────────────────────────────────────────────────────
    public AiAnalysisService(MarketPriceRepository priceRepository,
                             AiAnalysisRepository  aiRepository,
                             SimpMessagingTemplate messagingTemplate,
                             TechnicalIndicatorService technicalIndicatorService,
                             SmartRiskEngine           smartRiskEngine,
                             MultiTimeframeService     multiTimeframeService,
                             AlertService              alertService) {
        this.priceRepository           = priceRepository;
        this.aiRepository              = aiRepository;
        this.messagingTemplate         = messagingTemplate;
        this.technicalIndicatorService = technicalIndicatorService;
        this.smartRiskEngine           = smartRiskEngine;
        this.multiTimeframeService     = multiTimeframeService;
        this.alertService              = alertService;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Market Helpers
    // ═════════════════════════════════════════════════════════════════════
    private boolean isNseStock(String s)  { return NSE_SYMBOLS.contains(s); }
    private boolean isBseStock(String s)  { return BSE_SYMBOLS.contains(s); }
    private boolean isStock(String s)     { return isNseStock(s) || isBseStock(s); }

    private boolean isNseMarketOpen() {
        ZonedDateTime now  = ZonedDateTime.now(IST);
        if (now.getDayOfWeek().getValue() >= 6) return false;
        int nowMins   = now.getHour() * 60 + now.getMinute();
        int openMins  = NSE_OPEN_HOUR  * 60 + NSE_OPEN_MIN;
        int closeMins = NSE_CLOSE_HOUR * 60 + NSE_CLOSE_MIN;
        return nowMins >= openMins && nowMins < closeMins;
    }

    private boolean isMarketOpen(String symbol) {
        return isStock(symbol) ? isNseMarketOpen() : true;
    }

    private String exchangeTag(String symbol) {
        if (isNseStock(symbol)) return "NSE";
        if (isBseStock(symbol)) return "BSE";
        return "CRYPTO";
    }

    // ═════════════════════════════════════════════════════════════════════
    // Daily Budget Guard
    // ═════════════════════════════════════════════════════════════════════
    private boolean isDailyBudgetAvailable() {
        String today = LocalDate.now().toString();
        if (!today.equals(lastResetDate)) {
            dailyCallCount.set(0);
            lastResetDate = today;
            log.info("🔄 Daily AI counter reset for {}", today);
        }
        int used = dailyCallCount.get();
        if (used >= dailyLimit) {
            budgetExhausted.incrementAndGet();
            log.warn("🛑 Daily budget ({}) exhausted — fallback only.", dailyLimit);
            return false;
        }
        dailyCallCount.incrementAndGet();
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Startup Log
    // ═════════════════════════════════════════════════════════════════════
    @PostConstruct
    public void init() {
        log.info("════════════════════════════════════════════════════════════");
        log.info("🤖 AiAnalysisService v3.0 — Production Pipeline");
        log.info("   ── Per-tick pipeline (zero API cost) ─────────────────");
        log.info("   Step 1: TechnicalIndicatorService  → RSI/MACD/BB/ATR/Z-Score");
        log.info("   Step 2: SmartRiskEngine            → 6-factor risk score (0-100)");
        log.info("   Step 3: MultiTimeframeService      → 1m/5m/15m/1h confluence");
        log.info("   ── AI analysis (12h cache, budget-guarded) ────────────");
        log.info("   Step 4: Gemini AI                  → Sentiment + Signal");
        log.info("   Step 5: AlertService               → Threshold-based alerts");
        log.info("   Step 6: WebSocket                  → Real-time broadcast");
        log.info("   ── Safety Nets ────────────────────────────────────────");
        log.info("   Daily budget: {}/day (free tier: 14,400/day — {}x buffer)",
                dailyLimit, 14400 / Math.max(dailyLimit, 1));
        log.info("   Circuit breaker: opens after {} failures, resets after 5min", MAX_FAILURES);
        log.info("   Retry: exponential backoff 1s→2s (max {} attempts)", MAX_RETRY_ATTEMPTS);
        log.info("   Model: {}", geminiModel);
        log.info("   API Key: {}", (geminiApiKey == null || geminiApiKey.isBlank())
                ? "❌ NOT SET" : "✅ " + geminiApiKey.substring(0, Math.min(8, geminiApiKey.length())) + "...");
        log.info("════════════════════════════════════════════════════════════");
    }

    // ═════════════════════════════════════════════════════════════════════
    // Performance Stats — logged every 5 minutes
    // ═════════════════════════════════════════════════════════════════════
    @Scheduled(fixedRate = 300_000)
    public void logPerformanceStats() {
        long   total      = totalRequests.get();
        long   api        = apiCallsMade.get();
        double saved      = total > 0 ? ((double)(total - api) / total) * 100.0 : 0.0;
        double avgLatency = api   > 0 ? (double) totalLatencyMs.get() / api      : 0.0;
        ZonedDateTime now = ZonedDateTime.now(IST);

        log.info("════════════════════════════════════════════════════════════");
        log.info("📊 Production Pipeline Report — {}:{} IST | NSE: {}",
                String.format("%02d", now.getHour()),
                String.format("%02d", now.getMinute()),
                isNseMarketOpen() ? "🟢 OPEN" : "🔴 CLOSED");
        log.info("   ── AI Stats ─────────────────────────────────────────");
        log.info("   Daily: {}/{}  |  Total: {}  |  API: {}  |  Cache: {}",
                dailyCallCount.get(), dailyLimit, total, api, cacheHits.get());
        log.info("   Anomaly→Cache: {}  |  Mkt Closed: {}  |  Rate Limit: {}",
                anomalyCacheServed.get(), marketClosedHits.get(), rateLimitHits.get());
        log.info("   Circuit Breaks: {}  |  Retries: {}  |  Avg Latency: {}ms",
                circuitBreaks.get(), retryCount.get(), String.format("%.0f", avgLatency));
        log.info("   API Saved: {}%  |  Budget Exhausted: {} times",
                String.format("%.1f", saved), budgetExhausted.get());
        log.info("   ── Technical + Risk + Confluence Stats ──────────────");
        log.info("   Tech Done: {}  |  Z-Anomalies: {}  |  P&D: {}  |  High Risk: {}",
                technicalAnalysisDone.get(), zScoreAnomalies.get(),
                pumpDumpDetected.get(), highRiskDetected.get());
        log.info("   Risk Profiles: {}  |  CRITICAL: {}  |  Confluence: {}  |  Strong: {}",
                riskProfilesComputed.get(), criticalRiskEvents.get(),
                confluenceSignals.get(), strongConfluenceEvents.get());
        log.info("   Symbols Tracked: {}", lastAnalysisTime.size());
        log.info("════════════════════════════════════════════════════════════");
    }

    // ═════════════════════════════════════════════════════════════════════
    // ███  MAIN PIPELINE  ███
    // Called from Kafka consumer on every price event
    // ═════════════════════════════════════════════════════════════════════
    @Async("kafkaExecutor")
    public void analyzeAsync(CryptoPriceEvent event) {
        String symbol    = event.getSymbol();
        long   nowMs     = System.currentTimeMillis();
        double changePct = Math.abs(event.getPriceChange());

        List<CryptoPriceEvent> history;
        try {
            history = priceRepository.findTop100BySymbolOrderByTimestampDesc(symbol);
        } catch (Exception e) {
            log.error("❌ DB read failed for {}: {}", symbol, e.getMessage());
            return;
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // STEP 1 — Technical Indicators (every tick, zero cost)
        //   RSI/MACD/Bollinger/ATR/Z-Score/Volume/PumpDump/RiskScore
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        TechnicalIndicator indicator = null;
        try {
            indicator = technicalIndicatorService.analyze(symbol, event, history);
            technicalAnalysisDone.incrementAndGet();
            if (indicator.isAnomaly())            zScoreAnomalies.incrementAndGet();
            if (indicator.isPumpDumpSuspected())  pumpDumpDetected.incrementAndGet();
            if (indicator.getRiskScore() > 75)    highRiskDetected.incrementAndGet();
        } catch (Exception e) {
            log.warn("⚠️ TechnicalIndicator failed for {}: {}", symbol, e.getMessage());
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // STEP 2 — Smart Risk Engine (every tick, zero cost)
        //   6-factor composite risk score + market regime + VaR
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        RiskProfile riskProfile = null;
        if (indicator != null) {
            try {
                riskProfile = smartRiskEngine.computeRisk(symbol, event, indicator, history);
                riskProfilesComputed.incrementAndGet();
                if ("CRITICAL".equals(riskProfile.getRiskLevel())) criticalRiskEvents.incrementAndGet();
            } catch (Exception e) {
                log.warn("⚠️ SmartRiskEngine failed for {}: {}", symbol, e.getMessage());
            }
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // STEP 3 — Multi-Timeframe Confluence (every tick, zero cost)
        //   1m/5m/15m/1h agreement → BUY/SELL/HOLD/CONFLICTED + multiplier
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        MultiTimeframeService.ConfluenceResult confluence = null;
        try {
            confluence = multiTimeframeService.computeConfluence(symbol, event, history);
            confluenceSignals.incrementAndGet();
            if (confluence.isStrongSignal()) strongConfluenceEvents.incrementAndGet();
        } catch (Exception e) {
            log.warn("⚠️ MultiTimeframe failed for {}: {}", symbol, e.getMessage());
        }

        // Use best anomaly detection: Z-Score > fixed threshold fallback
        boolean isAnomaly = (indicator != null && indicator.isAnomaly())
                || changePct >= ANOMALY_THRESHOLD
                || (riskProfile != null && "CRITICAL".equals(riskProfile.getRiskLevel()));

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // STEP 4a — Market Closed Gate (NSE/BSE only)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (!isMarketOpen(symbol)) {
            AiAnalysisResult cached = lastResultCache.get(symbol);
            if (cached != null) {
                marketClosedHits.incrementAndGet();
                messagingTemplate.convertAndSend("/topic/analysis/" + symbol, cached);
            }
            return;
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // STEP 4b — Anomaly: flag for AI context, serve cache immediately
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (isAnomaly) {
            anomalyDetected.incrementAndGet();
            Long    lastAnomTime = lastAnomalyTime.get(symbol);
            boolean freshAnomaly = lastAnomTime == null
                    || (nowMs - lastAnomTime) >= ANOMALY_WINDOW_MS;

            if (freshAnomaly) {
                pendingAnomaly.put(symbol, true);
                pendingAnomalyPct.put(symbol, changePct);
                lastAnomalyTime.put(symbol, nowMs);
                double z = indicator != null ? indicator.getZScore() : 0;
                log.warn("🚨 ANOMALY [{}][{}] Z={:.2f} ({:.2f}%) risk={} — queued",
                        symbol, exchangeTag(symbol), z, changePct,
                        riskProfile != null ? riskProfile.getRiskLevel() : "?");
            }

            AiAnalysisResult cached = lastResultCache.get(symbol);
            if (cached != null) {
                anomalyCacheServed.incrementAndGet();
                cached.setAnomaly(true);
                cached.setAnomalyScore(indicator != null
                        ? Math.min(indicator.getZScore() / 5.0, 1.0)
                        : Math.min(changePct / 10.0, 1.0));
                messagingTemplate.convertAndSend("/topic/analysis/" + symbol, cached);
            }
            return;
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // STEP 4c — First seen: stagger initial AI calls across symbols
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (!lastAnalysisTime.containsKey(symbol)) {
            long offset = Math.abs(symbol.hashCode()) % AI_RATE_LIMIT_MS;
            lastAnalysisTime.put(symbol, nowMs - AI_RATE_LIMIT_MS + offset);
            log.info("⏱️ {} registered — first AI call in ~{}min", symbol, offset / 60_000);
            return;
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // STEP 4d — Within 12h window: serve cached AI result
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        long lastCall = lastAnalysisTime.get(symbol);
        if ((nowMs - lastCall) < AI_RATE_LIMIT_MS) {
            AiAnalysisResult cached = lastResultCache.get(symbol);
            if (cached != null) {
                cacheHits.incrementAndGet();
                messagingTemplate.convertAndSend("/topic/analysis/" + symbol, cached);
            }
            return;
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // STEP 4e — Fresh AI API call (max twice per symbol per day)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        totalRequests.incrementAndGet();

        if (isCircuitOpen(symbol)) {
            circuitBreaks.incrementAndGet();
            AiAnalysisResult cached = lastResultCache.get(symbol);
            messagingTemplate.convertAndSend("/topic/analysis/" + symbol,
                    cached != null ? cached : buildFallback(symbol));
            return;
        }

        boolean hadAnomaly     = pendingAnomaly.getOrDefault(symbol, false);
        double  anomalyPct     = pendingAnomalyPct.getOrDefault(symbol, 0.0);
        pendingAnomaly.put(symbol, false);
        pendingAnomalyPct.remove(symbol);
        lastAnalysisTime.put(symbol, nowMs);
        apiCallsMade.incrementAndGet();

        final TechnicalIndicator  finalIndicator  = indicator;
        final RiskProfile         finalRisk       = riskProfile;
        final MultiTimeframeService.ConfluenceResult finalConfluence = confluence;

        try {
            // ── AI Call with full context ──────────────────────────────────
            AiAnalysisResult result = callWithRetry(
                    event, history, hadAnomaly, anomalyPct,
                    finalIndicator, finalRisk, finalConfluence
            );

            // ── Attach risk + confluence scores to AI result ───────────────
            if (finalIndicator != null) {
                result.setRiskScore(finalIndicator.getRiskScore());
                result.setVolatilityScore(finalIndicator.getVolatilityScore());
                result.setManipulationProbability(finalIndicator.getManipulationScore());
            }
            if (finalRisk != null) {
                // Override with SmartRiskEngine composite (more accurate)
                result.setRiskScore(finalRisk.getCompositeRiskScore());
            }
            if (finalConfluence != null) {
                result.setConfluenceSignal(finalConfluence.confluenceSignal);
                result.setConfluenceCount(finalConfluence.confluenceCount);
                result.setConfluenceMultiplier(finalConfluence.multiplier);
            }

            // ── Save + broadcast ───────────────────────────────────────────
            failureCount.put(symbol, 0);
            circuitOpenTime.remove(symbol);
            lastResultCache.put(symbol, result);
            saveAsync(result);
            messagingTemplate.convertAndSend("/topic/analysis/" + symbol, result);

            // ── Alerts ─────────────────────────────────────────────────────
            if (finalIndicator != null) {
                alertService.checkAndAlertAdvanced(event, result, finalIndicator);
            } else {
                alertService.checkAndAlert(event, result);
            }

            log.info("🤖 [{}][{}] AI={} Risk={}/100 {} Z={:.1f}σ Confluence={} {}/4",
                    symbol, exchangeTag(symbol),
                    result.getSignal(),
                    finalRisk != null ? finalRisk.getCompositeRiskScore()
                            : (finalIndicator != null ? finalIndicator.getRiskScore() : "?"),
                    finalRisk != null ? finalRisk.getMarketRegime() : "",
                    finalIndicator != null ? finalIndicator.getZScore() : 0.0,
                    finalConfluence != null ? finalConfluence.confluenceSignal : "?",
                    finalConfluence != null ? finalConfluence.confluenceCount : 0);

        } catch (Exception e) {
            int failures = failureCount.getOrDefault(symbol, 0) + 1;
            failureCount.put(symbol, failures);
            if (failures >= MAX_FAILURES) {
                circuitOpenTime.put(symbol, System.currentTimeMillis());
                log.error("⚡ Circuit OPENED [{}] after {} failures", symbol, failures);
            }
            log.error("❌ AI pipeline failed for {}: {}", symbol, e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Manual REST analysis — called from AiController
    // ═════════════════════════════════════════════════════════════════════
    public AiAnalysisResult analyzeSymbol(String symbol) {
        AiAnalysisResult cached = lastResultCache.get(symbol);
        if (cached != null && cached.isFresh(300)) return cached;

        CryptoPriceEvent latest = priceRepository
                .findTopBySymbolOrderByTimestampDesc(symbol).orElse(null);
        if (latest == null) return buildFallback(symbol);

        List<CryptoPriceEvent> history =
                priceRepository.findTop100BySymbolOrderByTimestampDesc(symbol);

        TechnicalIndicator indicator = null;
        RiskProfile        risk      = null;
        MultiTimeframeService.ConfluenceResult confluence = null;

        try {
            indicator = technicalIndicatorService.analyze(symbol, history);
        } catch (Exception e) {
            log.warn("⚠️ Tech skip for {}: {}", symbol, e.getMessage());
        }
        try {
            if (indicator != null)
                risk = smartRiskEngine.computeRisk(symbol, latest, indicator, history);
        } catch (Exception e) {
            log.warn("⚠️ Risk skip for {}: {}", symbol, e.getMessage());
        }
        try {
            confluence = multiTimeframeService.computeConfluence(symbol, latest, history);
        } catch (Exception e) {
            log.warn("⚠️ Confluence skip for {}: {}", symbol, e.getMessage());
        }

        try {
            AiAnalysisResult result = callWithRetry(
                    latest, history, false, 0.0, indicator, risk, confluence);
            if (indicator != null) {
                result.setRiskScore(indicator.getRiskScore());
                result.setVolatilityScore(indicator.getVolatilityScore());
                result.setManipulationProbability(indicator.getManipulationScore());
            }
            if (risk != null) result.setRiskScore(risk.getCompositeRiskScore());
            if (confluence != null) {
                result.setConfluenceSignal(confluence.confluenceSignal);
                result.setConfluenceCount(confluence.confluenceCount);
            }
            lastResultCache.put(symbol, result);
            apiCallsMade.incrementAndGet();
            return result;
        } catch (Exception e) {
            log.error("❌ analyzeSymbol({}) failed: {}", symbol, e.getMessage());
            return buildFallback(symbol);
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Retry — exponential backoff: 1s, 2s
    // ═════════════════════════════════════════════════════════════════════
    private AiAnalysisResult callWithRetry(
            CryptoPriceEvent event,
            List<CryptoPriceEvent> history,
            boolean hadAnomaly,
            double  anomalyPct,
            TechnicalIndicator indicator,
            RiskProfile        risk,
            MultiTimeframeService.ConfluenceResult confluence) throws Exception {

        Exception lastEx = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                AiAnalysisResult result = callGeminiApi(
                        event, history, hadAnomaly, anomalyPct,
                        indicator, risk, confluence);
                if (isValidResult(result)) {
                    if (attempt > 1) log.info("✅ {} ok on attempt {}", event.getSymbol(), attempt);
                    return result;
                }
                validationFails.incrementAndGet();
            } catch (Exception e) {
                lastEx = e;
                log.warn("⚠️ {} attempt {}/{}: {}", event.getSymbol(), attempt, MAX_RETRY_ATTEMPTS, e.getMessage());
            }
            if (attempt < MAX_RETRY_ATTEMPTS) {
                retryCount.incrementAndGet();
                Thread.sleep(RETRY_BASE_DELAY_MS * (1L << (attempt - 1)));
            }
        }
        if (lastEx != null) throw lastEx;
        return buildFallback(event.getSymbol());
    }

    // ═════════════════════════════════════════════════════════════════════
    // Gemini API Call
    // ═════════════════════════════════════════════════════════════════════
    private AiAnalysisResult callGeminiApi(
            CryptoPriceEvent event,
            List<CryptoPriceEvent> history,
            boolean hadAnomaly,
            double  anomalyPct,
            TechnicalIndicator indicator,
            RiskProfile        risk,
            MultiTimeframeService.ConfluenceResult confluence) throws Exception {

        if (geminiApiKey == null || geminiApiKey.isBlank())
            return buildFallback(event.getSymbol());

        if (!isDailyBudgetAvailable())
            return buildFallback(event.getSymbol());

        // Per-minute rate limiter
        synchronized (this) {
            long now = System.currentTimeMillis();
            if (now - minuteWindowStart.get() >= 60_000) {
                minuteWindowStart.set(now);
                minuteCallCount.set(0);
            }
            if (minuteCallCount.incrementAndGet() > MAX_CALLS_PER_MINUTE) {
                long wait = 60_000L - (now - minuteWindowStart.get()) + 1_000L;
                rateLimitHits.incrementAndGet();
                log.warn("🚦 Rate limit — waiting {}ms", wait);
                Thread.sleep(Math.max(wait, 1_000));
                minuteWindowStart.set(System.currentTimeMillis());
                minuteCallCount.set(1);
            }
        }

        long   startTime  = System.currentTimeMillis();
        String prompt     = buildPrompt(event, history, hadAnomaly, anomalyPct,
                indicator, risk, confluence);

        String requestBody = mapper.writeValueAsString(Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text",
                        "You are a professional stock and crypto analyst. " +
                                "Analyze using all provided indicators. " +
                                "Respond ONLY in valid JSON. No markdown.\n\n" + prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.1, "maxOutputTokens", 250,
                        "topP", 0.8, "topK", 10),
                "safetySettings", List.of(
                        Map.of("category", "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold", "BLOCK_NONE"),
                        Map.of("category", "HARM_CATEGORY_HARASSMENT", "threshold", "BLOCK_NONE"))
        ));

        String url = geminiBaseUrl + "/" + geminiModel + ":generateContent?key=" + geminiApiKey;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(20))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        long latency = System.currentTimeMillis() - startTime;
        totalLatencyMs.addAndGet(latency);

        if (resp.statusCode() == 429) {
            rateLimitHits.incrementAndGet();
            return buildFallback(event.getSymbol());
        }
        if (resp.statusCode() == 503) return buildFallback(event.getSymbol());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Gemini HTTP " + resp.statusCode());
        }

        JsonNode candidates = mapper.readTree(resp.body()).path("candidates");
        if (!candidates.isArray() || candidates.isEmpty())
            throw new RuntimeException("Gemini: empty candidates");

        if ("SAFETY".equals(candidates.get(0).path("finishReason").asText()))
            return buildFallback(event.getSymbol());

        String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText("")
                .replaceAll("(?s)```json\\s*", "").replaceAll("```\\s*", "").trim();

        log.debug("🤖 Gemini [{}] {}ms raw={}", event.getSymbol(), latency,
                text.substring(0, Math.min(80, text.length())));

        AiAnalysisResult result = parseAiResponse(event, text, startTime, hadAnomaly, anomalyPct);
        result.setSource("GEMINI");
        result.setModelUsed(geminiModel);
        return result;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Prompt Builder — FULL context: price + indicators + risk + confluence
    // ═════════════════════════════════════════════════════════════════════
    private String buildPrompt(CryptoPriceEvent event,
                               List<CryptoPriceEvent> history,
                               boolean hadAnomaly, double anomalyPct,
                               TechnicalIndicator ind,
                               RiskProfile risk,
                               MultiTimeframeService.ConfluenceResult conf) {

        double avg = history.stream().mapToDouble(CryptoPriceEvent::getPrice).average().orElse(event.getPrice());
        double max = history.stream().mapToDouble(CryptoPriceEvent::getPrice).max().orElse(event.getPrice());
        double min = history.stream().mapToDouble(CryptoPriceEvent::getPrice).min().orElse(event.getPrice());

        boolean nse      = isNseStock(event.getSymbol());
        boolean bse      = isBseStock(event.getSymbol());
        String assetType = nse ? "Indian NSE stock" : bse ? "Indian BSE stock" : "cryptocurrency";
        String exchange  = nse ? "NSE India" : bse ? "BSE India" : "Binance";
        String currency  = (nse || bse) ? "INR" : "USD";
        String symbol    = event.getSymbol().replace("-BSE", "");

        String anomalyInfo = hadAnomaly
                ? String.format("⚠️ ANOMALY: %.2f%%", anomalyPct) +
                (ind != null ? String.format(" Z=%.2fσ (%s)", ind.getZScore(), ind.getAnomalySeverity()) : "")
                : "None";

        // Fallback: no indicators available
        if (ind == null) {
            return String.format("""
                Analyze this %s. Respond ONLY in valid JSON:
                Asset:%s(%s) Price:%.4f%s Change:%.2f%% Vol:%.0f Avg:%.4f Anomaly:%s
                {"sentiment":"BULLISH|BEARISH|NEUTRAL","sentimentScore":-1.0 to 1.0,"signal":"BUY|SELL|HOLD","summary":"max 20 words"}
                """, assetType, symbol, exchange, event.getPrice(), currency,
                    event.getPriceChange(), event.getVolume(), avg, anomalyInfo);
        }

        // Risk section
        String riskSection = (risk != null) ? String.format("""

            ── Smart Risk Engine ───────────────────────────────
            Composite Risk    : %d/100 → %s
            Market Regime     : %s (confidence: %.0f%%)
            Dominant Factor   : %s
            VaR-95            : %.2f%% max loss
            VaR-99            : %.2f%% max loss
            """,
                risk.getCompositeRiskScore(), risk.getRiskLevel(),
                risk.getMarketRegime(), risk.getRegimeConfidence() * 100,
                risk.getDominantRiskFactor(),
                risk.getVar95(), risk.getVar99()) : "";

        // Confluence section
        String confSection = (conf != null) ? String.format("""

            ── Multi-Timeframe Confluence ───────────────────────
            Signal            : %s (%d/4 timeframes agree)
            Confidence        : %s
            Strength Mult     : %.1fx
            Anomaly TFs       : %d/4 show anomaly
            """,
                conf.confluenceSignal, conf.confluenceCount,
                conf.confidenceText, conf.multiplier, conf.anomalyCount) : "";

        return String.format("""
            Analyze this %s using ALL provided data. Respond ONLY in valid JSON:

            ── Price Data ──────────────────────────────────────
            Asset    : %s (%s)
            Price    : %.4f %s | Change: %.2f%%
            High24h  : %.4f | Low24h : %.4f | Vol: %.0f
            Avg100   : %.4f | Max: %.4f | Min: %.4f
            Anomaly  : %s

            ── Technical Indicators ────────────────────────────
            RSI(14)   : %.1f → %s
            MACD      : Line=%.4f Hist=%.4f → %s
            Bollinger : Pos=%.0f%% → %s
            Z-Score   : %.2fσ → %s
            Volume    : %.1fx avg → %s
            ATR       : %.4f (%.2f%% of price)
            Risk/100  : %d → %s
            Pump&Dump : %.0f%% probability
            %s%s
            ── Required JSON (no extra text) ───────────────────
            {"sentiment":"BULLISH|BEARISH|NEUTRAL","sentimentScore":-1.0 to 1.0,"signal":"BUY|SELL|HOLD","summary":"max 20 words"}
            """,
                assetType, symbol, exchange,
                event.getPrice(), currency, event.getPriceChange(),
                event.getHigh24h(), event.getLow24h(), event.getVolume(),
                avg, max, min, anomalyInfo,
                ind.getRsi(),          safe(ind.getRsiSignal(), "NEUTRAL"),
                ind.getMacdLine(),     ind.getMacdHistogram(), safe(ind.getMacdSignal(), "NEUTRAL"),
                ind.getBollingerPosition() * 100, safe(ind.getBollingerSignal(), "INSIDE"),
                ind.getZScore(),       safe(ind.getAnomalySeverity(), "NORMAL"),
                ind.getVolumeRatio(),  safe(ind.getVolumeSignal(), "NORMAL"),
                ind.getAtr(),          ind.getAtrPercent(),
                ind.getRiskScore(),    safe(ind.getRiskLevel(), "LOW"),
                ind.getPumpDumpProbability(),
                riskSection, confSection
        );
    }

    // ═════════════════════════════════════════════════════════════════════
    // Parse AI Response
    // ═════════════════════════════════════════════════════════════════════
    private AiAnalysisResult parseAiResponse(CryptoPriceEvent event, String text,
                                             long startTime, boolean hadAnomaly,
                                             double anomalyPct) {
        try {
            int s = text.indexOf('{');
            int e = text.lastIndexOf('}');
            if (s != -1 && e > s) text = text.substring(s, e + 1);

            JsonNode node = mapper.readTree(text);
            AiAnalysisResult r = new AiAnalysisResult();
            r.setSymbol(event.getSymbol());
            r.setSentiment(sanitize(node.path("sentiment").asText("NEUTRAL"), VALID_SENTIMENTS, "NEUTRAL"));
            r.setSentimentScore(clamp(node.path("sentimentScore").asDouble(0.0), -1.0, 1.0));
            r.setSignal(sanitize(node.path("signal").asText("HOLD"), VALID_SIGNALS, "HOLD"));
            r.setSummary(node.path("summary").asText("Market analysis complete."));
            r.setAnomaly(hadAnomaly);
            r.setAnomalyScore(hadAnomaly ? Math.min(anomalyPct / 10.0, 1.0) : 0.0);
            r.setPriceAtAnalysis(event.getPrice());
            r.setPriceChange24h(event.getPriceChange());
            r.setModelUsed(geminiModel);
            r.setSource("GEMINI");
            r.setResponseTimeMs(System.currentTimeMillis() - startTime);
            r.setExpiresAt(Instant.now().plusSeconds(86_400));
            r.calculateConfidence();
            return r;
        } catch (Exception ex) {
            log.error("❌ Parse failed [{}]: {}", event.getSymbol(),
                    text.substring(0, Math.min(100, text.length())));
            return buildFallback(event.getSymbol());
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Validation
    // ═════════════════════════════════════════════════════════════════════
    private boolean isValidResult(AiAnalysisResult r) {
        if (r == null || "FALLBACK".equals(r.getSource()))       return false;
        if (!VALID_SENTIMENTS.contains(r.getSentiment()))        return false;
        if (!VALID_SIGNALS.contains(r.getSignal()))              return false;
        if (r.getSentimentScore() < -1.0 || r.getSentimentScore() > 1.0) return false;
        return r.getSummary() != null && !r.getSummary().isBlank();
    }

    // ═════════════════════════════════════════════════════════════════════
    // Circuit Breaker
    // ═════════════════════════════════════════════════════════════════════
    private boolean isCircuitOpen(String symbol) {
        Long t = circuitOpenTime.get(symbol);
        if (t == null) return false;
        if (System.currentTimeMillis() - t < CIRCUIT_OPEN_MS) return true;
        circuitOpenTime.remove(symbol);
        failureCount.put(symbol, 0);
        log.info("⚡ Circuit RESET for {}", symbol);
        return false;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Async DB Save
    // ═════════════════════════════════════════════════════════════════════
    private void saveAsync(AiAnalysisResult r) {
        CompletableFuture.runAsync(() -> {
            try { aiRepository.save(r); }
            catch (Exception e) { log.error("❌ DB save error {}: {}", r.getSymbol(), e.getMessage()); }
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // REST Performance Stats — /api/ai/stats
    // ═════════════════════════════════════════════════════════════════════
    public Map<String, Object> getPerformanceStats() {
        long   total      = totalRequests.get();
        long   api        = apiCallsMade.get();
        double saved      = total > 0 ? ((double)(total - api) / total) * 100.0 : 0.0;
        double avgLatency = api   > 0 ? (double) totalLatencyMs.get() / api      : 0.0;

        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("provider",               "GEMINI");
        stats.put("model",                  geminiModel);
        stats.put("dailyUsed",              dailyCallCount.get());
        stats.put("dailyLimit",             dailyLimit);
        stats.put("dailyRemaining",         dailyLimit - dailyCallCount.get());
        stats.put("budgetExhausted",        budgetExhausted.get());
        stats.put("totalRequests",          total);
        stats.put("apiCallsMade",           api);
        stats.put("cacheHits",              cacheHits.get());
        stats.put("anomalyDetected",        anomalyDetected.get());
        stats.put("marketClosedHits",       marketClosedHits.get());
        stats.put("rateLimitHits",          rateLimitHits.get());
        stats.put("circuitBreaks",          circuitBreaks.get());
        stats.put("retryCount",             retryCount.get());
        stats.put("avgLatencyMs",           String.format("%.0f", avgLatency));
        stats.put("apiCallsSavedPercent",   String.format("%.1f%%", saved));
        // Technical stats
        stats.put("technicalAnalysisDone",  technicalAnalysisDone.get());
        stats.put("zScoreAnomalies",        zScoreAnomalies.get());
        stats.put("pumpDumpDetected",       pumpDumpDetected.get());
        stats.put("highRiskDetected",       highRiskDetected.get());
        // Risk engine stats
        stats.put("riskProfilesComputed",   riskProfilesComputed.get());
        stats.put("criticalRiskEvents",     criticalRiskEvents.get());
        // Confluence stats
        stats.put("confluenceSignals",      confluenceSignals.get());
        stats.put("strongConfluenceEvents", strongConfluenceEvents.get());
        // Market
        stats.put("nseMarketOpen",          isNseMarketOpen());
        stats.put("trackedSymbols",         lastAnalysisTime.size());
        return stats;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════
    private double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private String safe(String v, String fallback)          { return v != null && !v.isBlank() ? v : fallback; }
    private String sanitize(String v, Set<String> valid, String fb) {
        String u = v != null ? v.toUpperCase().trim() : fb;
        return valid.contains(u) ? u : fb;
    }

    private AiAnalysisResult buildFallback(String symbol) {
        AiAnalysisResult r = new AiAnalysisResult();
        r.setSymbol(symbol);
        r.setSentiment("NEUTRAL");
        r.setSentimentScore(0.0);
        r.setSignal("HOLD");
        r.setSummary("AI analysis temporarily unavailable.");
        r.setAnomaly(false);
        r.setAnomalyScore(0.0);
        r.setSource("FALLBACK");
        r.setConfidenceScore(0.0);
        r.setModelUsed("none");
        r.setResponseTimeMs(0);
        r.setExpiresAt(Instant.now().plusSeconds(300));
        return r;
    }
}