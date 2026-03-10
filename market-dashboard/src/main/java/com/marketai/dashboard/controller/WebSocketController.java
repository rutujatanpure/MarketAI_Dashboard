package com.marketai.dashboard.controller;

import com.marketai.dashboard.model.CryptoPriceEvent;
import com.marketai.dashboard.model.StockPriceEvent;
import com.marketai.dashboard.repository.MarketPriceRepository;
import com.marketai.dashboard.repository.StockPriceRepository;
import com.marketai.dashboard.service.AiAnalysisService;
import com.marketai.dashboard.service.PricePredictionService;
import com.marketai.dashboard.service.PriceRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
public class WebSocketController {

    private static final Logger log =
            LoggerFactory.getLogger(WebSocketController.class);

    private final SimpMessagingTemplate  ws;
    private final PriceRedisService      redisService;
    private final MarketPriceRepository  cryptoRepository;
    private final StockPriceRepository   stockRepository;
    private final AiAnalysisService      aiService;
    private final PricePredictionService predictionService;

    public WebSocketController(SimpMessagingTemplate ws,
                               PriceRedisService redisService,
                               MarketPriceRepository cryptoRepository,
                               StockPriceRepository stockRepository,
                               AiAnalysisService aiService,
                               PricePredictionService predictionService) {
        this.ws                = ws;
        this.redisService      = redisService;
        this.cryptoRepository  = cryptoRepository;
        this.stockRepository   = stockRepository;
        this.aiService         = aiService;
        this.predictionService = predictionService;
    }

    // ── Client subscribes to all prices ──────────────────────────────────────
    @SubscribeMapping("/prices")
    public void onSubscribePrices(Principal principal) {
        log.debug("🔌 Client subscribed to /topic/prices: {}",
                principal != null ? principal.getName() : "anonymous");
    }

    // ── Client subscribes to all stocks ──────────────────────────────────────
    @SubscribeMapping("/stocks")
    public void onSubscribeStocks(Principal principal) {
        log.debug("📈 Client subscribed to /topic/stocks: {}",
                principal != null ? principal.getName() : "anonymous");
    }

    // ── Client sends: SEND /app/subscribe → get latest crypto price ───────────
    // Body: {"symbol": "BTCUSDT"}
    @MessageMapping("/subscribe")
    public void subscribeSymbol(Map<String, String> payload,
                                Principal principal) {
        String symbol = payload.getOrDefault("symbol", "").toUpperCase();
        if (symbol.isBlank()) return;

        log.debug("📌 {} subscribed to {}",
                principal != null ? principal.getName() : "anon", symbol);

        // ✅ Redis se instant snapshot do
        CryptoPriceEvent cached = redisService.getLatestPrice(symbol);
        if (cached != null) {
            ws.convertAndSend("/topic/prices/" + symbol, cached);
        } else {
            cryptoRepository.findTopBySymbolOrderByTimestampDesc(symbol)
                    .ifPresent(e -> ws.convertAndSend("/topic/prices/" + symbol, e));
        }
    }

    // ── Client sends: SEND /app/subscribe/stock → get latest stock price ──────
    // Body: {"symbol": "AAPL"}
    @MessageMapping("/subscribe/stock")
    public void subscribeStock(Map<String, String> payload,
                               Principal principal) {
        String symbol = payload.getOrDefault("symbol", "").toUpperCase();
        if (symbol.isBlank()) return;

        log.debug("📈 {} subscribed to stock {}",
                principal != null ? principal.getName() : "anon", symbol);

        // ✅ Latest stock price MongoDB se do
        stockRepository.findTopBySymbolOrderByTimestampDesc(symbol)
                .ifPresent(e -> ws.convertAndSend("/topic/stocks/" + symbol, e));
    }

    // ── Client sends: SEND /app/analyze → AI analysis request ────────────────
    // Body: {"symbol": "BTCUSDT"}
    @MessageMapping("/analyze")
    public void requestAnalysis(Map<String, String> payload) {
        String symbol = payload.getOrDefault("symbol", "").toUpperCase();
        if (symbol.isBlank()) return;

        log.info("🤖 AI analysis requested via WS for {}", symbol);

        // Crypto analysis
        cryptoRepository.findTopBySymbolOrderByTimestampDesc(symbol)
                .ifPresent(aiService::analyzeAsync);
    }

    // ── Client sends: SEND /app/predict → prediction request ─────────────────
    // Body: {"symbol": "BTCUSDT"} or {"symbol": "AAPL"}
    @MessageMapping("/predict")
    public void requestPrediction(Map<String, String> payload) {
        String symbol = payload.getOrDefault("symbol", "").toUpperCase();
        if (symbol.isBlank()) return;

        log.info("🔮 Prediction requested via WS for {}", symbol);

        // ✅ Async prediction — result push karo
        new Thread(() -> {
            try {
                var prediction = predictionService.getPrediction(symbol);
                ws.convertAndSend("/topic/prediction/" + symbol, prediction);
                log.info("✅ Prediction pushed to /topic/prediction/{}", symbol);
            } catch (Exception e) {
                log.error("❌ WS prediction failed for {}: {}", symbol, e.getMessage());
            }
        }).start();
    }

    // ── Server push helpers — Kafka consumers call these ─────────────────────

    // ✅ Crypto price broadcast
    public void broadcastCryptoPrice(CryptoPriceEvent event) {
        ws.convertAndSend("/topic/prices", event);
        ws.convertAndSend("/topic/prices/" + event.getSymbol(), event);
    }

    // ✅ Stock price broadcast — Finnhub WebSocket se aayega
    public void broadcastStockPrice(StockPriceEvent event) {
        ws.convertAndSend("/topic/stocks", event);
        ws.convertAndSend("/topic/stocks/" + event.getSymbol(), event);
    }
}