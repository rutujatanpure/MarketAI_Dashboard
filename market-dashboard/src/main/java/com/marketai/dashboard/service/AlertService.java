package com.marketai.dashboard.service;

import com.marketai.dashboard.model.AlertNotification;
import com.marketai.dashboard.model.AiAnalysisResult;
import com.marketai.dashboard.model.CryptoPriceEvent;
import com.marketai.dashboard.model.MarketAlert;
import com.marketai.dashboard.model.TechnicalIndicator;
import com.marketai.dashboard.repository.AlertRepository;
import com.marketai.dashboard.repository.MarketAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    // Old threshold still used for legacy alerts
    private static final double ANOMALY_THRESHOLD = 5.0;

    // Old repo — keep for backward compatibility
    private final AlertRepository        alertRepository;
    // New repo — for rich MarketAlert
    private final MarketAlertRepository  marketAlertRepository;
    private final SimpMessagingTemplate  messagingTemplate;

    public AlertService(AlertRepository alertRepository,
                        MarketAlertRepository marketAlertRepository,
                        SimpMessagingTemplate messagingTemplate) {
        this.alertRepository       = alertRepository;
        this.marketAlertRepository = marketAlertRepository;
        this.messagingTemplate     = messagingTemplate;
    }

    // ════════════════════════════════════════════════════════════════════════
    // EXISTING METHOD — kept for backward compatibility
    // Called by old Kafka consumer with just event + ai result
    // ════════════════════════════════════════════════════════════════════════
    public void checkAndAlert(CryptoPriceEvent event, AiAnalysisResult analysis) {
        double change = Math.abs(event.getPriceChange());

        // Anomaly alert — old style
        if (change >= ANOMALY_THRESHOLD) {
            String msg = String.format("%s price %s by %.2f%% — Signal: %s",
                    event.getSymbol(),
                    event.getPriceChange() > 0 ? "surged" : "dropped",
                    change,
                    analysis.getSignal());
            saveOldAlert(event, msg, "ANOMALY", change);
        }

        // BUY/SELL signal alert — old style
        if ("BUY".equals(analysis.getSignal()) || "SELL".equals(analysis.getSignal())) {
            String msg = String.format("%s AI Signal: %s | Sentiment: %s | Score: %.2f",
                    event.getSymbol(),
                    analysis.getSignal(),
                    analysis.getSentiment(),
                    analysis.getSentimentScore());
            saveOldAlert(event, msg, "SIGNAL", change);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // NEW METHOD — Call this from upgraded Kafka consumer
    // Uses TechnicalIndicator data for rich alerts
    // ════════════════════════════════════════════════════════════════════════
    public void checkAndAlertAdvanced(CryptoPriceEvent event,
                                      AiAnalysisResult analysis,
                                      TechnicalIndicator indicator) {
        // 1. Z-Score Anomaly alert
        if (indicator.isAnomaly()) {
            MarketAlert alert = MarketAlert.anomaly(
                    event.getSymbol(),
                    indicator.getZScore(),
                    event.getPrice(),
                    event.getPriceChange()
            );
            alert.setRsi(indicator.getRsi());
            alert.setVolumeRatio(indicator.getVolumeRatio());
            alert.setRiskScore(indicator.getRiskScore());
            saveNewAlert(alert);
        }

        // 2. RSI Extreme alert
        if (indicator.getRsi() < 28 || indicator.getRsi() > 72) {
            saveNewAlert(MarketAlert.rsiExtreme(
                    event.getSymbol(), indicator.getRsi(), event.getPrice()
            ));
        }

        // 3. Volume Spike alert
        if (indicator.isVolumeSpike() && indicator.getVolumeRatio() > 3.0) {
            saveNewAlert(MarketAlert.volumeSpike(
                    event.getSymbol(), indicator.getVolumeRatio(), event.getPrice()
            ));
        }

        // 4. Pump & Dump alert
        if (indicator.isPumpDumpSuspected()) {
            saveNewAlert(MarketAlert.pumpDump(
                    event.getSymbol(),
                    indicator.getPumpDumpProbability(),
                    event.getPrice(),
                    indicator.getPumpDumpPhase()
            ));
        }

        // 5. High Risk alert
        if (indicator.getRiskScore() > 75) {
            saveNewAlert(MarketAlert.riskHigh(
                    event.getSymbol(), indicator.getRiskScore(), event.getPrice()
            ));
        }

        // 6. AI Signal alert (BUY/SELL only)
        if ("BUY".equals(analysis.getSignal()) || "SELL".equals(analysis.getSignal())) {
            saveNewAlert(MarketAlert.aiSignal(
                    event.getSymbol(),
                    analysis.getSignal(),
                    analysis.getSentiment(),
                    event.getPrice()
            ));

            // Also save to old collection for backward compat
            String msg = String.format("%s AI Signal: %s | Sentiment: %s | Score: %.2f",
                    event.getSymbol(), analysis.getSignal(),
                    analysis.getSentiment(), analysis.getSentimentScore());
            saveOldAlert(event, msg, "SIGNAL", Math.abs(event.getPriceChange()));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Save & broadcast — new MarketAlert
    // ════════════════════════════════════════════════════════════════════════
    private void saveNewAlert(MarketAlert alert) {
        CompletableFuture.runAsync(() -> {
            try {
                marketAlertRepository.save(alert);

                // Broadcast to symbol-specific topic
                messagingTemplate.convertAndSend(
                        "/topic/alerts/" + alert.getSymbol(), alert);

                // Broadcast to global alerts feed
                messagingTemplate.convertAndSend("/topic/alerts/all", alert);

                log.warn("🚨 [{}][{}] {} — {}",
                        alert.getSeverity(), alert.getSymbol(),
                        alert.getAlertType(), alert.getMessage());

            } catch (Exception e) {
                log.error("MarketAlert save failed: {}", e.getMessage());
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // Old save method — kept for backward compatibility
    // ════════════════════════════════════════════════════════════════════════
    private void saveOldAlert(CryptoPriceEvent event,
                              String message, String type, double change) {
        AlertNotification alert = new AlertNotification(
                event.getSymbol(), message, type, change, event.getPrice());

        alertRepository.save(alert);
        messagingTemplate.convertAndSend("/topic/alerts", alert);
        messagingTemplate.convertAndSend("/topic/alerts/" + event.getSymbol(), alert);

        log.info("🚨 Alert [{}] {}: {}", type, event.getSymbol(), message);
    }
}