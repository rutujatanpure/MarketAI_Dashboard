package com.marketai.dashboard.controller;

import com.marketai.dashboard.model.CryptoPriceEvent;
import com.marketai.dashboard.service.CurrencyConversionService;
import com.marketai.dashboard.service.MarketPriceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketPriceService priceService;
    private final CurrencyConversionService currencyService;

    public MarketController(MarketPriceService priceService,
                            CurrencyConversionService currencyService) {
        this.priceService = priceService;
        this.currencyService = currencyService;
    }

    // GET /api/market/latest?symbol=BTCUSDT&currency=INR
    @GetMapping("/latest")
    public ResponseEntity<CryptoPriceEvent> getLatest(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "USD") String currency) {
        return priceService.getLatest(symbol)
                .map(event -> {
                    // Convert price in-place
                    event.setPrice(currencyService.convert(event.getPrice(), currency));
                    event.setHigh24h(currencyService.convert(event.getHigh24h(), currency));
                    event.setLow24h(currencyService.convert(event.getLow24h(), currency));
                    return ResponseEntity.ok(event);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/market/history?symbol=BTCUSDT&hours=24
    @GetMapping("/history")
    public ResponseEntity<List<CryptoPriceEvent>> getHistory(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(priceService.getHistory(symbol, hours));
    }

    // GET /api/market/crypto  — all latest crypto prices
    @GetMapping("/crypto")
    public ResponseEntity<List<CryptoPriceEvent>> getAllCrypto() {
        return ResponseEntity.ok(priceService.getAllCrypto());
    }

    // GET /api/market/stocks  — all latest stock prices
    @GetMapping("/stocks")
    public ResponseEntity<List<CryptoPriceEvent>> getAllStocks() {
        return ResponseEntity.ok(priceService.getAllStocks());
    }

    // GET /api/market/currencies  — available currency rates
    @GetMapping("/currencies")
    public ResponseEntity<?> getCurrencies() {
        return ResponseEntity.ok(currencyService.getAllRates());
    }
}