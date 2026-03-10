// ════════════════════════════════════════════════════════════════════════════
// FILE: MarketAlertRepository.java
// ════════════════════════════════════════════════════════════════════════════
package com.marketai.dashboard.repository;

import com.marketai.dashboard.model.MarketAlert;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MarketAlertRepository
        extends MongoRepository<MarketAlert, String> {

    // Latest 20 alerts for a symbol
    List<MarketAlert> findTop20BySymbolOrderByTimestampDesc(String symbol);

    // All unacknowledged
    List<MarketAlert> findByAcknowledgedFalseOrderByTimestampDesc();

    // By alert type (ANOMALY, PUMP_DUMP, etc.)
    List<MarketAlert> findByAlertTypeOrderByTimestampDesc(String alertType);

    // By severity
    List<MarketAlert> findBySeverityOrderByTimestampDesc(String severity);

    // Critical alerts in time window
    List<MarketAlert> findBySeverityAndTimestampAfterOrderByTimestampDesc(
            String severity, Instant since);

    // All alerts after a time
    List<MarketAlert> findByTimestampAfterOrderByTimestampDesc(Instant since);

    // Symbol + type combo
    List<MarketAlert> findBySymbolAndAlertTypeOrderByTimestampDesc(
            String symbol, String alertType);

    // Count by type in window
    long countByAlertTypeAndTimestampAfter(String alertType, Instant since);
}