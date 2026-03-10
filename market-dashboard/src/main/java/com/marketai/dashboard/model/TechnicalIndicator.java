package com.marketai.dashboard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "technical_indicators")
@CompoundIndex(name = "symbol_ts_idx", def = "{'symbol': 1, 'timestamp': -1}")
public class TechnicalIndicator {

    @Id
    private String id;

    @Indexed
    private String symbol;

    // ── Existing fields (keep as-is) ──────────────────────────────────────────
    private double rsi;
    private double macdLine;
    private double signalLine;
    private double macdHistogram;
    private double upperBand;
    private double middleBand;
    private double lowerBand;
    private String trend;              // UPTREND / DOWNTREND / SIDEWAYS

    // ── NEW: RSI Signal ───────────────────────────────────────────────────────
    private String rsiSignal;          // OVERSOLD / NEUTRAL / OVERBOUGHT

    // ── NEW: MACD Signal ──────────────────────────────────────────────────────
    private String macdSignal;         // BULLISH / BEARISH

    // ── NEW: Bollinger Position ───────────────────────────────────────────────
    private double bollingerPosition;  // 0.0 = lower band, 1.0 = upper band
    private String bollingerSignal;    // UPPER_TOUCH / LOWER_TOUCH / INSIDE

    // ── NEW: ATR (Average True Range) ─────────────────────────────────────────
    private double atr;
    private double atrPercent;         // ATR as % of price

    // ── NEW: Volume Analysis ──────────────────────────────────────────────────
    private double  volumeRatio;       // current / 100-period average
    private boolean volumeSpike;       // true if volumeRatio > 2.0
    private String  volumeSignal;      // SPIKE / HIGH / NORMAL / LOW

    // ── NEW: Z-Score Anomaly Detection ───────────────────────────────────────
    private double  zScore;
    private double  rollingMean;
    private double  rollingStdDev;
    private boolean anomaly;
    private String  anomalySeverity;   // NORMAL / MEDIUM / HIGH / CRITICAL

    // ── NEW: Multi-Factor Risk Score ──────────────────────────────────────────
    private int    riskScore;          // 0-100 overall risk
    private int    volatilityScore;    // 0-100 volatility
    private int    manipulationScore;  // 0-100 manipulation probability
    private String riskLevel;          // LOW / MEDIUM / HIGH / CRITICAL

    // ── NEW: Pump & Dump Detection ────────────────────────────────────────────
    private boolean pumpDumpSuspected;
    private double  pumpDumpProbability; // 0-100
    private String  pumpDumpPhase;       // NONE / ACCUMULATION / PUMP / DUMP

    @Indexed
    private Instant timestamp = Instant.now();

    public TechnicalIndicator() {}

    // ════════════════════════════════════════════════════════════════════════
    // Getters & Setters — Existing
    // ════════════════════════════════════════════════════════════════════════
    public String  getId()                            { return id; }
    public void    setId(String id)                   { this.id = id; }
    public String  getSymbol()                        { return symbol; }
    public void    setSymbol(String symbol)           { this.symbol = symbol; }
    public double  getRsi()                           { return rsi; }
    public void    setRsi(double rsi)                 { this.rsi = rsi; }
    public double  getMacdLine()                      { return macdLine; }
    public void    setMacdLine(double macdLine)       { this.macdLine = macdLine; }
    public double  getSignalLine()                    { return signalLine; }
    public void    setSignalLine(double signalLine)   { this.signalLine = signalLine; }
    public double  getMacdHistogram()                 { return macdHistogram; }
    public void    setMacdHistogram(double v)         { this.macdHistogram = v; }
    public double  getUpperBand()                     { return upperBand; }
    public void    setUpperBand(double upperBand)     { this.upperBand = upperBand; }
    public double  getMiddleBand()                    { return middleBand; }
    public void    setMiddleBand(double middleBand)   { this.middleBand = middleBand; }
    public double  getLowerBand()                     { return lowerBand; }
    public void    setLowerBand(double lowerBand)     { this.lowerBand = lowerBand; }
    public String  getTrend()                         { return trend; }
    public void    setTrend(String trend)             { this.trend = trend; }
    public Instant getTimestamp()                     { return timestamp; }
    public void    setTimestamp(Instant timestamp)    { this.timestamp = timestamp; }

    // ════════════════════════════════════════════════════════════════════════
    // Getters & Setters — NEW
    // ════════════════════════════════════════════════════════════════════════
    public String  getRsiSignal()                     { return rsiSignal; }
    public void    setRsiSignal(String v)             { this.rsiSignal = v; }
    public String  getMacdSignal()                    { return macdSignal; }
    public void    setMacdSignal(String v)            { this.macdSignal = v; }
    public double  getBollingerPosition()             { return bollingerPosition; }
    public void    setBollingerPosition(double v)     { this.bollingerPosition = v; }
    public String  getBollingerSignal()               { return bollingerSignal; }
    public void    setBollingerSignal(String v)       { this.bollingerSignal = v; }
    public double  getAtr()                           { return atr; }
    public void    setAtr(double v)                   { this.atr = v; }
    public double  getAtrPercent()                    { return atrPercent; }
    public void    setAtrPercent(double v)            { this.atrPercent = v; }
    public double  getVolumeRatio()                   { return volumeRatio; }
    public void    setVolumeRatio(double v)           { this.volumeRatio = v; }
    public boolean isVolumeSpike()                    { return volumeSpike; }
    public void    setVolumeSpike(boolean v)          { this.volumeSpike = v; }
    public String  getVolumeSignal()                  { return volumeSignal; }
    public void    setVolumeSignal(String v)          { this.volumeSignal = v; }
    public double  getZScore()                        { return zScore; }
    public void    setZScore(double v)                { this.zScore = v; }
    public double  getRollingMean()                   { return rollingMean; }
    public void    setRollingMean(double v)           { this.rollingMean = v; }
    public double  getRollingStdDev()                 { return rollingStdDev; }
    public void    setRollingStdDev(double v)         { this.rollingStdDev = v; }
    public boolean isAnomaly()                        { return anomaly; }
    public void    setAnomaly(boolean v)              { this.anomaly = v; }
    public String  getAnomalySeverity()               { return anomalySeverity; }
    public void    setAnomalySeverity(String v)       { this.anomalySeverity = v; }
    public int     getRiskScore()                     { return riskScore; }
    public void    setRiskScore(int v)                { this.riskScore = v; }
    public int     getVolatilityScore()               { return volatilityScore; }
    public void    setVolatilityScore(int v)          { this.volatilityScore = v; }
    public int     getManipulationScore()             { return manipulationScore; }
    public void    setManipulationScore(int v)        { this.manipulationScore = v; }
    public String  getRiskLevel()                     { return riskLevel; }
    public void    setRiskLevel(String v)             { this.riskLevel = v; }
    public boolean isPumpDumpSuspected()              { return pumpDumpSuspected; }
    public void    setPumpDumpSuspected(boolean v)    { this.pumpDumpSuspected = v; }
    public double  getPumpDumpProbability()           { return pumpDumpProbability; }
    public void    setPumpDumpProbability(double v)   { this.pumpDumpProbability = v; }
    public String  getPumpDumpPhase()                 { return pumpDumpPhase; }
    public void    setPumpDumpPhase(String v)         { this.pumpDumpPhase = v; }
}