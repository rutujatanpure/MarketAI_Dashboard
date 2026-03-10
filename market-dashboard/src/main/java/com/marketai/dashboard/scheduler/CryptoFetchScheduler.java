package com.marketai.dashboard.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketai.dashboard.model.CryptoPriceEvent;
import com.marketai.dashboard.producer.CryptoPriceProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

/**
 * Fallback REST-based crypto price fetcher.
 *
 * Binance public REST API (no API key required):
 * https://api.binance.com/api/v3/ticker/24hr?symbol=BTCUSDT
 *
 * Runs every 10 seconds as a safety net when the WebSocket feed
 * from BinanceLiveFeedProducer is disconnected or throttled.
 *
 * In normal operation (WS connected), these events are near-duplicates
 * — the consumer deduplicates by checking Redis before saving to MongoDB.
 */
@Component
public class CryptoFetchScheduler {

    private static final Logger log = LoggerFactory.getLogger(CryptoFetchScheduler.class);

    private static final String BINANCE_TICKER_URL =
            "https://api.binance.com/api/v3/ticker/24hr?symbol=";

    private static final List<String> SYMBOLS = List.of(
            "BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT",
            "ADAUSDT", "DOGEUSDT", "AVAXUSDT", "DOTUSDT", "MATICUSDT",
            "LINKUSDT", "LTCUSDT", "BCHUSDT", "ATOMUSDT", "TRXUSDT",
            "ETCUSDT", "FILUSDT", "ICPUSDT", "APTUSDT", "ARBUSDT",
            "OPUSDT", "SUIUSDT", "INJUSDT", "NEARUSDT", "HBARUSDT",
            "ALGOUSDT", "VETUSDT", "EGLDUSDT", "AAVEUSDT", "THETAUSDT",
            "XTZUSDT", "SANDUSDT", "MANAUSDT", "AXSUSDT", "GALAUSDT",
            "FLOWUSDT", "KAVAUSDT", "RUNEUSDT", "CAKEUSDT", "FTMUSDT",
            "CHZUSDT", "DYDXUSDT", "SNXUSDT", "LDOUSDT", "PEPEUSDT",
            "SHIBUSDT", "BONKUSDT", "BLURUSDT", "SEIUSDT", "TIAUSDT"
    );

    private final CryptoPriceProducer producer;
    private final ObjectMapper mapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CryptoFetchScheduler(CryptoPriceProducer producer, ObjectMapper mapper) {
        this.producer = producer;
        this.mapper   = mapper;
    }

    /**
     * Fetches all tracked symbols every 10 seconds.
     * Uses Binance batch ticker: /api/v3/ticker/24hr (no key needed, rate: 40 weight).
     */
    @Scheduled(fixedDelay = 10_000, initialDelay = 5_000)
    public void fetchAll() {
        try {
            // Batch request — fetch all symbols in one call using JSON array param
            String symbolsJson = buildSymbolsParam();
            String url = "https://api.binance.com/api/v3/ticker/24hr?symbols=" + symbolsJson;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("⚠️ Binance REST returned {}", response.statusCode());
                return;
            }

            JsonNode array = mapper.readTree(response.body());
            if (!array.isArray()) return;

            for (JsonNode node : array) {
                CryptoPriceEvent event = parseTicker(node);
                producer.sendAsync(event);
            }

            log.debug("📊 Fetched {} crypto prices via REST fallback", array.size());

        } catch (Exception e) {
            log.error("❌ CryptoFetchScheduler error: {}", e.getMessage());
        }
    }

    private String buildSymbolsParam() {
        // e.g. ["BTCUSDT","ETHUSDT",...]  — URL encoded
        StringBuilder sb = new StringBuilder("%5B");  // [
        for (int i = 0; i < SYMBOLS.size(); i++) {
            sb.append("%22").append(SYMBOLS.get(i)).append("%22");  // "SYMBOL"
            if (i < SYMBOLS.size() - 1) sb.append("%2C");           // ,
        }
        sb.append("%5D");  // ]
        return sb.toString();
    }

    private CryptoPriceEvent parseTicker(JsonNode node) {
        CryptoPriceEvent event = new CryptoPriceEvent();
        event.setSymbol(node.path("symbol").asText());
        event.setType("crypto");
        event.setPrice(node.path("lastPrice").asDouble());
        event.setPriceChange(node.path("priceChangePercent").asDouble());
        event.setVolume(node.path("volume").asDouble());
        event.setHigh24h(node.path("highPrice").asDouble());
        event.setLow24h(node.path("lowPrice").asDouble());
        event.setOpenPrice(node.path("openPrice").asDouble());
        event.setTradeCount(node.path("count").asLong());
        event.setTimestamp(Instant.now());
        return event;
    }
}