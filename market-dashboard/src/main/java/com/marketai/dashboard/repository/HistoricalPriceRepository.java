package com.marketai.dashboard.repository;

import com.marketai.dashboard.model.HistoricalPrice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface HistoricalPriceRepository extends MongoRepository<HistoricalPrice, String> {

    // All candles for a symbol + interval within time range
    List<HistoricalPrice> findBySymbolAndIntervalAndTimestampBetweenOrderByTimestampAsc(
            String symbol, String interval, Instant from, Instant to);

    // Latest N candles for a symbol
    List<HistoricalPrice> findTop200BySymbolAndIntervalOrderByTimestampDesc(
            String symbol, String interval);

    // Delete old candles (cleanup job)
    void deleteBySymbolAndIntervalAndTimestampBefore(
            String symbol, String interval, Instant cutoff);
    boolean existsBySymbolAndIntervalAndTimestamp(
            String symbol,
            String interval,
            Instant timestamp);
}