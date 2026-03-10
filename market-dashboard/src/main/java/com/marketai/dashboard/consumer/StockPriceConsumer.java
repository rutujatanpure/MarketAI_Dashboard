package com.marketai.dashboard.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketai.dashboard.model.AlertNotification;
import com.marketai.dashboard.model.StockPriceEvent;          // ✅ StockPriceEvent
import com.marketai.dashboard.repository.AlertRepository;
import com.marketai.dashboard.repository.StockPriceRepository; // ✅ StockPriceRepository
import com.marketai.dashboard.service.AiAnalysisService;
import com.marketai.dashboard.service.NotificationService;
import com.marketai.dashboard.service.PriceRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StockPriceConsumer {

    private static final Logger log = LoggerFactory.getLogger(StockPriceConsumer.class);
    private static final double ANOMALY_THRESHOLD = 3.0; // 3% = anomaly
    private final Map<String, Long> anomalyCooldown = new ConcurrentHashMap<>();
    private static final long ANOMALY_COOLDOWN_MS = 300_000; // 5 min

    private final ObjectMapper mapper;
    private final StockPriceRepository stockRepository;       // ✅ Stock repository
    private final AiAnalysisService aiAnalysisService;
    private final PriceRedisService redisService;
    private final NotificationService notificationService;
    private final AlertRepository alertRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public StockPriceConsumer(ObjectMapper mapper,
                              StockPriceRepository stockRepository,
                              AiAnalysisService aiAnalysisService,
                              PriceRedisService redisService,
                              NotificationService notificationService,
                              AlertRepository alertRepository,
                              SimpMessagingTemplate messagingTemplate) {
        this.mapper = mapper;
        this.stockRepository = stockRepository;
        this.aiAnalysisService = aiAnalysisService;
        this.redisService = redisService;
        this.notificationService = notificationService;
        this.alertRepository = alertRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(
            topics = "${kafka.topics.stock-prices}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeStockPrice(String message, Acknowledgment ack) {
        try {
            // ✅ StockPriceEvent use karo — CryptoPriceEvent nahi
            StockPriceEvent event = mapper.readValue(message, StockPriceEvent.class);

            // 1. Save to MongoDB
            stockRepository.save(event);

            // 2. Redis cache — stock:latest:RELIANCE
            redisService.cacheLatestStockPrice(event);

            // 3. WebSocket broadcast
            messagingTemplate.convertAndSend("/topic/stocks", event);
            messagingTemplate.convertAndSend("/topic/stocks/" + event.getSymbol(), event);

            // 4. Anomaly check (3% threshold)
            // 4. Anomaly check with cooldown
            if (Math.abs(event.getChangePercent()) >= ANOMALY_THRESHOLD) {

                String symbol = event.getSymbol();
                long now = System.currentTimeMillis();
                long lastAlertTime = anomalyCooldown.getOrDefault(symbol, 0L);

                if (now - lastAlertTime > ANOMALY_COOLDOWN_MS) {

                    String direction = event.getChangePercent() > 0 ? "📈 SPIKE" : "📉 DROP";
                    String msg = String.format("%s detected! %s moved %.2f%% to ₹%.2f",
                            direction,
                            symbol,
                            event.getChangePercent(),
                            event.getPrice());

                    log.warn("⚠️ ANOMALY: {}", msg);

                    AlertNotification alert = new AlertNotification(
                            symbol, msg, "ANOMALY",
                            event.getChangePercent(), event.getPrice());

                    alertRepository.save(alert);
                    messagingTemplate.convertAndSend("/topic/alerts", alert);
                    messagingTemplate.convertAndSend("/topic/alerts/" + symbol, alert);
                    notificationService.sendAnomalyEmailAsync(alert);

                    // update cooldown time
                    anomalyCooldown.put(symbol, now);
                }
            }
            ack.acknowledge();

            log.debug("✅ Stock consumed: {} | ₹{} | {}%",
                    event.getSymbol(),
                    String.format("%.2f", event.getPrice()),
                    String.format("%.2f", event.getChangePercent()));

        } catch (Exception e) {
            log.error("❌ Error processing stock message: {}", e.getMessage(), e);
        }
    }
}