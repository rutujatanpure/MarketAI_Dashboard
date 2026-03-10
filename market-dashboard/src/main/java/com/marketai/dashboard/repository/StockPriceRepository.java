package com.marketai.dashboard.repository;

import com.marketai.dashboard.model.StockPriceEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockPriceRepository extends MongoRepository<StockPriceEvent, String> {

    // Latest tick for a symbol
    Optional<StockPriceEvent> findTopBySymbolOrderByTimestampDesc(String symbol);

    // Historical ticks for charts
    List<StockPriceEvent> findBySymbolAndTimestampBetweenOrderByTimestampAsc(
            String symbol, Instant from, Instant to);

    // Last 100 ticks for AI analysis
    List<StockPriceEvent> findTop100BySymbolOrderByTimestampDesc(String symbol);

    // All distinct tracked stocks (latest per symbol)
    boolean existsBySymbol(String symbol);
}