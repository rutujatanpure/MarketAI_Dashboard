package com.marketai.dashboard.controller;

import com.marketai.dashboard.model.BacktestResult;
import com.marketai.dashboard.model.RiskProfile;
import com.marketai.dashboard.service.BacktestingEngine;
import com.marketai.dashboard.service.MultiTimeframeService;
import com.marketai.dashboard.service.MultiTimeframeService.ConfluenceResult;
import com.marketai.dashboard.service.SmartRiskEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BacktestController + RiskController — REST API for new features
 *
 * Endpoints:
 *
 * BACKTEST:
 *   POST /api/backtest/run?symbol=BTCUSDT&strategy=ANOMALY_DETECTION&days=90
 *   GET  /api/backtest/results?symbol=BTCUSDT
 *   GET  /api/backtest/best?symbol=BTCUSDT
 *   GET  /api/backtest/all
 *   GET  /api/backtest/system-accuracy   ← for admin dashboard
 *
 * RISK:
 *   GET  /api/risk/latest?symbol=BTCUSDT
 *   GET  /api/risk/high-risk?minScore=70
 *   GET  /api/risk/portfolio?symbols=BTCUSDT,ETHUSDT,RELIANCE
 *
 * CONFLUENCE:
 *   GET  /api/confluence/latest?symbol=BTCUSDT
 */
@RestController
@CrossOrigin(origins = "*")
public class BacktestAndRiskController {

    private final BacktestingEngine     backtestingEngine;
    private final SmartRiskEngine       smartRiskEngine;
    private final MultiTimeframeService multiTimeframeService;

    public BacktestAndRiskController(BacktestingEngine backtestingEngine,
                                     SmartRiskEngine smartRiskEngine,
                                     MultiTimeframeService multiTimeframeService) {
        this.backtestingEngine     = backtestingEngine;
        this.smartRiskEngine       = smartRiskEngine;
        this.multiTimeframeService = multiTimeframeService;
    }

