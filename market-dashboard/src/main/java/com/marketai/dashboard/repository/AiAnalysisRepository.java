package com.marketai.dashboard.repository;

import com.marketai.dashboard.model.AiAnalysisResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiAnalysisRepository extends MongoRepository<AiAnalysisResult, String> {

    Optional<AiAnalysisResult> findTopBySymbolOrderByTimestampDesc(String symbol);
}