package com.marketai.dashboard.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class PerformanceConfig {

    private static final Logger log = LoggerFactory.getLogger(PerformanceConfig.class);

    // ✅ 1. BEST PERFORMANCE — Thread Pool (Scalability)
    // Kafka async tasks ke liye optimized thread pool
    @Bean(name = "performanceKafkaExecutor")
    public Executor kafkaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core threads — hamesha ready rahenge
        executor.setCorePoolSize(5);

        // Max threads — load badhe toh zyada threads
        executor.setMaxPoolSize(20);

        // Queue — thread busy ho toh yahan wait karo
        executor.setQueueCapacity(500);

        // Thread name — logs mein clearly dikhega
        executor.setThreadNamePrefix("market-async-");

        // Graceful shutdown — kaam poora karo phir band ho
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        log.info("⚡ Thread Pool initialized:");
        log.info("   Core Threads : 5");
        log.info("   Max Threads  : 20");
        log.info("   Queue Size   : 500");
        return executor;
    }

    // ✅ 2. LOAD BALANCING — WebSocket executor
    // WebSocket broadcast ke liye alag thread pool
    @Bean(name = "websocketExecutor")
    public Executor websocketExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ws-async-");
        executor.initialize();
        log.info("🔌 WebSocket Thread Pool initialized:");
        log.info("   Core Threads : 3");
        log.info("   Max Threads  : 10");
        return executor;
    }

    @PostConstruct
    public void logPerformanceSettings() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        long totalMemory = Runtime.getRuntime().totalMemory() / (1024 * 1024);

        log.info("🖥️  System Performance Info:");
        log.info("   CPU Cores    : {}", cpuCores);
        log.info("   Max Memory   : {} MB", maxMemory);
        log.info("   Used Memory  : {} MB", totalMemory);
        log.info("   ─────────────────────────────────");
        log.info("   ⚡ Scalability  : Multi-thread pool ✅");
        log.info("   🔄 Load Balance : Separate executors ✅");
        log.info("   🛡️  Circuit Break: In AiAnalysisService ✅");
        log.info("   📦 Caching      : Redis + In-memory ✅");
        log.info("   ⏱️  Latency      : Async processing ✅");
        log.info("   🎯 Accuracy     : Groq LLaMA 3.1 ✅");
    }
}