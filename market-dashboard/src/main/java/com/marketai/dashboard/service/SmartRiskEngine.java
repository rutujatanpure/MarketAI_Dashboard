package com.marketai.dashboard.service;

import com.marketai.dashboard.model.CryptoPriceEvent;
import com.marketai.dashboard.model.RiskProfile;
import com.marketai.dashboard.model.TechnicalIndicator;
import com.marketai.dashboard.repository.MarketPriceRepository;
import com.marketai.dashboard.repository.RiskProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class SmartRiskEngine {

    private static final Logger log = LoggerFactory.getLogger(SmartRiskEngine.class);

    // ── Weights for composite score ────────────────────────────────────────
    // Must sum to 1.0
    private static final double W_PRICE_VOL    = 0.20;
    private static final double W_VOLUME       = 0.15;
    private static final double W_STATISTICAL  = 0.25;  // Z-Score most important
    private static final double W_MOMENTUM     = 0.20;
    private static final double W_MANIPULATION = 0.15;
    private static final double W_REGIME       = 0.05;

    // ── Risk level thresholds ──────────────────────────────────────────────
    private static final int CRITICAL_THRESHOLD = 80;
    private static final int HIGH_THRESHOLD     = 60;
    private static final int MEDIUM_THRESHOLD   = 40;

    // ── VaR constants (normal distribution z-values) ──────────────────────
    private static final double Z_95 = 1.645;
    private static final double Z_99 = 2.326;

    // ── Historical return windows (per symbol, last 100 prices) ───────────
    private final Map<String, Deque<Double>> returnWindows = new ConcurrentHashMap<>();
    private static final int RETURN_WINDOW = 100;

    private final RiskProfileRepository  riskProfileRepository;
    private final MarketPriceRepository  priceRepository;
    private final SimpMessagingTemplate  messagingTemplate;

    public SmartRiskEngine(RiskProfileRepository riskProfileRepository,
                           MarketPriceRepository priceRepository,
                           SimpMessagingTemplate messagingTemplate) {
        this.riskProfileRepository = riskProfileRepository;
        this.priceRepository       = priceRepository;
        this.messagingTemplate     = messagingTemplate;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAIN ENTRY — called from AiAnalysisService on every tick
    // Returns full RiskProfile — saved to DB + broadcast via WebSocket
    // ═══════════════════════════════════════════════════════════════════════
    public RiskProfile computeRisk(String symbol,
                                   CryptoPriceEvent event,
                                   TechnicalIndicator indicator,
                                   List<CryptoPriceEvent> history) {
        try {
            RiskProfile profile = new RiskProfile();
            profile.setSymbol(symbol);
            profile.setTimestamp(Instant.now());
            profile.setPriceAtSnapshot(event.getPrice());
            profile.setPriceChange24h(event.getPriceChange());

            // ── Update rolling return window ───────────────────────────────
            updateReturnWindow(symbol, event.getPrice(), history);

            // ── 1. Price Volatility Score ──────────────────────────────────
            int priceVolScore = computePriceVolatilityScore(indicator, history);
            profile.setPriceVolatilityScore(priceVolScore);

            // ── 2. Volume Anomaly Score ────────────────────────────────────
            int volAnomalyScore = computeVolumeAnomalyScore(indicator);
            profile.setVolumeAnomalyScore(volAnomalyScore);

            // ── 3. Statistical Anomaly Score (Z-Score based) ───────────────
            int statAnomalyScore = computeStatisticalAnomalyScore(indicator);
            profile.setStatisticalAnomalyScore(statAnomalyScore);

            // ── 4. Momentum Risk Score ─────────────────────────────────────
            int momentumScore = computeMomentumRiskScore(indicator);
            profile.setMomentumRiskScore(momentumScore);

            // ── 5. Manipulation Risk Score ─────────────────────────────────
            int manipScore = computeManipulationRiskScore(indicator);
            profile.setManipulationRiskScore(manipScore);

            // ── 6. Market Regime ───────────────────────────────────────────
            String regime          = detectMarketRegime(indicator, history, event);
            int    regimeRiskScore = regimeToRiskScore(regime);
            double regimeConf      = computeRegimeConfidence(indicator, history);
            profile.setMarketRegime(regime);
            profile.setRegimeRiskScore(regimeRiskScore);
            profile.setRegimeConfidence(regimeConf);

            // ── Composite Score ────────────────────────────────────────────
            int composite = (int)(
                    priceVolScore    * W_PRICE_VOL   +
                            volAnomalyScore  * W_VOLUME      +
                            statAnomalyScore * W_STATISTICAL +
                            momentumScore    * W_MOMENTUM    +
                            manipScore       * W_MANIPULATION +
                            regimeRiskScore  * W_REGIME
            );
            composite = clamp(composite, 0, 100);
            profile.setCompositeRiskScore(composite);
            profile.setRiskLevel(toRiskLevel(composite));

            // ── Dominant Factor ────────────────────────────────────────────
            profile.setDominantRiskFactor(findDominantFactor(
                    priceVolScore, volAnomalyScore, statAnomalyScore,
                    momentumScore, manipScore, regimeRiskScore
            ));

            // ── Human-readable summary ─────────────────────────────────────
            profile.setRiskSummary(buildRiskSummary(profile, indicator, event));

            // ── Value at Risk ──────────────────────────────────────────────
            double[] varValues = computeVaR(symbol, indicator);
            profile.setVar95(varValues[0]);
            profile.setVar99(varValues[1]);

            // ── Save to MongoDB asynchronously ─────────────────────────────
            saveAsync(profile);

            // ── Broadcast via WebSocket ────────────────────────────────────
            messagingTemplate.convertAndSend("/topic/risk/" + symbol, profile);

            log.debug("🎯 Risk [{}] composite={} level={} regime={} dominant={}",
                    symbol, composite, profile.getRiskLevel(),
                    regime, profile.getDominantRiskFactor());

            return profile;

        } catch (Exception e) {
            log.error("❌ SmartRiskEngine error for {}: {}", symbol, e.getMessage());
            return buildFallbackProfile(symbol, event);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PRICE VOLATILITY SCORE (0–100)
    // Based on: ATR% + rolling standard deviation vs historical average
    // ═══════════════════════════════════════════════════════════════════════
    private int computePriceVolatilityScore(TechnicalIndicator ind,
                                            List<CryptoPriceEvent> history) {
        if (ind == null) return 50;

        double atrPct = ind.getAtrPercent();   // ATR as % of price
        double stdDev = ind.getRollingStdDev();
        double mean   = ind.getRollingMean();
        double cvPct  = (mean > 0) ? (stdDev / mean) * 100 : 0; // Coefficient of Variation

        // ATR scoring:  <0.5%=low, 0.5-2%=medium, 2-5%=high, >5%=critical
        int atrScore;
        if      (atrPct < 0.5)  atrScore = 10;
        else if (atrPct < 1.0)  atrScore = 25;
        else if (atrPct < 2.0)  atrScore = 45;
        else if (atrPct < 3.5)  atrScore = 65;
        else if (atrPct < 5.0)  atrScore = 80;
        else                    atrScore = 95;

        // CV scoring: coefficient of variation
        int cvScore;
        if      (cvPct < 1.0) cvScore = 10;
        else if (cvPct < 2.0) cvScore = 30;
        else if (cvPct < 4.0) cvScore = 55;
        else if (cvPct < 7.0) cvScore = 75;
        else                  cvScore = 90;

        return clamp((atrScore * 6 + cvScore * 4) / 10, 0, 100);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VOLUME ANOMALY SCORE (0–100)
    // ═══════════════════════════════════════════════════════════════════════
    private int computeVolumeAnomalyScore(TechnicalIndicator ind) {
        if (ind == null) return 30;

        double volRatio = ind.getVolumeRatio();  // current / 100-period avg

        if      (volRatio < 0.5)  return 15;  // Very low volume = low risk
        else if (volRatio < 1.0)  return 20;
        else if (volRatio < 1.5)  return 30;
        else if (volRatio < 2.0)  return 50;
        else if (volRatio < 3.0)  return 70;  // Volume spike = manipulation risk
        else if (volRatio < 5.0)  return 85;
        else                      return 95;  // Extreme volume = crash/pump risk
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STATISTICAL ANOMALY SCORE (0–100)
    // Z-Score: how many std devs from rolling mean
    // |Z| < 1.0 = normal, >2.5 = anomaly, >4.0 = extreme event
    // ═══════════════════════════════════════════════════════════════════════
    private int computeStatisticalAnomalyScore(TechnicalIndicator ind) {
        if (ind == null) return 30;

        double absZ = Math.abs(ind.getZScore());

        if      (absZ < 0.5)  return 5;
        else if (absZ < 1.0)  return 15;
        else if (absZ < 1.5)  return 30;
        else if (absZ < 2.0)  return 45;
        else if (absZ < 2.5)  return 60;  // anomaly threshold
        else if (absZ < 3.0)  return 75;
        else if (absZ < 4.0)  return 87;
        else                  return 97;  // Black swan territory
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MOMENTUM RISK SCORE (0–100)
    // RSI extremes = overbought/oversold = reversal risk
    // MACD divergence = trend change risk
    // ═══════════════════════════════════════════════════════════════════════
    private int computeMomentumRiskScore(TechnicalIndicator ind) {
        if (ind == null) return 40;

        double rsi  = ind.getRsi();
        double hist = ind.getMacdHistogram();

        // RSI risk: extremes = higher reversal risk
        int rsiRisk;
        if      (rsi < 20)  rsiRisk = 80;  // Extreme oversold
        else if (rsi < 30)  rsiRisk = 65;  // Oversold
        else if (rsi < 40)  rsiRisk = 40;
        else if (rsi < 60)  rsiRisk = 20;  // Neutral zone = safe
        else if (rsi < 70)  rsiRisk = 40;
        else if (rsi < 80)  rsiRisk = 65;  // Overbought
        else                rsiRisk = 80;  // Extreme overbought

        // MACD divergence risk (large histogram = momentum risk)
        int macdRisk = 20;
        double absHist = Math.abs(hist);
        if (absHist > 0)   macdRisk = Math.min(60, (int)(absHist * 10000 / 100));

        return clamp((rsiRisk * 7 + macdRisk * 3) / 10, 0, 100);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MANIPULATION RISK SCORE (0–100)
    // Pump/dump probability + Bollinger band squeeze
    // ═══════════════════════════════════════════════════════════════════════
    private int computeManipulationRiskScore(TechnicalIndicator ind) {
        if (ind == null) return 20;

        double pdProb    = ind.getPumpDumpProbability();  // 0–100
        double bbPos     = ind.getBollingerPosition();    // 0–1
        boolean isSpike  = ind.isVolumeSpike();

        // Pump/dump direct contribution
        int pdScore = (int) pdProb;

        // Bollinger extremes + volume spike = manipulation signal
        int bbScore = 20;
        if (bbPos < 0.05 || bbPos > 0.95) bbScore = 60;  // Outside bands
        if (isSpike && (bbPos > 0.85 || bbPos < 0.15)) bbScore = 85;

        return clamp((pdScore * 7 + bbScore * 3) / 10, 0, 100);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MARKET REGIME DETECTION
    // Uses: price direction + ATR + volume + price change rate
    // Returns: TRENDING_UP / TRENDING_DOWN / RANGING / VOLATILE / CRASH
    // ═══════════════════════════════════════════════════════════════════════
    private String detectMarketRegime(TechnicalIndicator ind,
                                      List<CryptoPriceEvent> history,
                                      CryptoPriceEvent event) {
        if (ind == null || history.isEmpty()) return "RANGING";

        double priceChange = event.getPriceChange();   // 24h change %
        double atrPct      = ind.getAtrPercent();
        double zScore      = ind.getZScore();
        double volRatio    = ind.getVolumeRatio();
        String trend       = ind.getTrend();

        // CRASH detection — fast drop + high volume + extreme Z-Score
        if (priceChange < -7.0 && volRatio > 2.5 && zScore < -3.0) {
            return "CRASH";
        }

        // VOLATILE — high ATR + volume but no clear direction
        if (atrPct > 4.0 && volRatio > 2.0 && Math.abs(priceChange) < 3.0) {
            return "VOLATILE";
        }

        // TRENDING_UP — sustained positive change + price above mean
        if (priceChange > 3.0 && "UPTREND".equals(trend) && zScore > 0.5) {
            return "TRENDING_UP";
        }

        // TRENDING_DOWN — sustained negative change + price below mean
        if (priceChange < -3.0 && "DOWNTREND".equals(trend) && zScore < -0.5) {
            return "TRENDING_DOWN";
        }

        // RANGING — low ATR + low volume + small price change
        if (atrPct < 1.5 && Math.abs(priceChange) < 2.0 && volRatio < 1.3) {
            return "RANGING";
        }

        // Default
        if (priceChange > 1.0)       return "TRENDING_UP";
        else if (priceChange < -1.0) return "TRENDING_DOWN";
        return "RANGING";
    }

    private double computeRegimeConfidence(TechnicalIndicator ind,
                                           List<CryptoPriceEvent> history) {
        if (ind == null) return 0.5;
        // Higher Z-Score absolute value = more confident in regime
        double absZ    = Math.abs(ind.getZScore());
        double volConf = Math.min(ind.getVolumeRatio() / 3.0, 1.0);
        return clampD(absZ * 0.15 + volConf * 0.25 + 0.3, 0.1, 0.99);
    }

    private int regimeToRiskScore(String regime) {
        return switch (regime) {
            case "CRASH"         -> 95;
            case "VOLATILE"      -> 75;
            case "TRENDING_DOWN" -> 65;
            case "TRENDING_UP"   -> 25;
            case "RANGING"       -> 20;
            default              -> 40;
        };
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VALUE AT RISK — Parametric VaR using rolling returns
    // VaR_95 = μ - 1.645σ  (95% confidence: won't lose more than this)
    // VaR_99 = μ - 2.326σ
    // ═══════════════════════════════════════════════════════════════════════
    private double[] computeVaR(String symbol, TechnicalIndicator ind) {
        Deque<Double> returns = returnWindows.get(symbol);

        if (returns == null || returns.size() < 20) {
            // Fallback: use ATR-based estimate
            double atrPct = (ind != null) ? ind.getAtrPercent() : 2.0;
            return new double[]{atrPct * 1.645, atrPct * 2.326};
        }

        // Calculate mean and std dev of returns
        double[] arr  = returns.stream().mapToDouble(Double::doubleValue).toArray();
        double   mean = Arrays.stream(arr).average().orElse(0.0);
        double   variance = Arrays.stream(arr)
                .map(r -> (r - mean) * (r - mean))
                .average().orElse(0.0);
        double stdDev = Math.sqrt(variance);

        double var95 = Math.abs(mean - Z_95 * stdDev);
        double var99 = Math.abs(mean - Z_99 * stdDev);

        return new double[]{
                Math.round(var95 * 100.0) / 100.0,
                Math.round(var99 * 100.0) / 100.0
        };
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════
    private void updateReturnWindow(String symbol, double currentPrice,
                                    List<CryptoPriceEvent> history) {
        returnWindows.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> window = returnWindows.get(symbol);

        if (!history.isEmpty() && history.get(0).getPrice() > 0) {
            double prevPrice = history.get(0).getPrice();
            double ret = ((currentPrice - prevPrice) / prevPrice) * 100.0;
            window.addLast(ret);
            if (window.size() > RETURN_WINDOW) window.pollFirst();
        }
    }

    private String findDominantFactor(int pv, int va, int sa, int mo, int ma, int re) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("PRICE_VOLATILITY",  pv);
        scores.put("VOLUME_ANOMALY",    va);
        scores.put("STATISTICAL",       sa);
        scores.put("MOMENTUM",          mo);
        scores.put("MANIPULATION",      ma);
        scores.put("MARKET_REGIME",     re);
        return Collections.max(scores.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    private String buildRiskSummary(RiskProfile profile,
                                    TechnicalIndicator ind,
                                    CryptoPriceEvent event) {
        int score = profile.getCompositeRiskScore();
        String regime = profile.getMarketRegime();
        String dominant = profile.getDominantRiskFactor();

        if (score >= CRITICAL_THRESHOLD) {
            return String.format("CRITICAL: %s regime with %s as primary risk driver. VaR-99: %.1f%%",
                    regime, dominant, profile.getVar99());
        } else if (score >= HIGH_THRESHOLD) {
            return String.format("HIGH risk: %s conditions. %s elevated. Consider reducing exposure.",
                    regime, dominant);
        } else if (score >= MEDIUM_THRESHOLD) {
            return String.format("MEDIUM risk: %s market. Monitor %s closely.",
                    regime, dominant);
        } else {
            return String.format("LOW risk: %s conditions. Normal trading environment.",
                    regime);
        }
    }

    private String toRiskLevel(int score) {
        if (score >= CRITICAL_THRESHOLD) return "CRITICAL";
        if (score >= HIGH_THRESHOLD)     return "HIGH";
        if (score >= MEDIUM_THRESHOLD)   return "MEDIUM";
        return "LOW";
    }

    private void saveAsync(RiskProfile profile) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                riskProfileRepository.save(profile);
            } catch (Exception e) {
                log.error("❌ RiskProfile DB save error: {}", e.getMessage());
            }
        });
    }

    private RiskProfile buildFallbackProfile(String symbol, CryptoPriceEvent event) {
        RiskProfile p = new RiskProfile();
        p.setSymbol(symbol);
        p.setTimestamp(Instant.now());
        p.setCompositeRiskScore(50);
        p.setRiskLevel("MEDIUM");
        p.setMarketRegime("RANGING");
        p.setRiskSummary("Risk calculation unavailable — using fallback.");
        p.setPriceAtSnapshot(event.getPrice());
        p.setPriceChange24h(event.getPriceChange());
        p.setVar95(2.0);
        p.setVar99(3.3);
        return p;
    }

    private int    clamp(int v, int min, int max)       { return Math.max(min, Math.min(max, v)); }
    private double clampD(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    // ── Public query methods (called from controller) ──────────────────────
    public Optional<RiskProfile> getLatestRisk(String symbol) {
        return riskProfileRepository.findTopBySymbolOrderByTimestampDesc(symbol);
    }

    public List<RiskProfile> getHighRiskSymbols(int minScore) {
        return riskProfileRepository.findHighRiskSymbols(minScore,
                Instant.now().minusSeconds(3600));
    }
}