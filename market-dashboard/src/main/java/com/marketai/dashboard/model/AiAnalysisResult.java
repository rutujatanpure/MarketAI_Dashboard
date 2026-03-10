package com.marketai.dashboard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "ai_analysis")
@CompoundIndexes({
        @CompoundIndex(name = "symbol_timestamp_idx",
                def = "{'symbol': 1, 'timestamp': -1}"),
        @CompoundIndex(name = "symbol_signal_idx",
                def = "{'symbol': 1, 'signal': 1}"),
        @CompoundIndex(name = "anomaly_timestamp_idx",
                def = "{'anomaly': 1, 'timestamp': -1}")
})
public class AiAnalysisResult {

    @Id
    private String id;

    @Version
    private Long version;

    @Indexed
    private String symbol;

    private String sentiment;
    private double sentimentScore;
    private String signal;
    private String summary;
    private boolean anomaly;
    private double anomalyScore;

    private double priceAtAnalysis;
    private double priceChange24h;
    private double confidenceScore;
    private String modelUsed;
    private long   responseTimeMs;
    private String source;

    // ✅ Fix: expireAfterSeconds deprecated — naya @Indexed use karo
    // MongoDB TTL index — 24 hours baad auto delete
    @Indexed
    @Field("expires_at")
    private Instant expiresAt;

    @Indexed
    private Instant timestamp = Instant.now();

    // ── Constructors ─────────────────────────────────────────────────────────

    public AiAnalysisResult() {}

    public AiAnalysisResult(String symbol, String sentiment, double sentimentScore,
                            String signal, String summary) {
        this.symbol         = symbol;
        this.sentiment      = sentiment;
        this.sentimentScore = sentimentScore;
        this.signal         = signal;
        this.summary        = summary;
        this.timestamp      = Instant.now();
        this.expiresAt      = Instant.now().plusSeconds(86400); // 24 hours
        this.source         = "Gemini";
    }

    // ── Helper Methods ────────────────────────────────────────────────────────

    public void calculateConfidence() {
        this.confidenceScore = Math.min(Math.abs(sentimentScore) * 1.2, 1.0);
    }

    public boolean isFresh(long maxAgeSeconds) {
        return timestamp != null &&
                Instant.now().minusSeconds(maxAgeSeconds).isBefore(timestamp);
    }

    public String toLogString() {
        return String.format(
                "[%s] %s | %s | Score: %.2f | Confidence: %.0f%% | Source: %s | %dms",
                symbol, sentiment, signal,
                sentimentScore,
                confidenceScore * 100,
                source != null ? source : "UNKNOWN",
                responseTimeMs);
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }

    public double getSentimentScore() { return sentimentScore; }
    public void setSentimentScore(double sentimentScore) { this.sentimentScore = sentimentScore; }

    public String getSignal() { return signal; }
    public void setSignal(String signal) { this.signal = signal; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public boolean isAnomaly() { return anomaly; }
    public void setAnomaly(boolean anomaly) { this.anomaly = anomaly; }

    public double getAnomalyScore() { return anomalyScore; }
    public void setAnomalyScore(double anomalyScore) { this.anomalyScore = anomalyScore; }

    public double getPriceAtAnalysis() { return priceAtAnalysis; }
    public void setPriceAtAnalysis(double v) { this.priceAtAnalysis = v; }

    public double getPriceChange24h() { return priceChange24h; }
    public void setPriceChange24h(double v) { this.priceChange24h = v; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double v) { this.confidenceScore = v; }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

    public long getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(long v) { this.responseTimeMs = v; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    private int riskScore;             // 0-100 overall risk (from TechnicalIndicator)
    private int volatilityScore;       // 0-100 volatility
    private int manipulationProbability; // 0-100 pump/dump probability

    // ── MultiTimeframe Confluence fields ───────────────────────────────────
    private String confluenceSignal;         // BUY / SELL / HOLD / CONFLICTED
    private int    confluenceCount;          // 0-4 timeframes agreeing
    private double confluenceMultiplier;     // 0.5 / 1.0 / 1.5 / 2.0


    // ── NEW Getters/Setters — paste karo existing getters ke baad ────────────
    public int  getRiskScore()                  { return riskScore; }
    public void setRiskScore(int v)             { this.riskScore = v; }
    public int  getVolatilityScore()            { return volatilityScore; }
    public void setVolatilityScore(int v)       { this.volatilityScore = v; }
    public int  getManipulationProbability()    { return manipulationProbability; }
    public void setManipulationProbability(int v){ this.manipulationProbability = v; }

    public String getConfluenceSignal() { return confluenceSignal; }
    public void setConfluenceSignal(String v) { this.confluenceSignal = v; }

    public int getConfluenceCount() { return confluenceCount; }
    public void setConfluenceCount(int v) { this.confluenceCount = v; }

    public double getConfluenceMultiplier() { return confluenceMultiplier; }
    public void setConfluenceMultiplier(double v) { this.confluenceMultiplier = v; }


}