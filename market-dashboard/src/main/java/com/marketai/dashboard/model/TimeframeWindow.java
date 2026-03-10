package com.marketai.dashboard.model;

import java.time.Instant;

/**
 * TimeframeWindow — technical indicator snapshot for ONE timeframe
 * Used by MultiTimeframeService to aggregate signals across 1m/5m/15m/1h
 *
 * Not stored in MongoDB — computed on-demand, held in memory
 */
public class TimeframeWindow {

    public enum Timeframe {
        ONE_MIN("1m", 60),
        FIVE_MIN("5m", 300),
        FIFTEEN_MIN("15m", 900),
        ONE_HOUR("1h", 3600);

        public final String label;
        public final int    seconds;
        Timeframe(String l, int s) { this.label = l; this.seconds = s; }
    }

    private Timeframe timeframe;
    private String    symbol;
    private Instant   computedAt;

    // ── Technical values for this timeframe ──────────────────────────────
    private double rsi;
    private double macdHistogram;
    private double zScore;
    private double volumeRatio;
    private double atr;
    private double bollingerPosition;  // 0.0–1.0
    private int    riskScore;
    private double pumpDumpProbability;

    // ── Derived signals ───────────────────────────────────────────────────
    /** BUY / SELL / HOLD */
    private String signal;

    /** BULLISH / BEARISH / NEUTRAL */
    private String trend;

    /** How strong this signal is: 0.0–1.0 */
    private double signalStrength;

    /** Is this timeframe showing anomaly? */
    private boolean anomaly;

    // ── Constructors ──────────────────────────────────────────────────────
    public TimeframeWindow() {}

    public TimeframeWindow(Timeframe tf, String symbol) {
        this.timeframe  = tf;
        this.symbol     = symbol;
        this.computedAt = Instant.now();
    }

    // ── Derive signal from indicators ─────────────────────────────────────
    public void deriveSignal() {
        int buyScore  = 0;
        int sellScore = 0;

        // RSI
        if (rsi < 35)      buyScore  += 2;
        else if (rsi < 45) buyScore  += 1;
        else if (rsi > 65) sellScore += 2;
        else if (rsi > 55) sellScore += 1;

        // MACD
        if (macdHistogram > 0)  buyScore  += 1;
        else                    sellScore += 1;

        // Z-Score
        if (zScore < -2.0) buyScore  += 2;  // price way below mean
        else if (zScore > 2.0) sellScore += 2; // price way above mean

        // Bollinger
        if (bollingerPosition < 0.1)      buyScore  += 2;
        else if (bollingerPosition > 0.9) sellScore += 2;

        // Determine signal
        if (buyScore > sellScore + 1)  this.signal = "BUY";
        else if (sellScore > buyScore + 1) this.signal = "SELL";
        else this.signal = "HOLD";

        // Signal strength
        int total = buyScore + sellScore;
        int diff  = Math.abs(buyScore - sellScore);
        this.signalStrength = total > 0 ? (double) diff / total : 0.0;

        // Trend
        if (macdHistogram > 0 && rsi > 50) this.trend = "BULLISH";
        else if (macdHistogram < 0 && rsi < 50) this.trend = "BEARISH";
        else this.trend = "NEUTRAL";

        // Anomaly
        this.anomaly = Math.abs(zScore) > 2.5 || volumeRatio > 2.5 || riskScore > 75;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────
    public Timeframe getTimeframe() { return timeframe; }
    public void setTimeframe(Timeframe t) { this.timeframe = t; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String s) { this.symbol = s; }

    public Instant getComputedAt() { return computedAt; }
    public void setComputedAt(Instant t) { this.computedAt = t; }

    public double getRsi() { return rsi; }
    public void setRsi(double v) { this.rsi = v; }

    public double getMacdHistogram() { return macdHistogram; }
    public void setMacdHistogram(double v) { this.macdHistogram = v; }

    public double getZScore() { return zScore; }
    public void setZScore(double v) { this.zScore = v; }

    public double getVolumeRatio() { return volumeRatio; }
    public void setVolumeRatio(double v) { this.volumeRatio = v; }

    public double getAtr() { return atr; }
    public void setAtr(double v) { this.atr = v; }

    public double getBollingerPosition() { return bollingerPosition; }
    public void setBollingerPosition(double v) { this.bollingerPosition = v; }

    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int v) { this.riskScore = v; }

    public double getPumpDumpProbability() { return pumpDumpProbability; }
    public void setPumpDumpProbability(double v) { this.pumpDumpProbability = v; }

    public String getSignal() { return signal; }
    public void setSignal(String s) { this.signal = s; }

    public String getTrend() { return trend; }
    public void setTrend(String t) { this.trend = t; }

    public double getSignalStrength() { return signalStrength; }
    public void setSignalStrength(double v) { this.signalStrength = v; }

    public boolean isAnomaly() { return anomaly; }
    public void setAnomaly(boolean a) { this.anomaly = a; }
}