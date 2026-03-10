package com.marketai.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.marketai.dashboard.model.CryptoPriceEvent;
import com.marketai.dashboard.model.StockPriceEvent;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages Redis cache for:
 *  - Latest price per symbol (TTL: 5 seconds)
 *  - Rolling price history per symbol for charts (last 200 ticks, TTL: 60s)
 */
@Service
public class PriceRedisService {

    private static final Logger log = LoggerFactory.getLogger(PriceRedisService.class);
    private static final String LATEST_KEY_PREFIX = "price:latest:";
    private static final String HISTORY_KEY_PREFIX = "price:history:";
    private static final int HISTORY_MAX_SIZE = 200;

    @Value("${cache.ttl.latest-price:5}")
    private long latestPriceTtlSeconds;

    @Value("${cache.ttl.history:60}")
    private long historyTtlSeconds;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper mapper;

    public PriceRedisService(
            @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate,
            ObjectMapper mapper) {
        this.redisTemplate = redisTemplate;
        this.mapper = mapper;
    }
    @PostConstruct
    public void init() {
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ── Cache latest price ────────────────────────────────────────────────────

    public void cacheLatestPrice(CryptoPriceEvent event) {
        String key = LATEST_KEY_PREFIX + event.getSymbol();
        try {
            String json = mapper.writeValueAsString(event);
            redisTemplate.opsForValue().set(key, json,
                    Duration.ofSeconds(latestPriceTtlSeconds));
        } catch (JsonProcessingException e) {
            log.error("❌ Redis cache write failed for {}: {}", event.getSymbol(), e.getMessage());
        }
    }

    public CryptoPriceEvent getLatestPrice(String symbol) {
        String key = LATEST_KEY_PREFIX + symbol.toUpperCase();
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return mapper.readValue(cached.toString(), CryptoPriceEvent.class);
            }
        } catch (Exception e) {
            log.warn("⚠️ Redis read failed for {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    // ── Rolling price history ─────────────────────────────────────────────────

    public void addToHistory(CryptoPriceEvent event) {
        String key = HISTORY_KEY_PREFIX + event.getSymbol();
        try {
            String json = mapper.writeValueAsString(event);
            // Push to left (newest first)
            redisTemplate.opsForList().leftPush(key, json);
            // Keep only last 200 entries
            redisTemplate.opsForList().trim(key, 0, HISTORY_MAX_SIZE - 1);
            // Refresh TTL
            redisTemplate.expire(key, Duration.ofSeconds(historyTtlSeconds));
        } catch (Exception e) {
            log.error("❌ Redis history write failed: {}", e.getMessage());
        }
    }

    public List<CryptoPriceEvent> getHistory(String symbol) {
        String key = HISTORY_KEY_PREFIX + symbol.toUpperCase();
        List<CryptoPriceEvent> result = new ArrayList<>();
        try {
            List<Object> cached = redisTemplate.opsForList().range(key, 0, -1);
            if (cached != null) {
                for (Object item : cached) {
                    result.add(mapper.readValue(item.toString(), CryptoPriceEvent.class));
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Redis history read failed for {}: {}", symbol, e.getMessage());
        }
        return result;
    }

    public void cacheLatestStockPrice(StockPriceEvent event) {
        String key = "stock:latest:" + event.getSymbol();
        try {
            String json = mapper.writeValueAsString(event);
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(5));
        } catch (JsonProcessingException e) {
            log.warn("❌ Redis stock cache failed for {}: {}", event.getSymbol(), e.getMessage());
        }
    }
}