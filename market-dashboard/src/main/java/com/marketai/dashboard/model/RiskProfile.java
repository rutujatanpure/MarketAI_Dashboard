package com.marketai.dashboard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * RiskProfile — per-symbol comprehensive risk snapshot
 *
 * Real-world problem solved:
 * Retail investors have no way to quantify HOW RISKY a position is
 * before entering. This model gives them a 0-100 score built from
 * 6 independent risk dimensions — same approach used by institutional
 * risk desks at hedge funds.
 *
 * Stored in MongoDB collection: risk_profiles
 */
@Document(collection = "risk_profiles")
@CompoundIndex(name = "symbol_ts", def = "{'symbol':1,'timestamp':-1}")
public class RiskProfile {

    @Id
    private String id;

    @Indexed
    private String symbol;

    private Instant timestamp;

    // ── Component Scores (0-100 each) ──────────────────────────────────────
    /** Price volatility risk — based on ATR + standard deviation */
    private int priceVolatilityScore;

    /** Volume anomaly risk — based on volume ratio + spike detection */
    private int volumeAnomalyScore;

    /** Statistical anomaly risk — based on Z-Score magnitude */
    private int statisticalAnomalyScore;

    /** Momentum risk — RSI extremes + MACD divergence */
    private int momentumRiskScore;

    /** Manipulation risk — pump/dump probability + Bollinger squeeze */
    private int manipulationRiskScore;

    /** Market regime risk — trending vs ranging vs volatile */
    private int regimeRiskScore;

    // ── Composite Score ────────────────────────────────────────────────────
    /** Final weighted composite: 0=safe, 100=extremely risky */
    private int compositeRiskScore;

    /** LOW / MEDIUM / HIGH / CRITICAL */
    private String riskLevel;

    /** Short human-readable explanation of the biggest risk factor */
    private String riskSummary;

    /** Which component drove the risk score highest */
    private String dominantRiskFactor;

    // ── Market Regime ─────────────────────────────────────────────────────
    /** TRENDING_UP / TRENDING_DOWN / RANGING / VOLATILE / CRASH */
    private String marketRegime;

    /** Confidence in regime detection: 0.0–1.0 */
    private double regimeConfidence;

    // ── Timeframe Confluence ───────────────────────────────────────────────
    /** How many timeframes (1m/5m/15m/1h) agree on risk direction */
    private int confluenceCount;          // 0–4

    /** Combined signal across all timeframes: BUY / SELL / HOLD / CONFLICTED */
    private String confluenceSignal;

    /** Confidence multiplier from confluence: 0.5–2.0 */
    private double confluenceMultiplier;

    // ── Value at Risk ──────────────────────────────────────────────────────
    /** Estimated max downside in next 24h (percentage) — 95% confidence */
    private double var95;

    /** Estimated max downside in next 24h (percentage) — 99% confidence */
    private double var99;

    // ── Price context ──────────────────────────────────────────────────────
    private double priceAtSnapshot;
    private double priceChange24h;

    // ── Getters / Setters ──────────────────────────────────────────────────
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public int getPriceVolatilityScore() { return priceVolatilityScore; }
    public void setPriceVolatilityScore(int v) { this.priceVolatilityScore = v; }

    public int getVolumeAnomalyScore() { return volumeAnomalyScore; }
    public void setVolumeAnomalyScore(int v) { this.volumeAnomalyScore = v; }

    public int getStatisticalAnomalyScore() { return statisticalAnomalyScore; }
    public void setStatisticalAnomalyScore(int v) { this.statisticalAnomalyScore = v; }

    public int getMomentumRiskScore() { return momentumRiskScore; }
    public void setMomentumRiskScore(int v) { this.momentumRiskScore = v; }

    public int getManipulationRiskScore() { return manipulationRiskScore; }
    public void setManipulationRiskScore(int v) { this.manipulationRiskScore = v; }

    public int getRegimeRiskScore() { return regimeRiskScore; }
    public void setRegimeRiskScore(int v) { this.regimeRiskScore = v; }

    public int getCompositeRiskScore() { return compositeRiskScore; }
    public void setCompositeRiskScore(int v) { this.compositeRiskScore = v; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String v) { this.riskLevel = v; }

    public String getRiskSummary() { return riskSummary; }
    public void setRiskSummary(String v) { this.riskSummary = v; }

    public String getDominantRiskFactor() { return dominantRiskFactor; }
    public void setDominantRiskFactor(String v) { this.dominantRiskFactor = v; }

    public String getMarketRegime() { return marketRegime; }
    public void setMarketRegime(String v) { this.marketRegime = v; }

    public double getRegimeConfidence() { return regimeConfidence; }
    public void setRegimeConfidence(double v) { this.regimeConfidence = v; }

    public int getConfluenceCount() { return confluenceCount; }
    public void setConfluenceCount(int v) { this.confluenceCount = v; }

    public String getConfluenceSignal() { return confluenceSignal; }
    public void setConfluenceSignal(String v) { this.confluenceSignal = v; }

    public double getConfluenceMultiplier() { return confluenceMultiplier; }
    public void setConfluenceMultiplier(double v) { this.confluenceMultiplier = v; }

    public double getVar95() { return var95; }
    public void setVar95(double v) { this.var95 = v; }

    public double getVar99() { return var99; }
    public void setVar99(double v) { this.var99 = v; }

    public double getPriceAtSnapshot() { return priceAtSnapshot; }
    public void setPriceAtSnapshot(double v) { this.priceAtSnapshot = v; }

    public double getPriceChange24h() { return priceChange24h; }
    public void setPriceChange24h(double v) { this.priceChange24h = v; }
}