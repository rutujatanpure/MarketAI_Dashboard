package com.marketai.dashboard.repository;

import com.marketai.dashboard.model.RiskProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RiskProfileRepository extends MongoRepository<RiskProfile, String> {

    Optional<RiskProfile> findTopBySymbolOrderByTimestampDesc(String symbol);

    List<RiskProfile> findBySymbolOrderByTimestampDesc(String symbol);

    @Query("{ 'compositeRiskScore': { $gte: ?0 }, 'timestamp': { $gte: ?1 } }")
    List<RiskProfile> findHighRiskSymbols(int minScore, Instant since);

    @Query("{ 'marketRegime': ?0, 'timestamp': { $gte: ?1 } }")
    List<RiskProfile> findByMarketRegime(String regime, Instant since);

    void deleteByTimestampBefore(Instant cutoff);
}