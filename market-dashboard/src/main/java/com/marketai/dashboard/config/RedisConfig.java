package com.marketai.dashboard.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    private final RedisConnectionFactory connectionFactory;

    public RedisConfig(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    // ✅ Safe startup connection check (connection properly closed)
    @PostConstruct
    public void checkRedisConnection() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.ping();
            log.info("📦 Redis Config:");
            log.info("   Status     : ✅ Connected");
            log.info("   Serializer : GenericJackson2Json");
            log.info("   TTL Policy : Per key (set in PriceRedisService)");
        } catch (Exception e) {
            log.warn("📦 Redis Config:");
            log.warn("   Status : ❌ NOT Connected!");
            log.warn("   Reason : {}", e.getMessage());
            log.warn("   ⚠️ App will run without Redis cache — MongoDB fallback active");
        }
    }

    // ✅ Mark as Primary to avoid bean conflict
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate() {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key serializer
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value serializer
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();

        log.info("📦 RedisTemplate bean created ✅");

        return template;
    }
}