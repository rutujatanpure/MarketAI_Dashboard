package com.marketai.dashboard.controller;

import com.marketai.dashboard.model.TechnicalIndicator;
import com.marketai.dashboard.repository.TechnicalIndicatorRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/indicators")
@CrossOrigin(origins = "*")
public class TechnicalIndicatorController {

    private final TechnicalIndicatorRepository repository;

    public TechnicalIndicatorController(TechnicalIndicatorRepository repository) {
        this.repository = repository;
    }

    // GET /api/indicators/latest?symbol=BTCUSDT
    // Frontend mein main card ke neeche dikhega
    @GetMapping("/latest")
    public ResponseEntity<TechnicalIndicator> getLatest(@RequestParam String symbol) {
        return repository.findTopBySymbolOrderByTimestampDesc(symbol.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(null));
    }

    // GET /api/indicators/summary?symbol=BTCUSDT
    // Dashboard widget ke liye compact version
    // ✅ FIX: HashMap<String, Object> use karo — Map.ofEntries mixed types handle nahi karta
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(@RequestParam String symbol) {
        return repository.findTopBySymbolOrderByTimestampDesc(symbol.toUpperCase())
                .map(ind -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("symbol",              ind.getSymbol());
                    map.put("rsi",                 ind.getRsi());
                    map.put("rsiSignal",           ind.getRsiSignal()       != null ? ind.getRsiSignal()       : "NEUTRAL");
                    map.put("macdLine",            ind.getMacdLine());
                    map.put("signalLine",          ind.getSignalLine());
                    map.put("macdHistogram",       ind.getMacdHistogram());
                    map.put("macdSignal",          ind.getMacdSignal()      != null ? ind.getMacdSignal()      : "NEUTRAL");
                    map.put("trend",               ind.getTrend()           != null ? ind.getTrend()           : "SIDEWAYS");
                    map.put("upperBand",           ind.getUpperBand());
                    map.put("middleBand",          ind.getMiddleBand());
                    map.put("lowerBand",           ind.getLowerBand());
                    map.put("bollingerPosition",   ind.getBollingerPosition());
                    map.put("bollingerSignal",     ind.getBollingerSignal() != null ? ind.getBollingerSignal() : "INSIDE");
                    map.put("atr",                 ind.getAtr());
                    map.put("atrPercent",          ind.getAtrPercent());
                    map.put("volumeRatio",         ind.getVolumeRatio());
                    map.put("volumeSpike",         ind.isVolumeSpike());
                    map.put("volumeSignal",        ind.getVolumeSignal()    != null ? ind.getVolumeSignal()    : "NORMAL");
                    map.put("zScore",              ind.getZScore());
                    map.put("rollingMean",         ind.getRollingMean());
                    map.put("rollingStdDev",       ind.getRollingStdDev());
                    map.put("anomaly",             ind.isAnomaly());
                    map.put("anomalySeverity",     ind.getAnomalySeverity() != null ? ind.getAnomalySeverity() : "NORMAL");
                    map.put("riskScore",           ind.getRiskScore());
                    map.put("riskLevel",           ind.getRiskLevel()       != null ? ind.getRiskLevel()       : "LOW");
                    map.put("volatilityScore",     ind.getVolatilityScore());
                    map.put("manipulationScore",   ind.getManipulationScore());
                    map.put("pumpDumpSuspected",   ind.isPumpDumpSuspected());
                    map.put("pumpDumpProbability", ind.getPumpDumpProbability());
                    map.put("pumpDumpPhase",       ind.getPumpDumpPhase()   != null ? ind.getPumpDumpPhase()   : "NONE");
                    map.put("timestamp",           ind.getTimestamp());
                    return ResponseEntity.<Map<String, Object>>ok(map);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/indicators/history?symbol=BTCUSDT
    // Chart ke liye last 10 readings
    @GetMapping("/history")
    public ResponseEntity<List<TechnicalIndicator>> getHistory(@RequestParam String symbol) {
        return ResponseEntity.ok(
                repository.findTop10BySymbolOrderByTimestampDesc(symbol.toUpperCase())
        );
    }

    // GET /api/indicators/anomalies?symbol=BTCUSDT
    // Symbol ke saare anomalies
    @GetMapping("/anomalies")
    public ResponseEntity<List<TechnicalIndicator>> getAnomalies(@RequestParam String symbol) {
        return ResponseEntity.ok(
                repository.findBySymbolAndAnomalyTrueOrderByTimestampDesc(symbol.toUpperCase())
        );
    }

    // GET /api/indicators/anomalies/recent — last 1 hour all symbols
    @GetMapping("/anomalies/recent")
    public ResponseEntity<List<TechnicalIndicator>> getRecentAnomalies() {
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        return ResponseEntity.ok(repository.findRecentAnomalies(since));
    }

    // GET /api/indicators/high-risk?minScore=70
    // High risk symbols right now
    @GetMapping("/high-risk")
    public ResponseEntity<List<TechnicalIndicator>> getHighRisk(
            @RequestParam(defaultValue = "70") int minScore) {
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        return ResponseEntity.ok(repository.findHighRiskSymbols(minScore, since));
    }

    // GET /api/indicators/pump-dump
    // All pump & dump suspected symbols
    @GetMapping("/pump-dump")
    public ResponseEntity<List<TechnicalIndicator>> getPumpDump() {
        return ResponseEntity.ok(
                repository.findByPumpDumpSuspectedTrueOrderByTimestampDesc()
        );
    }

    // GET /api/indicators/volume-spikes — last 30 min
    @GetMapping("/volume-spikes")
    public ResponseEntity<List<TechnicalIndicator>> getVolumeSpikes() {
        Instant since = Instant.now().minus(30, ChronoUnit.MINUTES);
        return ResponseEntity.ok(repository.findRecentVolumeSpikes(since));
    }
}