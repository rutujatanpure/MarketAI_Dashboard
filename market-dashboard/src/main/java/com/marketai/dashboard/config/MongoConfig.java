package com.marketai.dashboard.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.util.concurrent.TimeUnit;

@Configuration
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    private final MongoTemplate mongoTemplate;

    public MongoConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void createTtlIndexes() {

        log.info("🗂️ Creating MongoDB TTL indexes (2 days)...");

        try {

            mongoTemplate.indexOps("market_prices")
                    .createIndex(new Index("timestamp", Sort.Direction.ASC)
                            .expire(2, TimeUnit.DAYS)
                            .named("ttl_2d"));
            log.info("  ✅ market_prices        → 2 days");

            mongoTemplate.indexOps("technical_indicators")
                    .createIndex(new Index("timestamp", Sort.Direction.ASC)
                            .expire(2, TimeUnit.DAYS)
                            .named("ttl_2d"));
            log.info("  ✅ technical_indicators → 2 days");

            mongoTemplate.indexOps("ai_analysis_results")
                    .createIndex(new Index("timestamp", Sort.Direction.ASC)
                            .expire(2, TimeUnit.DAYS)
                            .named("ttl_2d"));
            log.info("  ✅ ai_analysis_results  → 2 days");

            mongoTemplate.indexOps("backtest_results")
                    .createIndex(new Index("runAt", Sort.Direction.ASC)
                            .expire(2, TimeUnit.DAYS)
                            .named("ttl_2d"));
            log.info("  ✅ backtest_results     → 2 days");

            mongoTemplate.indexOps("risk_profiles")
                    .createIndex(new Index("timestamp", Sort.Direction.ASC)
                            .expire(2, TimeUnit.DAYS)
                            .named("ttl_2d"));
            log.info("  ✅ risk_profiles        → 2 days");

            mongoTemplate.indexOps("alert_notifications")
                    .createIndex(new Index("timestamp", Sort.Direction.ASC)
                            .expire(2, TimeUnit.DAYS)
                            .named("ttl_2d"));
            log.info("  ✅ alert_notifications  → 2 days");

            log.info("✅ All collections — 2 days TTL active. Auto-cleanup ON.");

        } catch (Exception e) {
            log.warn("⚠️ TTL index creation failed (non-fatal): {}", e.getMessage());
        }
    }
}