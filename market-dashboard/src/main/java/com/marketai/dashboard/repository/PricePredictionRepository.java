package com.marketai.dashboard.repository;

import com.marketai.dashboard.model.PricePrediction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PricePredictionRepository extends MongoRepository<PricePrediction, String> {

    Optional<PricePrediction> findTopBySymbolOrderByTimestampDesc(String symbol);

}