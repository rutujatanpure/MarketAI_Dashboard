package com.marketai.dashboard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Rich alert model — replaces old AlertNotification
 * AlertNotification purana code ke saath backward compatible hai
 * Ye new collection mein save hoga: "market_alerts"
 */
@Document(collection = "market_alerts")
@CompoundIndex(name = "symbol_ts_idx", def = "{'symbol': 1, 'timestamp': -1}")
public class MarketAlert {

    @Id
    private String id;

    @Indexed
    private String symbol;

    // ── Alert Classification ──────────────────────────────────────────────────
    // Types: ANOMALY / PUMP_DUMP / VOLUME_SPIKE / RSI_EXTREME / RISK_HIGH /
    //        MACD_CROSS / AI_SIGNAL
    private String alertType;

    // Severity: LOW / MEDIUM / HIGH / CRITICAL
    private String severity;

    // Human readable message
    private String message;

    // ── Trigger Data ──────────────────────────────────────────────────────────
    private double triggerValue;    // Value that triggered alert (zScore, rsi, etc.)
    private double priceAtAlert;    // Price when alert fired
    private double priceChange;     // % change at alert time

    // ── Status ────────────────────────────────────────────────────────────────
    private boolean acknowledged = false;  // User ne dekha?
    private boolean emailSent    = false;  // Email gaya?

    // ── Technical snapshot at alert time ─────────────────────────────────────
    private double rsi;
    private double zScore;
    private double volumeRatio;
    private int    riskScore;
    private String pumpDumpPhase;

    @Indexed
    private Instant timestamp = Instant.now();

    public MarketAlert() {}

    // ════════════════════════════════════════════════════════════════════════
    // Static factory methods — clean alert creation
    // ════════════════════════════════════════════════════════════════════════

    public static MarketAlert anomaly(String symbol, double zScore,
                                      double price, double changePct) {
        MarketAlert a  = new MarketAlert();
        a.symbol       = symbol;
        a.alertType    = "ANOMALY";
        a.severity     = zScore > 4.0 ? "CRITICAL" : zScore > 3.0 ? "HIGH" : "MEDIUM";
        a.message      = String.format(
                "🚨 %s abnormal price move! Z-Score: %.2fσ (%.2f%% change)",
                symbol, zScore, changePct);
        a.triggerValue = zScore;
        a.priceAtAlert = price;
        a.priceChange  = changePct;
        a.zScore       = zScore;
        return a;
    }

    public static MarketAlert pumpDump(String symbol, double probability,
                                       double price, String phase) {
        MarketAlert a     = new MarketAlert();
        a.symbol          = symbol;
        a.alertType       = "PUMP_DUMP";
        a.severity        = probability > 80 ? "CRITICAL" : probability > 60 ? "HIGH" : "MEDIUM";
        a.message         = String.format(
                "⚠️ %s Pump & Dump suspected! Phase: %s (%.0f%% probability)",
                symbol, phase, probability);
        a.triggerValue    = probability;
        a.priceAtAlert    = price;
        a.pumpDumpPhase   = phase;
        return a;
    }

    public static MarketAlert volumeSpike(String symbol, double ratio, double price) {
        MarketAlert a  = new MarketAlert();
        a.symbol       = symbol;
        a.alertType    = "VOLUME_SPIKE";
        a.severity     = ratio > 5.0 ? "HIGH" : "MEDIUM";
        a.message      = String.format(
                "📊 %s Volume spike: %.1fx normal volume!", symbol, ratio);
        a.triggerValue = ratio;
        a.priceAtAlert = price;
        a.volumeRatio  = ratio;
        return a;
    }

    public static MarketAlert rsiExtreme(String symbol, double rsi, double price) {
        MarketAlert a  = new MarketAlert();
        a.symbol       = symbol;
        a.alertType    = "RSI_EXTREME";
        a.severity     = "MEDIUM";
        a.message      = String.format(
                "📈 %s RSI %s: %.1f — %s",
                symbol,
                rsi < 30 ? "oversold" : "overbought",
                rsi,
                rsi < 30 ? "Potential BUY opportunity" : "Potential SELL signal");
        a.triggerValue = rsi;
        a.priceAtAlert = price;
        a.rsi          = rsi;
        return a;
    }

    public static MarketAlert riskHigh(String symbol, int riskScore, double price) {
        MarketAlert a  = new MarketAlert();
        a.symbol       = symbol;
        a.alertType    = "RISK_HIGH";
        a.severity     = riskScore > 80 ? "CRITICAL" : "HIGH";
        a.message      = String.format(
                "🔴 %s Risk Score: %d/100 — %s risk level detected",
                symbol, riskScore, riskScore > 80 ? "CRITICAL" : "HIGH");
        a.triggerValue = riskScore;
        a.priceAtAlert = price;
        a.riskScore    = riskScore;
        return a;
    }

    public static MarketAlert aiSignal(String symbol, String signal,
                                       String sentiment, double price) {
        MarketAlert a  = new MarketAlert();
        a.symbol       = symbol;
        a.alertType    = "AI_SIGNAL";
        a.severity     = "LOW";
        a.message      = String.format(
                "🤖 %s AI Signal: %s | Sentiment: %s", symbol, signal, sentiment);
        a.priceAtAlert = price;
        return a;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Getters & Setters
    // ════════════════════════════════════════════════════════════════════════
    public String  getId()                        { return id; }
    public void    setId(String v)                { this.id = v; }
    public String  getSymbol()                    { return symbol; }
    public void    setSymbol(String v)            { this.symbol = v; }
    public String  getAlertType()                 { return alertType; }
    public void    setAlertType(String v)         { this.alertType = v; }
    public String  getSeverity()                  { return severity; }
    public void    setSeverity(String v)          { this.severity = v; }
    public String  getMessage()                   { return message; }
    public void    setMessage(String v)           { this.message = v; }
    public double  getTriggerValue()              { return triggerValue; }
    public void    setTriggerValue(double v)      { this.triggerValue = v; }
    public double  getPriceAtAlert()              { return priceAtAlert; }
    public void    setPriceAtAlert(double v)      { this.priceAtAlert = v; }
    public double  getPriceChange()               { return priceChange; }
    public void    setPriceChange(double v)       { this.priceChange = v; }
    public boolean isAcknowledged()               { return acknowledged; }
    public void    setAcknowledged(boolean v)     { this.acknowledged = v; }
    public boolean isEmailSent()                  { return emailSent; }
    public void    setEmailSent(boolean v)        { this.emailSent = v; }
    public double  getRsi()                       { return rsi; }
    public void    setRsi(double v)               { this.rsi = v; }
    public double  getZScore()                    { return zScore; }
    public void    setZScore(double v)            { this.zScore = v; }
    public double  getVolumeRatio()               { return volumeRatio; }
    public void    setVolumeRatio(double v)       { this.volumeRatio = v; }
    public int     getRiskScore()                 { return riskScore; }
    public void    setRiskScore(int v)            { this.riskScore = v; }
    public String  getPumpDumpPhase()             { return pumpDumpPhase; }
    public void    setPumpDumpPhase(String v)     { this.pumpDumpPhase = v; }
    public Instant getTimestamp()                 { return timestamp; }
    public void    setTimestamp(Instant v)        { this.timestamp = v; }
}