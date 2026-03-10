package com.marketai.dashboard.producer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketai.dashboard.model.CryptoPriceEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connects to Binance public WebSocket (no API key needed).
 * Receives live ticker events for 5 crypto symbols and publishes to Kafka.
 */
@Component
public class BinanceLiveFeedProducer {

    private static final Logger log = LoggerFactory.getLogger(BinanceLiveFeedProducer.class);

    private static final String BINANCE_WS_URL =
            "wss://stream.binance.com:9443/stream?streams=" +
                    "btcusdt@ticker/ethusdt@ticker/solusdt@ticker/bnbusdt@ticker/xrpusdt@ticker";

    @Value("${kafka.topics.crypto-prices}")
    private String cryptoTopic;

    @Value("${binance.reconnect-delay-ms:3000}")
    private long reconnectDelayMs;

    @Value("${binance.max-reconnect-attempts:10}")
    private int maxReconnectAttempts;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private volatile WebSocket webSocket;

    public BinanceLiveFeedProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper mapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.mapper = mapper;
    }

    @PostConstruct
    public void start() {
        log.info("🚀 Starting Binance live feed producer...");
        connect();
    }

    private void connect() {
        if (!running.get()) return;

        int attempt = reconnectAttempts.incrementAndGet();
        if (attempt > maxReconnectAttempts) {
            log.error("❌ Max reconnect attempts ({}) reached. Binance feed stopped.", maxReconnectAttempts);
            return;
        }

        log.info("🔌 Connecting to Binance WebSocket (attempt {})...", attempt);

        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(BINANCE_WS_URL), new BinanceWebSocketListener())
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    reconnectAttempts.set(0);
                    log.info("✅ Connected to Binance WebSocket — streaming BTC/ETH/SOL/BNB/XRP");
                })
                .exceptionally(ex -> {
                    log.error("❌ Binance WS connection failed: {}", ex.getMessage(), ex);
                    scheduleReconnect();
                    return null;
                });
    }

    private void scheduleReconnect() {
        if (!running.get()) return;
        long delay = Math.min(reconnectDelayMs * reconnectAttempts.get(), 30_000);
        log.info("⏳ Reconnecting in {}ms...", delay);
        scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        scheduler.shutdownNow();
        if (webSocket != null) {
            try { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Server shutdown"); }
            catch (Exception e) { log.warn("⚠️ Failed to close WebSocket cleanly: {}", e.getMessage()); }
        }
        log.info("🛑 Binance live feed stopped.");
    }

    // ── WebSocket Listener ────────────────────────────────────────────────────
    private class BinanceWebSocketListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                processMessage(message);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket ws, ByteBuffer message) {
            ws.sendPong(message);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.error("❌ Binance WS error: {}", error.getMessage(), error);
            scheduleReconnect();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.warn("⚠️ Binance WS closed: {} — {}", statusCode, reason);
            if (running.get()) scheduleReconnect();
            return null;
        }
    }

    // ── Message Processing ────────────────────────────────────────────────────
    private void processMessage(String rawMessage) {
        try {
            JsonNode root = mapper.readTree(rawMessage);
            JsonNode data = root.has("data") ? root.get("data") : root;

            if (!data.has("e") || !"24hrTicker".equals(data.get("e").asText())) return;

            CryptoPriceEvent event = parseTicker(data);
            String json = mapper.writeValueAsString(event);

            kafkaTemplate.send(cryptoTopic, event.getSymbol(), json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) log.error("❌ Kafka send failed for {}: {}", event.getSymbol(), ex.getMessage(), ex);
                        else log.debug("📊 {} @ ${} → Kafka partition {}", event.getSymbol(), String.format("%.2f", event.getPrice()), result.getRecordMetadata().partition());
                    });

        } catch (Exception e) {
            log.error("❌ Failed to process Binance message: {}", e.getMessage(), e);
        }
    }

    private CryptoPriceEvent parseTicker(JsonNode data) {
        CryptoPriceEvent event = new CryptoPriceEvent();
        event.setSymbol(data.get("s").asText());
        event.setType("crypto");
        event.setPrice(data.get("c").asDouble());
        event.setPriceChange(data.get("P").asDouble());
        event.setVolume(data.get("v").asDouble());
        event.setHigh24h(data.get("h").asDouble());
        event.setLow24h(data.get("l").asDouble());
        event.setOpenPrice(data.get("o").asDouble());
        event.setTradeCount(data.get("n").asLong());

        // 🔹 Use Binance event time (ms → Instant)
        if (data.has("E")) event.setTimestamp(Instant.ofEpochMilli(data.get("E").asLong()));
        else event.setTimestamp(Instant.now());

        return event;
    }
}
