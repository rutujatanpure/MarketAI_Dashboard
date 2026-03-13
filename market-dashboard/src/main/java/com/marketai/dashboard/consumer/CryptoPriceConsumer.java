package com.marketai.dashboard.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketai.dashboard.model.AlertNotification;
import com.marketai.dashboard.model.CryptoPriceEvent;
import com.marketai.dashboard.repository.AlertRepository;
import com.marketai.dashboard.repository.MarketPriceRepository;
import com.marketai.dashboard.service.AiAnalysisService;
import com.marketai.dashboard.service.NotificationService;
import com.marketai.dashboard.service.PriceRedisService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka consumer for real-time crypto price ticks.
 *
 * Pipeline per message:
 *   1. Deserialize JSON → CryptoPriceEvent
 *   2. Save to MongoDB (time-series history)
 *   3. Update Redis cache (latest price for fast reads)
 *   4. Broadcast to WebSocket /topic/prices
 *   5. Detect anomaly (price change >= 9%)
 *   6. Trigger AI analysis asynchronously
 */
@Component
public class CryptoPriceConsumer {

    private static final Logger log = LoggerFactory.getLogger(CryptoPriceConsumer.class);

    // ==============================
    // Anomaly Configuration
    // ==============================
    private static final double ANOMALY_THRESHOLD = 5.0;         // 9% move
    private static final long ANOMALY_COOLDOWN_MS = 90_000;      // 1 minute per symbol
    private static final long EMAIL_COOLDOWN_MS = 900_000;       // 15 minutes per symbol

    // Cooldown trackers (per symbol)
    private final Map<String, Long> lastAnomalyTime = new ConcurrentHashMap<>();
    private final Map<String, Long> lastEmailTime = new ConcurrentHashMap<>();

    // Dependencies
    private final ObjectMapper mapper;
    private final MarketPriceRepository priceRepository;
    private final AiAnalysisService aiAnalysisService;
    private final PriceRedisService redisService;
    private final NotificationService notificationService;
    private final AlertRepository alertRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public CryptoPriceConsumer(ObjectMapper mapper,
                               MarketPriceRepository priceRepository,
                               AiAnalysisService aiAnalysisService,
                               PriceRedisService redisService,
                               NotificationService notificationService,
                               AlertRepository alertRepository,
                               SimpMessagingTemplate messagingTemplate) {
        this.mapper = mapper;
        this.priceRepository = priceRepository;
        this.aiAnalysisService = aiAnalysisService;
        this.redisService = redisService;
        this.notificationService = notificationService;
        this.alertRepository = alertRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(
            topics = "${spring.kafka.topics.crypto-prices}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeCryptoPrice(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            Acknowledgment ack) {

        try {
            CryptoPriceEvent event = mapper.readValue(message, CryptoPriceEvent.class);

            // 1️⃣ Persist to MongoDB
            priceRepository.save(event);

            // 2️⃣ Update Redis cache
            redisService.cacheLatestPrice(event);

            // 3️⃣ Broadcast to WebSocket
            messagingTemplate.convertAndSend("/topic/prices", event);
            messagingTemplate.convertAndSend("/topic/prices/" + event.getSymbol(), event);

            // 4️⃣ Anomaly detection with cooldown
            if (Math.abs(event.getPriceChange()) >= ANOMALY_THRESHOLD) {
                processAnomalyWithCooldown(event);
            }

            // 5️⃣ AI analysis (non-blocking)
            aiAnalysisService.analyzeAsync(event);

            // 6️⃣ Acknowledge Kafka offset
            ack.acknowledge();

        } catch (Exception e) {
            log.error("❌ Error processing crypto price message: {}", e.getMessage(), e);
            // No ack → message retried
        }
    }

    private void processAnomalyWithCooldown(CryptoPriceEvent event) {
        long now = System.currentTimeMillis();
        String symbol = event.getSymbol();

        Long lastTime = lastAnomalyTime.get(symbol);

        if (lastTime == null || now - lastTime > ANOMALY_COOLDOWN_MS) {
            lastAnomalyTime.put(symbol, now);
            handleAnomaly(event);
        } else {
            log.debug("Anomaly suppressed due to cooldown for {}", symbol);
        }
    }

    private void handleAnomaly(CryptoPriceEvent event) {

        String direction = event.getPriceChange() > 0 ? "📈 SPIKE" : "📉 DROP";
        String message = String.format(
                "%s detected! %s moved %.2f%% to $%.2f",
                direction,
                event.getSymbol(),
                event.getPriceChange(),
                event.getPrice()
        );

        log.warn("⚠️ ANOMALY: {}", message);

        // Save alert
        AlertNotification alert = new AlertNotification(
                event.getSymbol(),
                message,
                "ANOMALY",
                event.getPriceChange(),
                event.getPrice()
        );

        alertRepository.save(alert);

        // Broadcast alert
        messagingTemplate.convertAndSend("/topic/alerts", alert);

        // Email cooldown protection
        long now = System.currentTimeMillis();
        String symbol = event.getSymbol();
        Long lastEmail = lastEmailTime.get(symbol);

        if (lastEmail == null || now - lastEmail > EMAIL_COOLDOWN_MS) {
            lastEmailTime.put(symbol, now);
            notificationService.sendAnomalyEmailAsync(alert);
        } else {
            log.debug("Email suppressed due to cooldown for {}", symbol);
        }
    }
}
