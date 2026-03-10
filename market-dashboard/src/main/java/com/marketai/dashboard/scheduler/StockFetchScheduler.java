//package com.marketai.dashboard.scheduler;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.marketai.dashboard.model.StockPriceEvent;
//import com.marketai.dashboard.repository.StockPriceRepository;
//import com.marketai.dashboard.service.PriceRedisService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.messaging.simp.SimpMessagingTemplate;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.time.Duration;
//import java.time.Instant;
//import java.util.List;
//import java.util.concurrent.atomic.AtomicInteger;
//
///**
// * Polls Alpha Vantage for real-time (15-min delayed) stock quotes.
// *
// * Free tier limits: 5 API calls/minute, 500/day.
// * Strategy: rotate through symbols, one per 12 seconds → stays within 5/min.
// *
// * On startup, fetches all symbols once immediately.
// * After that, rotates every 12 seconds.
// */
//@ConditionalOnProperty(
//        name = "stock.rest.enabled",
//        havingValue = "true",
//        matchIfMissing = false
//)
//@Component
//public class StockFetchScheduler {
//
//    private static final Logger log = LoggerFactory.getLogger(StockFetchScheduler.class);
//
//    private static final List<String> SYMBOLS = List.of(
//            "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA",
//            "NVDA", "META", "NFLX", "AMD", "INTC"
//    );
//
//    @Value("${alphavantage.api-key}")
//    private String apiKey;
//
//    @Value("${alphavantage.base-url}")
//    private String baseUrl;
//
//    @Value("${kafka.topics.stock-prices}")
//    private String stockTopic;
//
//    private final KafkaTemplate<String, String>    kafkaTemplate;
//    private final StockPriceRepository             stockRepository;
//    private final PriceRedisService                redisService;
//    private final SimpMessagingTemplate            ws;
//    private final ObjectMapper                     mapper;
//    private final HttpClient                       httpClient;
//    private final AtomicInteger                    symbolIndex = new AtomicInteger(0);
//
//    public StockFetchScheduler(KafkaTemplate<String, String> kafkaTemplate,
//                               StockPriceRepository stockRepository,
//                               PriceRedisService redisService,
//                               SimpMessagingTemplate ws,
//                               ObjectMapper mapper) {
//        this.kafkaTemplate  = kafkaTemplate;
//        this.stockRepository= stockRepository;
//        this.redisService   = redisService;
//        this.ws             = ws;
//        this.mapper         = mapper;
//        this.httpClient     = HttpClient.newBuilder()
//                .connectTimeout(Duration.ofSeconds(10))
//                .build();
//    }
//
//    /**
//     * Rotates through symbols every 12 seconds.
//     * 5 symbols × 12s = 60s = 5 calls/minute (free tier safe).
//     * 10 symbols → 120s cycle (2 min to refresh all).
//     */
//    @Scheduled(fixedDelay = 12_000, initialDelay = 3_000)
//    public void fetchNext() {
//        if (apiKey == null || apiKey.isBlank() || "demo".equals(apiKey)) {
//            log.warn("⚠️ Alpha Vantage API key not set — using demo data");
//            publishDemoStock();
//            return;
//        }
//
//        int idx = symbolIndex.getAndUpdate(i -> (i + 1) % SYMBOLS.size());
//        String symbol = SYMBOLS.get(idx);
//        fetchAndPublish(symbol);
//    }
//
//    private void fetchAndPublish(String symbol) {
//        try {
//            String url = String.format(
//                    "%s?function=GLOBAL_QUOTE&symbol=%s&apikey=%s",
//                    baseUrl, symbol, apiKey);
//
//            HttpRequest request = HttpRequest.newBuilder()
//                    .uri(URI.create(url))
//                    .GET()
//                    .timeout(Duration.ofSeconds(10))
//                    .build();
//
//            HttpResponse<String> response =
//                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//
//            JsonNode root  = mapper.readTree(response.body());
//            JsonNode quote = root.path("Global Quote");
//
//            // Alpha Vantage sends {} when rate limited or key invalid
//            if (quote.isEmpty() || !quote.has("05. price")) {
//                log.warn("⚠️ No data for {} — rate limited or invalid key", symbol);
//                return;
//            }
//
//            StockPriceEvent event = parseQuote(symbol, quote);
//            persist(event);
//
//        } catch (Exception e) {
//            log.error("❌ Stock fetch failed for {}: {}", symbol, e.getMessage());
//        }
//    }
//
//    private StockPriceEvent parseQuote(String symbol, JsonNode quote) {
//        StockPriceEvent event = new StockPriceEvent();
//        event.setSymbol(symbol);
//        event.setPrice(safeDouble(quote, "05. price"));
//        event.setOpen(safeDouble(quote, "02. open"));
//        event.setHigh(safeDouble(quote, "03. high"));
//        event.setLow(safeDouble(quote, "04. low"));
//        event.setPreviousClose(safeDouble(quote, "08. previous close"));
//        event.setChange(safeDouble(quote, "09. change"));
//        event.setVolume(safeLong(quote, "06. volume"));
//
//        // Parse "10. change percent" → strip "%" suffix
//        String rawPct = quote.path("10. change percent").asText("0%");
//        try {
//            event.setChangePercent(Double.parseDouble(rawPct.replace("%", "").trim()));
//        } catch (NumberFormatException ignored) {
//            event.setChangePercent(0.0);
//        }
//
//        event.setTimestamp(Instant.now());
//        return event;
//    }
//
//    private void persist(StockPriceEvent event) throws Exception {
//        // 1. Save to MongoDB
//        stockRepository.save(event);
//
//        // 2. Publish to Kafka → consumers will Redis-cache + WebSocket broadcast
//        String json = mapper.writeValueAsString(event);
//        kafkaTemplate.send(stockTopic, event.getSymbol(), json);
//
//        log.info("📈 Stock {} @ ${} ({}%)",
//                event.getSymbol(),
//                String.format("%.2f", event.getPrice()),
//                String.format("%+.2f", event.getChangePercent()));
//    }
//
//    // ── Demo fallback when no API key ─────────────────────────────────────────
//
//    private void publishDemoStock() {
//        // Publishes random-walk demo prices so the UI isn't empty during dev
//        String[] symbols = {"AAPL", "MSFT", "GOOGL"};
//        double[] basePrices = {178.0, 415.0, 176.0};
//
//        for (int i = 0; i < symbols.length; i++) {
//            StockPriceEvent event = new StockPriceEvent();
//            event.setSymbol(symbols[i]);
//            double noise = (Math.random() - 0.5) * 2.0;  // ±1%
//            event.setPrice(basePrices[i] * (1 + noise / 100));
//            event.setChangePercent(noise);
//            event.setTimestamp(Instant.now());
//            try {
//                String json = mapper.writeValueAsString(event);
//                kafkaTemplate.send(stockTopic, event.getSymbol(), json);
//            } catch (Exception e) {
//                log.error("❌ Demo stock publish failed: {}", e.getMessage());
//            }
//        }
//    }
//
//    // ── Helpers ───────────────────────────────────────────────────────────────
//
//    private double safeDouble(JsonNode node, String field) {
//        try { return Double.parseDouble(node.path(field).asText("0")); }
//        catch (NumberFormatException e) { return 0.0; }
//    }
//
//    private long safeLong(JsonNode node, String field) {
//        try { return Long.parseLong(node.path(field).asText("0")); }
//        catch (NumberFormatException e) { return 0L; }
//    }
//}