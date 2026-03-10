package com.marketai.dashboard.controller;

import com.marketai.dashboard.model.HistoricalPrice;
import com.marketai.dashboard.model.StockPriceEvent;
import com.marketai.dashboard.repository.HistoricalPriceRepository;
import com.marketai.dashboard.repository.StockPriceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockPriceRepository stockRepository;
    private final HistoricalPriceRepository historyRepository;

    public StockController(StockPriceRepository stockRepository,
                           HistoricalPriceRepository historyRepository) {
        this.stockRepository = stockRepository;
        this.historyRepository = historyRepository;
    }

    /**
     * GET /api/stocks/latest?symbol=AAPL
     * Returns the latest price tick for a stock symbol.
     */
    @GetMapping("/latest")
    public ResponseEntity<StockPriceEvent> getLatest(@RequestParam String symbol) {
        return stockRepository.findTopBySymbolOrderByTimestampDesc(symbol.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/stocks/all
     * Returns latest quotes for all tracked stocks.
     * Note: returns most recent tick per symbol.
     */
    @GetMapping("/all")
    public ResponseEntity<List<StockPriceEvent>> getAll() {
        List<String> symbols = List.of(
                "AAPL","GOOGL","MSFT","AMZN","TSLA",
                "NVDA","META","NFLX","AMD","INTC");
        List<StockPriceEvent> latest = symbols.stream()
                .flatMap(s -> stockRepository.findTopBySymbolOrderByTimestampDesc(s).stream())
                .toList();
        return ResponseEntity.ok(latest);
    }

    /**
     * GET /api/stocks/history?symbol=AAPL&hours=48
     * Raw tick history for sparkline charts.
     */
    @GetMapping("/history")
    public ResponseEntity<List<StockPriceEvent>> getHistory(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "48") int hours) {
        Instant from = Instant.now().minus(hours, ChronoUnit.HOURS);
        List<StockPriceEvent> history =
                stockRepository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(
                        symbol.toUpperCase(), from, Instant.now());
        return ResponseEntity.ok(history);
    }

    /**
     * GET /api/stocks/candles?symbol=AAPL&interval=1d&limit=30
     * OHLCV candle data for stock charts.
     */
    @GetMapping("/candles")
    public ResponseEntity<List<HistoricalPrice>> getCandles(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(defaultValue = "30") int limit) {
        List<HistoricalPrice> candles =
                historyRepository.findTop200BySymbolAndIntervalOrderByTimestampDesc(
                        symbol.toUpperCase(), interval);
        int from = Math.max(0, candles.size() - limit);
        return ResponseEntity.ok(candles.subList(from, candles.size()));
    }

    /**
     * GET /api/stocks/symbols
     * Returns list of all tracked stock symbols.
     */
    @GetMapping("/symbols")
    public ResponseEntity<List<String>> getSymbols() {
        return ResponseEntity.ok(List.of(
                "AAPL","GOOGL","MSFT","AMZN","TSLA",
                "NVDA","META","NFLX","AMD","INTC"));
    }
}