package com.marketai.dashboard.repository;

import com.marketai.dashboard.model.Watchlist;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WatchlistRepository extends MongoRepository<Watchlist, String> {

    Optional<Watchlist> findByUserId(String userId);

    Optional<Watchlist> findByEmail(String email);

    boolean existsByUserId(String userId);

    boolean existsByEmail(String email);

    void deleteByUserId(String userId);
}