package com.marketai.dashboard.repository;

import com.marketai.dashboard.model.CryptoPriceEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CryptoPriceRepository extends MongoRepository<CryptoPriceEvent, String> {
}