    // ════════════════════════════════════════════════════════════════════════
    // BACKTEST ENDPOINTS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Trigger a backtest run (async — returns immediately, result saved to DB)
     * Admin only
     */
    @PostMapping("/api/backtest/run")
    public ResponseEntity<Map<String, Object>> runBacktest(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "ANOMALY_DETECTION") String strategy,
            @RequestParam(defaultValue = "90") int days) {

        backtestingEngine.runBacktest(symbol.toUpperCase(), strategy, days);

        Map<String, Object> resp = new HashMap<>();
        resp.put("status",   "STARTED");
        resp.put("symbol",   symbol.toUpperCase());
        resp.put("strategy", strategy);
        resp.put("days",     days);
        resp.put("message",  "Backtest running async. Check /api/backtest/results?symbol=" + symbol);
        return ResponseEntity.ok(resp);
    }

    /**
     * Get all backtest results for a symbol
     */
    @GetMapping("/api/backtest/results")
    public ResponseEntity<List<BacktestResult>> getResults(@RequestParam String symbol) {
        return ResponseEntity.ok(
                backtestingEngine.getLatestResults(symbol.toUpperCase())
        );
    }

    /**
     * Get the best performing backtest for a symbol
     */
    @GetMapping("/api/backtest/best")
    public ResponseEntity<BacktestResult> getBest(@RequestParam String symbol) {
        return backtestingEngine.getBestResult(symbol.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all latest backtest results across all symbols (for admin dashboard)
     */
    @GetMapping("/api/backtest/all")
    public ResponseEntity<List<BacktestResult>> getAll() {
        return ResponseEntity.ok(backtestingEngine.getAllLatest());
    }

    /**
     * System accuracy summary — shown on admin dashboard
     * "Overall AI signal accuracy across all backtested symbols"
     */
    @GetMapping("/api/backtest/system-accuracy")
    public ResponseEntity<Map<String, Object>> getSystemAccuracy() {
        List<BacktestResult> all = backtestingEngine.getAllLatest();

        if (all.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("message", "No backtest data yet. Run backtests first.");
            return ResponseEntity.ok(empty);
        }

        // Average key metrics across all backtests
        double avgAccuracy  = all.stream().mapToDouble(BacktestResult::getAccuracy).average().orElse(0);
        double avgPrecision = all.stream().mapToDouble(BacktestResult::getPrecision).average().orElse(0);
        double avgRecall    = all.stream().mapToDouble(BacktestResult::getRecall).average().orElse(0);
        double avgF1        = all.stream().mapToDouble(BacktestResult::getF1Score).average().orElse(0);
        double avgSharpe    = all.stream().mapToDouble(BacktestResult::getSharpeRatio).average().orElse(0);
        double avgPnl       = all.stream().mapToDouble(BacktestResult::getSimulatedPnlPercent).average().orElse(0);
        double avgWinRate   = all.stream().mapToDouble(BacktestResult::getWinRate).average().orElse(0);

        long aPlus  = all.stream().filter(r -> "A+".equals(r.getGrade())).count();
        long aGrade = all.stream().filter(r -> "A".equals(r.getGrade())).count();
        long bGrade = all.stream().filter(r -> "B".equals(r.getGrade())).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBacktests",     all.size());
        stats.put("avgAccuracyPct",     fmt(avgAccuracy  * 100));
        stats.put("avgPrecisionPct",    fmt(avgPrecision * 100));
        stats.put("avgRecallPct",       fmt(avgRecall    * 100));
        stats.put("avgF1Score",         fmt(avgF1));
        stats.put("avgSharpeRatio",     fmt(avgSharpe));
        stats.put("avgSimulatedPnlPct", fmt(avgPnl));
        stats.put("avgWinRatePct",      fmt(avgWinRate   * 100));
        stats.put("gradeAPlusCount",    aPlus);
        stats.put("gradeACount",        aGrade);
        stats.put("gradeBCount",        bGrade);
        stats.put("systemGrade",        avgF1 >= 0.7 ? "A" : avgF1 >= 0.6 ? "B" : "C");
        // The key claim for Google interview
        stats.put("keyClaimForInterview",
                String.format("Anomaly detection: %.0f%% precision, %.0f%% recall, F1=%.2f",
                        avgPrecision*100, avgRecall*100, avgF1));

        return ResponseEntity.ok(stats);
    }

    // ════════════════════════════════════════════════════════════════════════
    // RISK ENDPOINTS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Latest risk profile for a symbol
     */
    @GetMapping("/api/risk/latest")
    public ResponseEntity<RiskProfile> getLatestRisk(@RequestParam String symbol) {
        return smartRiskEngine.getLatestRisk(symbol.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(null));
    }

    /**
     * All currently high-risk symbols (score >= minScore)
     */
    @GetMapping("/api/risk/high-risk")
    public ResponseEntity<List<RiskProfile>> getHighRisk(
            @RequestParam(defaultValue = "70") int minScore) {
        return ResponseEntity.ok(smartRiskEngine.getHighRiskSymbols(minScore));
    }

    /**
     * Portfolio risk analysis — weighted risk across multiple symbols
     * Used in User Dashboard "Portfolio Risk" card
     */
    @GetMapping("/api/risk/portfolio")
    public ResponseEntity<Map<String, Object>> getPortfolioRisk(
            @RequestParam String symbols) {

        String[] symbolList = symbols.split(",");
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> symbolRisks = new HashMap<>();

        double totalRisk     = 0;
        double maxRisk       = 0;
        String highestRiskSym = "";
        int    count         = 0;

        for (String sym : symbolList) {
            String s = sym.trim().toUpperCase();
            var profile = smartRiskEngine.getLatestRisk(s);
            if (profile.isPresent()) {
                RiskProfile rp = profile.get();
                int score = rp.getCompositeRiskScore();
                totalRisk += score;
                count++;
                symbolRisks.put(s, Map.of(
                        "riskScore",  score,
                        "riskLevel",  rp.getRiskLevel(),
                        "regime",     rp.getMarketRegime(),
                        "var95",      rp.getVar95(),
                        "summary",    rp.getRiskSummary()
                ));
                if (score > maxRisk) {
                    maxRisk       = score;
                    highestRiskSym = s;
                }
            }
        }

        double avgRisk = count > 0 ? totalRisk / count : 50;
        String portfolioLevel = avgRisk >= 80 ? "CRITICAL"
                : avgRisk >= 60 ? "HIGH"
                : avgRisk >= 40 ? "MEDIUM" : "LOW";

        result.put("portfolioRiskScore",  fmt(avgRisk));
        result.put("portfolioRiskLevel",  portfolioLevel);
        result.put("highestRiskSymbol",   highestRiskSym);
        result.put("highestRiskScore",    (int) maxRisk);
        result.put("symbolCount",         count);
        result.put("symbolRisks",         symbolRisks);
        result.put("recommendation",      buildPortfolioReco(avgRisk, highestRiskSym));

        return ResponseEntity.ok(result);
    }

    // ════════════════════════════════════════════════════════════════════════
    // CONFLUENCE ENDPOINTS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Latest multi-timeframe confluence for a symbol
     */
    @GetMapping("/api/confluence/latest")
    public ResponseEntity<Map<String, Object>> getConfluence(@RequestParam String symbol) {
        var optional = multiTimeframeService.getLastConfluence(symbol.toUpperCase());
        if (optional.isEmpty()) {
            return ResponseEntity.ok(null);
        }

        ConfluenceResult cr = optional.get();

        Map<String, Object> response = new HashMap<>();
        response.put("symbol",           cr.symbol);
        response.put("confluenceSignal", cr.confluenceSignal);
        response.put("confluenceCount",  cr.confluenceCount);
        response.put("multiplier",       cr.multiplier);
        response.put("confidenceText",   cr.confidenceText);
        response.put("avgStrength",      fmt(cr.avgSignalStrength));
        response.put("anomalyCount",     cr.anomalyCount);
        response.put("isStrongSignal",   cr.isStrongSignal());
        response.put("isConfirmed",      cr.isConfirmedSignal());

        // Per-timeframe breakdown
        Map<String, Object> timeframes = new HashMap<>();
        cr.windows.forEach((tf, w) -> {
            Map<String, Object> tfData = new HashMap<>();
            tfData.put("signal",     w.getSignal());
            tfData.put("rsi",        fmt(w.getRsi()));
            tfData.put("zScore",     fmt(w.getZScore()));
            tfData.put("volRatio",   fmt(w.getVolumeRatio()));
            tfData.put("strength",   fmt(w.getSignalStrength()));
            tfData.put("anomaly",    w.isAnomaly());
            timeframes.put(tf.label, tfData);
        });
        response.put("timeframes", timeframes);

        return ResponseEntity.ok(null);
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private String buildPortfolioReco(double avgRisk, String highestSym) {
        if (avgRisk >= 80) return "CRITICAL: Reduce all positions. Portfolio at extreme risk.";
        if (avgRisk >= 60) return "HIGH: Consider reducing " + highestSym + " exposure significantly.";
        if (avgRisk >= 40) return "MEDIUM: Monitor " + highestSym + " closely. Set stop-losses.";
        return "LOW: Portfolio risk within acceptable levels. Continue monitoring.";
    }

    private double fmt(double v) { return Math.round(v * 100.0) / 100.0; }
}