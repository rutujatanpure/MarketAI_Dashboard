package com.marketai.dashboard.service;

import com.marketai.dashboard.model.CryptoPriceEvent;
import com.marketai.dashboard.repository.MarketPriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Serves price data to REST controllers.
 * Read strategy: Redis first (fast) → MongoDB fallback (persistent).
 */
@Service
public class MarketPriceService {

    private static final Logger log = LoggerFactory.getLogger(MarketPriceService.class);

    private final MarketPriceRepository repository;
    private final PriceRedisService redisService;

    public MarketPriceService(MarketPriceRepository repository,
                              PriceRedisService redisService) {
        this.repository = repository;
        this.redisService = redisService;
    }

    // ── Latest price (Redis → MongoDB) ────────────────────────────────────────

    public Optional<CryptoPriceEvent> getLatest(String symbol) {
        // Try Redis cache first
        CryptoPriceEvent cached = redisService.getLatestPrice(symbol.toUpperCase());
        if (cached != null) {
            log.debug("✅ Cache hit for {}", symbol);
            return Optional.of(cached);
        }

        // Fall back to MongoDB
        log.debug("⚠️ Cache miss for {} — querying MongoDB", symbol);
        return repository.findTopBySymbolOrderByTimestampDesc(symbol.toUpperCase());
    }

    // ── Historical data for charts ────────────────────────────────────────────

    public List<CryptoPriceEvent> getHistory(String symbol, int hours) {
        Instant from = Instant.now().minus(hours, ChronoUnit.HOURS);
        return repository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(
                symbol.toUpperCase(), from, Instant.now());
    }

    // ── All prices by type ────────────────────────────────────────────────────

    public List<CryptoPriceEvent> getAllCrypto() {
        return repository.findByType("crypto");
    }

    public List<CryptoPriceEvent> getAllStocks() {
        return repository.findByType("stock");
    }
}