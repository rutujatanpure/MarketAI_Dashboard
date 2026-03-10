package com.marketai.dashboard.controller;

import com.marketai.dashboard.model.CryptoPriceEvent;
import com.marketai.dashboard.model.HistoricalPrice;
import com.marketai.dashboard.repository.HistoricalPriceRepository;
import com.marketai.dashboard.repository.MarketPriceRepository;
import com.marketai.dashboard.service.PriceRedisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/crypto")
public class CryptoController {

    private final MarketPriceRepository priceRepository;
    private final HistoricalPriceRepository historyRepository;
    private final PriceRedisService redisService;

    public CryptoController(MarketPriceRepository priceRepository,
                            HistoricalPriceRepository historyRepository,
                            PriceRedisService redisService) {
        this.priceRepository = priceRepository;
        this.historyRepository = historyRepository;
        this.redisService = redisService;
    }

    /**
     * GET /api/crypto/latest?symbol=BTCUSDT
     * Returns latest tick for a symbol. Redis → MongoDB fallback.
     */
    @GetMapping("/latest")
    public ResponseEntity<CryptoPriceEvent> getLatest(@RequestParam String symbol) {
        // Try Redis cache first (fastest)
        CryptoPriceEvent cached = redisService.getLatestPrice(symbol.toUpperCase());
        if (cached != null) return ResponseEntity.ok(cached);

        // Fallback: MongoDB
        return priceRepository.findTopBySymbolOrderByTimestampDesc(symbol.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/crypto/all
     * Returns latest ticks for all tracked crypto symbols.
     */
    @GetMapping("/all")
    public ResponseEntity<List<CryptoPriceEvent>> getAll() {
        return ResponseEntity.ok(priceRepository.findByType("crypto"));
    }

    /**
     * GET /api/crypto/history?symbol=BTCUSDT&hours=24
     * Raw price tick history from MongoDB.
     */
    @GetMapping("/history")
    public ResponseEntity<List<CryptoPriceEvent>> getHistory(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "24") int hours) {
        Instant from = Instant.now().minus(hours, ChronoUnit.HOURS);
        List<CryptoPriceEvent> history =
                priceRepository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(
                        symbol.toUpperCase(), from, Instant.now());
        return ResponseEntity.ok(history);
    }

    /**
     * GET /api/crypto/candles?symbol=BTCUSDT&interval=1h&limit=100
     * OHLCV candle data for TradingView-style charts.
     */
    @GetMapping("/candles")
    public ResponseEntity<List<HistoricalPrice>> getCandles(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1h") String interval,
            @RequestParam(defaultValue = "100") int limit) {
        List<HistoricalPrice> candles =
                historyRepository.findTop200BySymbolAndIntervalOrderByTimestampDesc(
                        symbol.toUpperCase(), interval);
        // Return up to 'limit' candles, oldest first
        int from = Math.max(0, candles.size() - limit);
        return ResponseEntity.ok(candles.subList(from, candles.size()));
    }

    /**
     * GET /api/crypto/symbols
     * Returns list of all tracked crypto symbols.
     */
    @GetMapping("/symbols")
    public ResponseEntity<List<String>> getSymbols() {
        return ResponseEntity.ok(List.of(
                "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT",
                "ADAUSDT", "DOGEUSDT", "AVAXUSDT", "DOTUSDT", "MATICUSDT"
        ));
    }
}
