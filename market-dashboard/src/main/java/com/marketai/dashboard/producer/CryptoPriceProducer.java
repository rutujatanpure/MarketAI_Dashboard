package com.marketai.dashboard.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketai.dashboard.model.CryptoPriceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Reusable Kafka producer for CryptoPriceEvent messages.
 *
 * Used by:
 * - CryptoFetchScheduler (REST-polled prices as backup)
 * - BinanceLiveFeedProducer (WebSocket live prices)
 * - Any service needing to publish a price event
 */
@Component
public class CryptoPriceProducer {

    private static final Logger log = LoggerFactory.getLogger(CryptoPriceProducer.class);

    @Value("${kafka.topics.crypto-prices}")
    private String cryptoTopic;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper;

    public CryptoPriceProducer(KafkaTemplate<String, String> kafkaTemplate,
                               ObjectMapper mapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.mapper = mapper;
    }

    /**
     * Sends a CryptoPriceEvent to Kafka.
     * Key = symbol (ensures same symbol always goes to same partition,
     * preserving ordering per symbol).
     *
     * @return CompletableFuture to allow async callback handling
     */
    public CompletableFuture<SendResult<String, String>> send(CryptoPriceEvent event) {
        try {
            String json = mapper.writeValueAsString(event);
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(cryptoTopic, event.getSymbol(), json);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("❌ Failed to send {} to Kafka: {}",
                            event.getSymbol(), ex.getMessage());
                } else {
                    log.debug("✅ Sent {} @ ${} → topic={} partition={} offset={}",
                            event.getSymbol(),
                            String.format("%.4f", event.getPrice()),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });

            return future;

        } catch (JsonProcessingException e) {
            log.error("❌ Serialization error for {}: {}", event.getSymbol(), e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Fire-and-forget variant for high-frequency producers.
     */
    public void sendAsync(CryptoPriceEvent event) {
        send(event);  // result ignored, callback logs errors
    }

    /**
     * Send multiple events in batch.
     */
    public void sendBatch(Iterable<CryptoPriceEvent> events) {
        events.forEach(this::sendAsync);
    }
}