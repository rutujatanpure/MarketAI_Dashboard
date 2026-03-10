package com.marketai.dashboard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "price_predictions")
public class PricePrediction {

    @Id
    private String id;

    private String symbol;
    private String type;
    private double currentPrice;
    private int historyDaysUsed;
    private String modelUsed;
    private Instant timestamp;

    private double predictionToday;
    private double predictionTomorrow;
    private double predictionWeek;
    private double predictionMonth;

    private double changePercentToday;
    private double changePercentTomorrow;
    private double changePercentWeek;
    private double changePercentMonth;

    private String directionToday;
    private String directionTomorrow;
    private String directionWeek;
    private String directionMonth;

    private double confidenceToday;
    private double confidenceTomorrow;
    private double confidenceWeek;
    private double confidenceMonth;

    private String riskLevel;
    private String recommendation;
    private String summary;

    private double rsi14;

    private double macdLine;
    private double macdSignal;
    private double macdHistogram;

    private double bbUpper;
    private double bbMiddle;
    private double bbLower;

    private double supportLevel;
    private double resistanceLevel;

    private double volatility;
    private double trendScore;

    private double changePercent7d;
    private double changePercent30d;

    // -------------------------
    // Getters and Setters
    // -------------------------

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public int getHistoryDaysUsed() { return historyDaysUsed; }
    public void setHistoryDaysUsed(int historyDaysUsed) { this.historyDaysUsed = historyDaysUsed; }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public double getPredictionToday() { return predictionToday; }
    public void setPredictionToday(double predictionToday) { this.predictionToday = predictionToday; }

    public double getPredictionTomorrow() { return predictionTomorrow; }
    public void setPredictionTomorrow(double predictionTomorrow) { this.predictionTomorrow = predictionTomorrow; }

    public double getPredictionWeek() { return predictionWeek; }
    public void setPredictionWeek(double predictionWeek) { this.predictionWeek = predictionWeek; }

    public double getPredictionMonth() { return predictionMonth; }
    public void setPredictionMonth(double predictionMonth) { this.predictionMonth = predictionMonth; }

    public double getChangePercentToday() { return changePercentToday; }
    public void setChangePercentToday(double changePercentToday) { this.changePercentToday = changePercentToday; }

    public double getChangePercentTomorrow() { return changePercentTomorrow; }
    public void setChangePercentTomorrow(double changePercentTomorrow) { this.changePercentTomorrow = changePercentTomorrow; }

    public double getChangePercentWeek() { return changePercentWeek; }
    public void setChangePercentWeek(double changePercentWeek) { this.changePercentWeek = changePercentWeek; }

    public double getChangePercentMonth() { return changePercentMonth; }
    public void setChangePercentMonth(double changePercentMonth) { this.changePercentMonth = changePercentMonth; }

    public String getDirectionToday() { return directionToday; }
    public void setDirectionToday(String directionToday) { this.directionToday = directionToday; }

    public String getDirectionTomorrow() { return directionTomorrow; }
    public void setDirectionTomorrow(String directionTomorrow) { this.directionTomorrow = directionTomorrow; }

    public String getDirectionWeek() { return directionWeek; }
    public void setDirectionWeek(String directionWeek) { this.directionWeek = directionWeek; }

    public String getDirectionMonth() { return directionMonth; }
    public void setDirectionMonth(String directionMonth) { this.directionMonth = directionMonth; }

    public double getConfidenceToday() { return confidenceToday; }
    public void setConfidenceToday(double confidenceToday) { this.confidenceToday = confidenceToday; }

    public double getConfidenceTomorrow() { return confidenceTomorrow; }
    public void setConfidenceTomorrow(double confidenceTomorrow) { this.confidenceTomorrow = confidenceTomorrow; }

    public double getConfidenceWeek() { return confidenceWeek; }
    public void setConfidenceWeek(double confidenceWeek) { this.confidenceWeek = confidenceWeek; }

    public double getConfidenceMonth() { return confidenceMonth; }
    public void setConfidenceMonth(double confidenceMonth) { this.confidenceMonth = confidenceMonth; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public double getRsi14() { return rsi14; }
    public void setRsi14(double rsi14) { this.rsi14 = rsi14; }

    public double getMacdLine() { return macdLine; }
    public void setMacdLine(double macdLine) { this.macdLine = macdLine; }

    public double getMacdSignal() { return macdSignal; }
    public void setMacdSignal(double macdSignal) { this.macdSignal = macdSignal; }

    public double getMacdHistogram() { return macdHistogram; }
    public void setMacdHistogram(double macdHistogram) { this.macdHistogram = macdHistogram; }

    public double getBbUpper() { return bbUpper; }
    public void setBbUpper(double bbUpper) { this.bbUpper = bbUpper; }

    public double getBbMiddle() { return bbMiddle; }
    public void setBbMiddle(double bbMiddle) { this.bbMiddle = bbMiddle; }

    public double getBbLower() { return bbLower; }
    public void setBbLower(double bbLower) { this.bbLower = bbLower; }

    public double getSupportLevel() { return supportLevel; }
    public void setSupportLevel(double supportLevel) { this.supportLevel = supportLevel; }

    public double getResistanceLevel() { return resistanceLevel; }
    public void setResistanceLevel(double resistanceLevel) { this.resistanceLevel = resistanceLevel; }

    public double getVolatility() { return volatility; }
    public void setVolatility(double volatility) { this.volatility = volatility; }

    public double getTrendScore() { return trendScore; }
    public void setTrendScore(double trendScore) { this.trendScore = trendScore; }

    public double getChangePercent7d() { return changePercent7d; }
    public void setChangePercent7d(double changePercent7d) { this.changePercent7d = changePercent7d; }

    public double getChangePercent30d() { return changePercent30d; }
    public void setChangePercent30d(double changePercent30d) { this.changePercent30d = changePercent30d; }
}