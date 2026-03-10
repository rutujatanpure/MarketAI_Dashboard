package com.marketai.dashboard.service;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MetricsService {

    // ── Counters ──────────────────────────────────────────────────────────────
    private final Counter aiCallsTotal;
    private final Counter aiCacheHitsTotal;
    private final Counter anomaliesDetectedTotal;
    private final Counter pumpDumpAlertsTotal;
    private final Counter volumeSpikesTotal;
    private final Counter kafkaMessagesTotal;
    private final Counter highRiskSymbolsTotal;
    private final Counter circuitBreaksTotal;
    private final Counter rateLimitHitsTotal;
    private final Counter budgetExhaustedTotal;
    private final Counter alertsFiredTotal;

    // ── Gauges ────────────────────────────────────────────────────────────────
    private final AtomicInteger activeSymbolsCount   = new AtomicInteger(0);
    private final AtomicInteger dailyAiCallsUsed     = new AtomicInteger(0);
    private final AtomicInteger unreadAlertsCount    = new AtomicInteger(0);
    private final AtomicInteger highRiskSymbolCount  = new AtomicInteger(0);
    private final AtomicInteger websocketConnections = new AtomicInteger(0);
    private final AtomicInteger kafkaConsumerLag     = new AtomicInteger(0);

    // ── Timers ────────────────────────────────────────────────────────────────
    private final Timer aiCallLatency;
    private final Timer indicatorCalcLatency;
    private final Timer kafkaProcessingLatency;

    public MetricsService(MeterRegistry registry) {

        // ── Counters ──────────────────────────────────────────────────────────
        // FIX: increment(0) ke baad call karo — is se Prometheus mein
        // metric turant 0.0 pe dikhta hai, chahe koi event abhi tak na hua ho.
        // Bina is ke counter tab tak invisible rehta hai jab tak pehli baar
        // increment() call na ho.

        aiCallsTotal = Counter.builder("marketai_ai_calls_total")
                .description("Total Gemini API calls made")
                .register(registry);
        aiCallsTotal.increment(0);

        aiCacheHitsTotal = Counter.builder("marketai_ai_cache_hits_total")
                .description("Total AI cache hits (API calls saved)")
                .register(registry);
        aiCacheHitsTotal.increment(0);

        anomaliesDetectedTotal = Counter.builder("marketai_anomalies_detected_total")
                .description("Total Z-Score anomalies detected (>2.5σ)")
                .register(registry);
        anomaliesDetectedTotal.increment(0);

        pumpDumpAlertsTotal = Counter.builder("marketai_pump_dump_alerts_total")
                .description("Total pump & dump patterns detected")
                .register(registry);
        pumpDumpAlertsTotal.increment(0);

        volumeSpikesTotal = Counter.builder("marketai_volume_spikes_total")
                .description("Total volume spikes detected (>2x avg)")
                .register(registry);
        volumeSpikesTotal.increment(0);

        kafkaMessagesTotal = Counter.builder("marketai_kafka_messages_total")
                .description("Total Kafka price events processed")
                .register(registry);
        kafkaMessagesTotal.increment(0);

        highRiskSymbolsTotal = Counter.builder("marketai_high_risk_symbols_total")
                .description("Total high risk symbol events (score >75)")
                .register(registry);
        highRiskSymbolsTotal.increment(0);

        circuitBreaksTotal = Counter.builder("marketai_circuit_breaks_total")
                .description("Total circuit breaker trips")
                .register(registry);
        circuitBreaksTotal.increment(0);

        rateLimitHitsTotal = Counter.builder("marketai_rate_limit_hits_total")
                .description("Total API rate limit hits")
                .register(registry);
        rateLimitHitsTotal.increment(0);

        budgetExhaustedTotal = Counter.builder("marketai_budget_exhausted_total")
                .description("Total daily budget exhaustion events")
                .register(registry);
        budgetExhaustedTotal.increment(0);

        alertsFiredTotal = Counter.builder("marketai_alerts_fired_total")
                .description("Total alerts fired (all types)")
                .register(registry);
        alertsFiredTotal.increment(0);

        // ── Gauges ────────────────────────────────────────────────────────────
        Gauge.builder("marketai_active_symbols", activeSymbolsCount, AtomicInteger::get)
                .description("Number of symbols being tracked")
                .register(registry);

        Gauge.builder("marketai_daily_ai_calls_used", dailyAiCallsUsed, AtomicInteger::get)
                .description("Daily AI calls used (resets at midnight)")
                .register(registry);

        Gauge.builder("marketai_unread_alerts", unreadAlertsCount, AtomicInteger::get)
                .description("Number of unread/unacknowledged alerts")
                .register(registry);

        Gauge.builder("marketai_high_risk_symbol_count", highRiskSymbolCount, AtomicInteger::get)
                .description("Current high risk symbol count")
                .register(registry);

        Gauge.builder("marketai_websocket_connections", websocketConnections, AtomicInteger::get)
                .description("Active WebSocket connections")
                .register(registry);

        Gauge.builder("marketai_kafka_consumer_lag", kafkaConsumerLag, AtomicInteger::get)
                .description("Kafka consumer lag (messages behind)")
                .register(registry);

        // ── Timers ────────────────────────────────────────────────────────────
        aiCallLatency = Timer.builder("marketai_ai_call_latency")
                .description("Gemini API call latency distribution")
                .publishPercentiles(0.50, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);

        indicatorCalcLatency = Timer.builder("marketai_indicator_calc_latency")
                .description("Technical indicator calculation latency")
                .publishPercentiles(0.50, 0.95, 0.99)
                .register(registry);

        kafkaProcessingLatency = Timer.builder("marketai_kafka_processing_latency")
                .description("End-to-end Kafka event processing latency")
                .publishPercentiles(0.50, 0.95, 0.99)
                .register(registry);
    }

    // ── Counter increment methods ─────────────────────────────────────────────
    public void recordAiCall()            { aiCallsTotal.increment(); }
    public void recordCacheHit()          { aiCacheHitsTotal.increment(); }
    public void recordAnomaly()           { anomaliesDetectedTotal.increment(); }
    public void recordPumpDump()          { pumpDumpAlertsTotal.increment(); }
    public void recordVolumeSpike()       { volumeSpikesTotal.increment(); }
    public void recordKafkaMessage()      { kafkaMessagesTotal.increment(); }
    public void recordHighRisk()          { highRiskSymbolsTotal.increment(); }
    public void recordCircuitBreak()      { circuitBreaksTotal.increment(); }
    public void recordRateLimitHit()      { rateLimitHitsTotal.increment(); }
    public void recordBudgetExhausted()   { budgetExhaustedTotal.increment(); }
    public void recordAlertFired()        { alertsFiredTotal.increment(); }

    // ── Gauge set methods ─────────────────────────────────────────────────────
    public void setActiveSymbols(int n)      { activeSymbolsCount.set(n); }
    public void setDailyAiCalls(int n)       { dailyAiCallsUsed.set(n); }
    public void setUnreadAlerts(int n)       { unreadAlertsCount.set(n); }
    public void setHighRiskSymbols(int n)    { highRiskSymbolCount.set(n); }
    public void setWebsocketConns(int n)     { websocketConnections.set(n); }
    public void setKafkaConsumerLag(int n)   { kafkaConsumerLag.set(n); }

    // ── Timer methods ─────────────────────────────────────────────────────────
    public Timer.Sample startAiTimer()                { return Timer.start(); }
    public void stopAiTimer(Timer.Sample s)           { s.stop(aiCallLatency); }

    public Timer.Sample startIndicatorTimer()         { return Timer.start(); }
    public void stopIndicatorTimer(Timer.Sample s)    { s.stop(indicatorCalcLatency); }

    public Timer.Sample startKafkaTimer()             { return Timer.start(); }
    public void stopKafkaTimer(Timer.Sample s)        { s.stop(kafkaProcessingLatency); }
}