package com.marketai.dashboard.controller;

import com.marketai.dashboard.model.HistoricalPrice;
import com.marketai.dashboard.service.HistoricalDataService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Provides OHLCV candle data for TradingView-style charts.
 *
 * GET /api/history/candles?symbol=BTCUSDT&interval=1h&limit=100
 * GET /api/history/candles/range?symbol=BTCUSDT&interval=1d&from=...&to=...
 * POST /api/history/backfill  (admin only — trigger manual backfill)
 */
@RestController
@RequestMapping("/api/history")
public class HistoricalDataController {

    private final HistoricalDataService histService;

    public HistoricalDataController(HistoricalDataService histService) {
        this.histService = histService;
    }

    /**
     * GET /api/history/candles?symbol=BTCUSDT&interval=1h&limit=100
     * Returns last N closed candles, oldest-first (for chart rendering).
     * Intervals: 1m, 5m, 1h, 4h, 1d
     */
    @GetMapping("/candles")
    public ResponseEntity<List<HistoricalPrice>> getCandles(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1h") String interval,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(
                histService.getCandles(symbol.toUpperCase(), interval,
                        Math.min(limit, 500)));
    }

    /**
     * GET /api/history/candles/range?symbol=BTCUSDT&interval=1d&from=2024-01-01T00:00:00Z&to=2024-02-01T00:00:00Z
     */
    @GetMapping("/candles/range")
    public ResponseEntity<List<HistoricalPrice>> getCandlesInRange(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1h") String interval,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(
                histService.getCandlesInRange(symbol.toUpperCase(), interval, from, to));
    }

    /**
     * POST /api/history/backfill
     * Admin-only: triggers backfill of last 24h of hourly candles.
     * Useful after restart or first deployment.
     */
    @PostMapping("/backfill")
    public ResponseEntity<String> triggerBackfill() {
        histService.backfillLast24Hours();
        return ResponseEntity.ok("Backfill triggered for last 24h of hourly candles");
    }
}