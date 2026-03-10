package com.marketai.dashboard.repository;

import com.marketai.dashboard.model.AlertNotification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends MongoRepository<AlertNotification, String> {

    List<AlertNotification> findTop20ByOrderByTimestampDesc();

    List<AlertNotification> findBySymbolOrderByTimestampDesc(String symbol);

    List<AlertNotification> findByEmailSentFalse();
}