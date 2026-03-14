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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * CoinGecko REST API — replaces Binance WebSocket
 * Binance returns HTTP 451 (legal block) on Render India region.
 * CoinGecko free tier: no auth needed, works everywhere.
 */
@Component
public class BinanceLiveFeedProducer {

    private static final Logger log = LoggerFactory.getLogger(BinanceLiveFeedProducer.class);

    @Value("${kafka.topics.crypto-prices:crypto-prices-topic}")
    private String cryptoTopic;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper;
    private HttpClient httpClient;

    // CoinGecko ID → Binance-style symbol (same as before)
    private static final Map<String, String> COINGECKO_SYMBOLS = Map.of(
            "bitcoin",     "BTCUSDT",
            "ethereum",    "ETHUSDT",
            "solana",      "SOLUSDT",
            "binancecoin", "BNBUSDT",
            "ripple",      "XRPUSDT"
    );

    public BinanceLiveFeedProducer(KafkaTemplate<String, String> kafkaTemplate,
                                   ObjectMapper mapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.mapper = mapper;
    }

    @PostConstruct
    public void start() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("════════════════════════════════════════════════");
        log.info("📈 CoinGecko Producer started (Binance blocked in India)");
        log.info("   Symbols : BTC, ETH, SOL, BNB, XRP");
        log.info("   Interval: every 15s");
        log.info("   Topic   : {}", cryptoTopic);
        log.info("════════════════════════════════════════════════");
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 3_000)
    public void fetchCryptoPrices() {
        try {
            String ids = String.join(",", COINGECKO_SYMBOLS.keySet());
            String url = "https://api.coingecko.com/api/v3/simple/price"
                    + "?ids=" + ids
                    + "&vs_currencies=usd"
                    + "&include_24hr_change=true"
                    + "&include_24hr_vol=true"
                    + "&include_high_24h=true"
                    + "&include_low_24h=true";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("User-Agent", "MarketAI/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 429) {
                log.warn("⚠️ CoinGecko rate limited — skipping this cycle");
                return;
            }
            if (resp.statusCode() != 200) {
                log.warn("⚠️ CoinGecko status: {}", resp.statusCode());
                return;
            }

            JsonNode root = mapper.readTree(resp.body());
            int count = 0;

            for (Map.Entry<String, String> entry : COINGECKO_SYMBOLS.entrySet()) {
                String geckoId = entry.getKey();
                String symbol  = entry.getValue();
                JsonNode data  = root.path(geckoId);
                if (data.isMissingNode()) continue;

                double price     = data.path("usd").asDouble();
                double change24h = data.path("usd_24h_change").asDouble();
                double vol24h    = data.path("usd_24h_vol").asDouble();
                double high24h   = data.path("usd_24h_high").asDouble(price);
                double low24h    = data.path("usd_24h_low").asDouble(price);
                double openPrice = price / (1 + change24h / 100);

                if (price <= 0) continue;

                // ✅ CryptoPriceEvent — same model as before
                CryptoPriceEvent event = new CryptoPriceEvent();
                event.setSymbol(symbol);
                event.setType("crypto");
                event.setPrice(price);
                event.setPriceChange(change24h);
                event.setVolume(vol24h);
                event.setHigh24h(high24h);
                event.setLow24h(low24h);
                event.setOpenPrice(openPrice);
                event.setTradeCount(0L);
                event.setTimestamp(Instant.now());

                String json = mapper.writeValueAsString(event);
                kafkaTemplate.send(cryptoTopic, symbol, json)
                        .whenComplete((result, ex) -> {
                            if (ex != null) log.error("❌ Kafka send failed for {}: {}", symbol, ex.getMessage());
                            else log.debug("📊 {} @ ${} → Kafka", symbol, String.format("%.2f", price));
                        });
                count++;
            }

            log.info("📊 CoinGecko: {} crypto prices fetched ✅", count);

        } catch (Exception e) {
            log.error("❌ CoinGecko fetch failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        log.info("🛑 CoinGecko Producer stopped");
    }
}
