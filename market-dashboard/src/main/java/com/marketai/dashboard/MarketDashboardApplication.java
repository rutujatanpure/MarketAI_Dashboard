package com.marketai.dashboard;

import com.marketai.dashboard.service.AiAnalysisService;
import com.marketai.dashboard.service.AlertService;
import com.marketai.dashboard.service.TechnicalIndicatorService;
import com.marketai.dashboard.service.PriceRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(KafkaProperties.class)
public class MarketDashboardApplication {

	private static final Logger log = LoggerFactory.getLogger(MarketDashboardApplication.class);

	public static void main(String[] args) {
		ApplicationContext ctx = SpringApplication.run(MarketDashboardApplication.class, args);

		log.info("╔════════════════════════════════════════╗");
		log.info("║   🚀 Market Dashboard AI — Started     ║");
		log.info("╚════════════════════════════════════════╝");

		// ── Services check ────────────────────────────────
		checkService(ctx, "AI Analysis Service",          AiAnalysisService.class);
		checkService(ctx, "Alert Service",                AlertService.class);
		checkService(ctx, "Technical Indicator Service",  TechnicalIndicatorService.class);
		checkService(ctx, "Price Redis Service",          PriceRedisService.class);

		// ── Infrastructure check ──────────────────────────
		checkService(ctx, "Kafka Template",               KafkaTemplate.class);
		checkService(ctx, "MongoDB Template",             MongoTemplate.class);
		checkService(ctx, "Redis Template",               RedisTemplate.class);

		log.info("════════════════════════════════════════════");
		log.info("🌐 App        → http://localhost:8080");
		log.info("📊 Stats      → http://localhost:8080/api/performance/stats");
		log.info("🤖 AI Analyze → http://localhost:8080/api/ai/analyze?symbol=BTCUSDT");
		log.info("🚨 Alerts     → http://localhost:8080/api/alerts/recent");
		log.info("════════════════════════════════════════════");
	}

	private static void checkService(ApplicationContext ctx,
									 String name, Class<?> serviceClass) {
		try {
			ctx.getBean(serviceClass);
			log.info("  ✅ {} — Running", name);
		} catch (Exception e) {
			log.error("  ❌ {} — FAILED! Reason: {}", name, e.getMessage());
		}
	}
}