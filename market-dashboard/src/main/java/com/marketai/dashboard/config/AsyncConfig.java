package com.marketai.dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * CPU bound + IO mixed workload ke liye optimized
     * Used by: AiAnalysisService (@Async("kafkaExecutor"))
     */
    @Bean(name = "kafkaExecutor")
    public Executor kafkaExecutor() {

        int cores = Runtime.getRuntime().availableProcessors();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Production tuning formula:
        // Core = CPU cores * 2
        // Max  = CPU cores * 4
        executor.setCorePoolSize(cores * 2);
        executor.setMaxPoolSize(cores * 4);

        // Large enough queue but not infinite
        executor.setQueueCapacity(5000);

        executor.setThreadNamePrefix("KafkaAsync-");

        // Prevent crashes under load
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // Graceful shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }

    /**
     * General async tasks
     * Used by: general @Async calls across the application
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {

        int cores = Runtime.getRuntime().availableProcessors();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(cores);
        executor.setMaxPoolSize(cores * 2);
        executor.setQueueCapacity(2000);

        executor.setThreadNamePrefix("MarketAsync-");

        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }

    /**
     * Backtest thread pool — CPU-intensive, isolated from Kafka pipeline
     * Used by: BacktestingEngine (@Async("backtestExecutor"))
     *
     * Why separate pool?
     * Backtesting iterates over thousands of historical data points.
     * If it shared kafkaExecutor, it would starve real-time price processing.
     * Separate pool = backtests run in background, never block live trading.
     *
     * Sizing: 2 core / 4 max is intentionally conservative.
     * We never want CPU-heavy backtests competing with Kafka consumers.
     */
    @Bean(name = "backtestExecutor")
    public Executor backtestExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Intentionally small — backtests are background jobs
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);

        executor.setThreadNamePrefix("Backtest-");

        // AbortPolicy: if backtest queue is full, reject new request
        // (admin gets an error, which is fine — don't block Kafka pipeline)
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120); // backtests may take longer

        executor.initialize();
        return executor;
    }
}