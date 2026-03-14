package com.marketai.dashboard.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * CryptoFetchScheduler — DISABLED for Render free tier
 *
 * Reasons:
 * 1. Binance REST returns HTTP 451 (legal block) on India region servers
 * 2. CryptoPriceProducer uses Kafka → OutOfMemoryError on 512MB RAM
 *
 * Crypto prices are now handled by BinanceLiveFeedProducer
 * via CoinGecko REST API → direct WebSocket push (no Kafka)
 */
@Component
public class CryptoFetchScheduler {

    private static final Logger log = LoggerFactory.getLogger(CryptoFetchScheduler.class);

    // All scheduled methods removed — BinanceLiveFeedProducer handles crypto prices
    // This class kept to avoid breaking Spring context (other beans may reference it)

    public CryptoFetchScheduler() {
        log.info("⏸️ CryptoFetchScheduler disabled — using CoinGecko via BinanceLiveFeedProducer");
    }
}
