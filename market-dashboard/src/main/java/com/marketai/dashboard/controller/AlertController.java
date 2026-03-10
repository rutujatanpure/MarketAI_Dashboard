package com.marketai.dashboard.controller;

import com.marketai.dashboard.model.AlertNotification;
import com.marketai.dashboard.model.MarketAlert;
import com.marketai.dashboard.repository.AlertRepository;
import com.marketai.dashboard.repository.MarketAlertRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = "*")
public class AlertController {

    // Both repos — old + new
    private final AlertRepository       alertRepository;
    private final MarketAlertRepository marketAlertRepository;

    public AlertController(AlertRepository alertRepository,
                           MarketAlertRepository marketAlertRepository) {
        this.alertRepository       = alertRepository;
        this.marketAlertRepository = marketAlertRepository;
    }

    // ════════════════════════════════════════════════════════════════════════
    // EXISTING endpoints — backward compatible
    // ════════════════════════════════════════════════════════════════════════

    // GET /api/alerts/recent — last 20 old-style alerts
    @GetMapping("/recent")
    public ResponseEntity<List<AlertNotification>> getRecent() {
        return ResponseEntity.ok(
                alertRepository.findTop20ByOrderByTimestampDesc()
        );
    }

    // GET /api/alerts?symbol=BTCUSDT — old style by symbol
    @GetMapping
    public ResponseEntity<List<AlertNotification>> getBySymbol(
            @RequestParam String symbol) {
        return ResponseEntity.ok(
                alertRepository.findBySymbolOrderByTimestampDesc(symbol.toUpperCase())
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // NEW endpoints — rich MarketAlert
    // ════════════════════════════════════════════════════════════════════════

    // GET /api/alerts/market/recent — last 24h rich alerts
    @GetMapping("/market/recent")
    public ResponseEntity<List<MarketAlert>> getMarketRecent() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        return ResponseEntity.ok(
                marketAlertRepository.findByTimestampAfterOrderByTimestampDesc(since)
        );
    }

    // GET /api/alerts/market/symbol?symbol=BTCUSDT
    @GetMapping("/market/symbol")
    public ResponseEntity<List<MarketAlert>> getMarketBySymbol(
            @RequestParam String symbol) {
        return ResponseEntity.ok(
                marketAlertRepository.findTop20BySymbolOrderByTimestampDesc(symbol.toUpperCase())
        );
    }

    // GET /api/alerts/market/unread — all unacknowledged
    @GetMapping("/market/unread")
    public ResponseEntity<List<MarketAlert>> getUnread() {
        return ResponseEntity.ok(
                marketAlertRepository.findByAcknowledgedFalseOrderByTimestampDesc()
        );
    }

    // GET /api/alerts/market/critical — critical severity last 6h
    @GetMapping("/market/critical")
    public ResponseEntity<List<MarketAlert>> getCritical() {
        Instant since = Instant.now().minus(6, ChronoUnit.HOURS);
        return ResponseEntity.ok(
                marketAlertRepository.findBySeverityAndTimestampAfterOrderByTimestampDesc(
                        "CRITICAL", since)
        );
    }

    // GET /api/alerts/market/pump-dump — all pump & dump alerts
    @GetMapping("/market/pump-dump")
    public ResponseEntity<List<MarketAlert>> getPumpDump() {
        return ResponseEntity.ok(
                marketAlertRepository.findByAlertTypeOrderByTimestampDesc("PUMP_DUMP")
        );
    }

    // GET /api/alerts/market/anomalies — all anomaly alerts
    @GetMapping("/market/anomalies")
    public ResponseEntity<List<MarketAlert>> getAnomalies() {
        return ResponseEntity.ok(
                marketAlertRepository.findByAlertTypeOrderByTimestampDesc("ANOMALY")
        );
    }

    // PUT /api/alerts/market/{id}/acknowledge — mark as read
    @PutMapping("/market/{id}/acknowledge")
    public ResponseEntity<Void> acknowledge(@PathVariable String id) {
        marketAlertRepository.findById(id).ifPresent(alert -> {
            alert.setAcknowledged(true);
            marketAlertRepository.save(alert);
        });
        return ResponseEntity.ok().build();
    }

    // GET /api/alerts/market/stats — dashboard stats
    @GetMapping("/market/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Instant since24h = Instant.now().minus(24, ChronoUnit.HOURS);
        Instant since6h  = Instant.now().minus(6,  ChronoUnit.HOURS);

        List<MarketAlert> all24h = marketAlertRepository
                .findByTimestampAfterOrderByTimestampDesc(since24h);

        long anomalyCount  = all24h.stream()
                .filter(a -> "ANOMALY".equals(a.getAlertType())).count();
        long pumpDumpCount = all24h.stream()
                .filter(a -> "PUMP_DUMP".equals(a.getAlertType())).count();
        long volumeCount   = all24h.stream()
                .filter(a -> "VOLUME_SPIKE".equals(a.getAlertType())).count();
        long criticalCount = all24h.stream()
                .filter(a -> "CRITICAL".equals(a.getSeverity())).count();
        long unreadCount   = marketAlertRepository
                .findByAcknowledgedFalseOrderByTimestampDesc().size();

        return ResponseEntity.ok(Map.of(
                "totalAlerts24h",   all24h.size(),
                "unreadCount",      unreadCount,
                "anomalyCount",     anomalyCount,
                "pumpDumpCount",    pumpDumpCount,
                "volumeSpikeCount", volumeCount,
                "criticalCount",    criticalCount
        ));
    }
}