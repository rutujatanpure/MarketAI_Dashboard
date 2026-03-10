package com.marketai.dashboard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * BacktestResult — stores results of historical signal accuracy testing
 *
 * Real-world problem solved:
 * "Your AI says BUY — but is it actually right?"
 * Without backtesting, AI signals are just guesses.
 * This proves: "Our anomaly detection had 71% precision over 6 months
 * of BTC data" — concrete, quantifiable, interview-worthy claim.
 *
 * Stored in MongoDB: backtest_results
 */
@Document(collection = "backtest_results")
public class BacktestResult {

    @Id
    private String id;

    @Indexed
    private String symbol;

    /** ANOMALY_DETECTION / AI_SIGNAL / RISK_SCORE / CONFLUENCE */
    private String strategyType;

    private Instant runAt;
    private Instant dataFrom;
    private Instant dataTo;
    private int totalDataPoints;

    // ── Signal Performance ─────────────────────────────────────────────────
    /** Total signals generated */
    private int totalSignals;

    /** Signals where price moved in predicted direction */
    private int correctSignals;

    /** Signals where price moved against prediction */
    private int incorrectSignals;

    /** False anomalies — flagged but price was normal */
    private int falsePositives;

    /** Real anomalies missed by the model */
    private int falseNegatives;

    // ── Accuracy Metrics ───────────────────────────────────────────────────
    /** correct / total — overall accuracy */
    private double accuracy;

    /** TP / (TP + FP) — of all signals, how many were real */
    private double precision;

    /** TP / (TP + FN) — of all real events, how many we caught */
    private double recall;

    /** Harmonic mean of precision + recall */
    private double f1Score;

    // ── P&L Simulation ─────────────────────────────────────────────────────
    /** Simulated P&L if you followed all signals (%) */
    private double simulatedPnlPercent;

    /** Simulated P&L vs buy-and-hold benchmark (%) */
    private double pnlVsBenchmark;

    /** Max drawdown during simulation (%) */
    private double maxDrawdownPercent;

    /** Sharpe ratio of simulated returns */
    private double sharpeRatio;

    /** Win rate of individual trades */
    private double winRate;

    /** Average gain on winning trades (%) */
    private double avgWinPercent;

    /** Average loss on losing trades (%) */
    private double avgLossPercent;

    // ── Threshold Performance ──────────────────────────────────────────────
    /** Z-Score threshold used in this backtest run */
    private double zScoreThreshold;

    /** Risk score threshold used */
    private int riskScoreThreshold;

    /** Optimal threshold found during backtest */
    private double optimalZScoreThreshold;

    // ── Summary ────────────────────────────────────────────────────────────
    /** Human-readable result summary */
    private String summary;

    /** Grade: A+ / A / B / C / D */
    private String grade;

    /** Per-signal detail list (last 20 signals for UI display) */
    private List<SignalDetail> recentSignals;

    // ── Inner class for individual signal results ──────────────────────────
    public static class SignalDetail {
        public Instant timestamp;
        public String  signalType;     // BUY / SELL / ANOMALY
        public double  priceAtSignal;
        public double  priceAfter24h;
        public double  actualChange;
        public boolean correct;
        public double  zScoreAtSignal;
        public int     riskScoreAtSignal;

        public SignalDetail() {}
        public SignalDetail(Instant ts, String type, double price, double after,
                            double chg, boolean ok, double z, int risk) {
            this.timestamp       = ts;
            this.signalType      = type;
            this.priceAtSignal   = price;
            this.priceAfter24h   = after;
            this.actualChange    = chg;
            this.correct         = ok;
            this.zScoreAtSignal  = z;
            this.riskScoreAtSignal = risk;
        }
    }

    // ── Getters / Setters ──────────────────────────────────────────────────
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String s) { this.symbol = s; }

    public String getStrategyType() { return strategyType; }
    public void setStrategyType(String s) { this.strategyType = s; }

    public Instant getRunAt() { return runAt; }
    public void setRunAt(Instant t) { this.runAt = t; }

    public Instant getDataFrom() { return dataFrom; }
    public void setDataFrom(Instant t) { this.dataFrom = t; }

    public Instant getDataTo() { return dataTo; }
    public void setDataTo(Instant t) { this.dataTo = t; }

    public int getTotalDataPoints() { return totalDataPoints; }
    public void setTotalDataPoints(int n) { this.totalDataPoints = n; }

    public int getTotalSignals() { return totalSignals; }
    public void setTotalSignals(int n) { this.totalSignals = n; }

    public int getCorrectSignals() { return correctSignals; }
    public void setCorrectSignals(int n) { this.correctSignals = n; }

    public int getIncorrectSignals() { return incorrectSignals; }
    public void setIncorrectSignals(int n) { this.incorrectSignals = n; }

    public int getFalsePositives() { return falsePositives; }
    public void setFalsePositives(int n) { this.falsePositives = n; }

    public int getFalseNegatives() { return falseNegatives; }
    public void setFalseNegatives(int n) { this.falseNegatives = n; }

    public double getAccuracy() { return accuracy; }
    public void setAccuracy(double v) { this.accuracy = v; }

    public double getPrecision() { return precision; }
    public void setPrecision(double v) { this.precision = v; }

    public double getRecall() { return recall; }
    public void setRecall(double v) { this.recall = v; }

    public double getF1Score() { return f1Score; }
    public void setF1Score(double v) { this.f1Score = v; }

    public double getSimulatedPnlPercent() { return simulatedPnlPercent; }
    public void setSimulatedPnlPercent(double v) { this.simulatedPnlPercent = v; }

    public double getPnlVsBenchmark() { return pnlVsBenchmark; }
    public void setPnlVsBenchmark(double v) { this.pnlVsBenchmark = v; }

    public double getMaxDrawdownPercent() { return maxDrawdownPercent; }
    public void setMaxDrawdownPercent(double v) { this.maxDrawdownPercent = v; }

    public double getSharpeRatio() { return sharpeRatio; }
    public void setSharpeRatio(double v) { this.sharpeRatio = v; }

    public double getWinRate() { return winRate; }
    public void setWinRate(double v) { this.winRate = v; }

    public double getAvgWinPercent() { return avgWinPercent; }
    public void setAvgWinPercent(double v) { this.avgWinPercent = v; }

    public double getAvgLossPercent() { return avgLossPercent; }
    public void setAvgLossPercent(double v) { this.avgLossPercent = v; }

    public double getZScoreThreshold() { return zScoreThreshold; }
    public void setZScoreThreshold(double v) { this.zScoreThreshold = v; }

    public int getRiskScoreThreshold() { return riskScoreThreshold; }
    public void setRiskScoreThreshold(int v) { this.riskScoreThreshold = v; }

    public double getOptimalZScoreThreshold() { return optimalZScoreThreshold; }
    public void setOptimalZScoreThreshold(double v) { this.optimalZScoreThreshold = v; }

    public String getSummary() { return summary; }
    public void setSummary(String s) { this.summary = s; }

    public String getGrade() { return grade; }
    public void setGrade(String g) { this.grade = g; }

    public List<SignalDetail> getRecentSignals() { return recentSignals; }
    public void setRecentSignals(List<SignalDetail> l) { this.recentSignals = l; }
}