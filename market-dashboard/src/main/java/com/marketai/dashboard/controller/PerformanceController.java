package com.marketai.dashboard.controller;

import com.marketai.dashboard.service.AiAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    private final AiAnalysisService aiService;

    public PerformanceController(AiAnalysisService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(aiService.getPerformanceStats());
    }
}