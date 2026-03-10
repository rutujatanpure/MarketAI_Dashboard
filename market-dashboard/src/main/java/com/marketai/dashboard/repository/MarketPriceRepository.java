package com.marketai.dashboard.repository;

import com.marketai.dashboard.model.CryptoPriceEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketPriceRepository extends MongoRepository<CryptoPriceEvent, String> {

    // Latest tick for a symbol
    Optional<CryptoPriceEvent> findTopBySymbolOrderByTimestampDesc(String symbol);

    // All latest prices grouped (last tick per symbol)
    List<CryptoPriceEvent> findByType(String type);

    // Historical data for charts
    List<CryptoPriceEvent> findBySymbolAndTimestampBetweenOrderByTimestampAsc(
            String symbol, Instant from, Instant to);

    // Last N ticks for AI analysis
    List<CryptoPriceEvent> findTop100BySymbolOrderByTimestampDesc(String symbol);

    // Check if symbol has data
    boolean existsBySymbol(String symbol);

    List<CryptoPriceEvent> findBySymbolAndTimestampAfterOrderByTimestampAsc(String symbol, Instant from);
}