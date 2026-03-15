package com.marketai.dashboard.producer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketai.dashboard.controller.WebSocketController;
import com.marketai.dashboard.model.CryptoPriceEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * CoinGecko REST API — NO Kafka (saves ~150MB RAM on free tier)
 * Pushes directly to WebSocket.
 */
@Component
public class BinanceLiveFeedProducer {

    private static final Logger log = LoggerFactory.getLogger(BinanceLiveFeedProducer.class);

    private final WebSocketController wsController;
    private final ObjectMapper mapper;
    private HttpClient httpClient;

    private static final Map<String, String> COINGECKO_SYMBOLS = Map.of(
            "bitcoin",     "BTCUSDT",
            "ethereum",    "ETHUSDT",
            "solana",      "SOLUSDT",
            "binancecoin", "BNBUSDT",
            "ripple",      "XRPUSDT"
    );

    public BinanceLiveFeedProducer(WebSocketController wsController, ObjectMapper mapper) {
        this.wsController = wsController;
        this.mapper = mapper;
    }

    @PostConstruct
    public void start() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("📈 CoinGecko Producer started — direct WebSocket push (no Kafka)");
    }

    @Scheduled(fixedDelay = 20_000, initialDelay = 5_000)
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
                log.warn("⚠️ CoinGecko rate limited");
                return;
            }
            if (resp.statusCode() != 200) return;

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

                if (price <= 0) continue;

                CryptoPriceEvent event = new CryptoPriceEvent();
                event.setSymbol(symbol);
                event.setType("crypto");
                event.setPrice(price);
                event.setPriceChange(change24h);
                event.setVolume(vol24h);
                event.setHigh24h(high24h);
                event.setLow24h(low24h);
                event.setOpenPrice(price / (1 + change24h / 100));
                event.setTradeCount(0L);
                event.setTimestamp(Instant.now());

                // ✅ Seedha WebSocket pe push — Kafka bypass
                wsController.broadcastCryptoPrice(event);
                count++;
            }

            log.info("📊 CoinGecko: {} prices → WebSocket ✅", count);

        } catch (Exception e) {
            log.error("❌ CoinGecko fetch failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        log.info("🛑 CoinGecko Producer stopped");
    }
}
