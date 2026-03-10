package com.marketai.dashboard.repository;

import com.marketai.dashboard.model.BacktestResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BacktestRepository extends MongoRepository<BacktestResult, String> {

    List<BacktestResult> findBySymbolOrderByRunAtDesc(String symbol);

    Optional<BacktestResult> findTopBySymbolOrderByF1ScoreDesc(String symbol);

    List<BacktestResult> findByStrategyTypeOrderByRunAtDesc(String strategyType);

    Optional<BacktestResult> findTopBySymbolAndStrategyTypeOrderByRunAtDesc(
            String symbol, String strategyType);
}
