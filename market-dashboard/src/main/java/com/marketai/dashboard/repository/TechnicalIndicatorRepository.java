// ════════════════════════════════════════════════════════════════════════════
// FILE: TechnicalIndicatorRepository.java
// ════════════════════════════════════════════════════════════════════════════
package com.marketai.dashboard.repository;

import com.marketai.dashboard.model.TechnicalIndicator;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TechnicalIndicatorRepository
        extends MongoRepository<TechnicalIndicator, String> {

    // Latest indicator for a symbol
    Optional<TechnicalIndicator> findTopBySymbolOrderByTimestampDesc(String symbol);

    // Last 10 for chart history
    List<TechnicalIndicator> findTop10BySymbolOrderByTimestampDesc(String symbol);

    // Anomalies only
    List<TechnicalIndicator> findBySymbolAndAnomalyTrueOrderByTimestampDesc(String symbol);

    // All anomalies across all symbols — last 24h
    @Query("{ 'anomaly': true, 'timestamp': { $gte: ?0 } }")
    List<TechnicalIndicator> findRecentAnomalies(Instant since);

    // High risk symbols
    @Query("{ 'riskScore': { $gte: ?0 }, 'timestamp': { $gte: ?1 } }")
    List<TechnicalIndicator> findHighRiskSymbols(int minRiskScore, Instant since);

    // Pump & dump suspected
    List<TechnicalIndicator> findByPumpDumpSuspectedTrueOrderByTimestampDesc();

    // Volume spikes
    @Query("{ 'volumeSpike': true, 'timestamp': { $gte: ?0 } }")
    List<TechnicalIndicator> findRecentVolumeSpikes(Instant since);

    // Cleanup old data
    void deleteByTimestampBefore(Instant cutoff);
}