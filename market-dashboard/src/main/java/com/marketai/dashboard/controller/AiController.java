package com.marketai.dashboard.controller;

import com.marketai.dashboard.model.AiAnalysisResult;
import com.marketai.dashboard.repository.AiAnalysisRepository;
import com.marketai.dashboard.service.AiAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiAnalysisService aiService;
    private final AiAnalysisRepository aiRepository;

    public AiController(AiAnalysisService aiService,
                        AiAnalysisRepository aiRepository) {
        this.aiService = aiService;
        this.aiRepository = aiRepository;
    }

    // GET /api/ai/analyze?symbol=BTCUSDT  — triggers fresh AI analysis
    @GetMapping("/analyze")
    public ResponseEntity<AiAnalysisResult> analyze(@RequestParam String symbol) {
        return ResponseEntity.ok(aiService.analyzeSymbol(symbol.toUpperCase()));
    }

    // GET /api/ai/latest?symbol=BTCUSDT  — returns last cached AI result
    @GetMapping("/latest")
    public ResponseEntity<AiAnalysisResult> getLatest(@RequestParam String symbol) {
        return aiRepository.findTopBySymbolOrderByTimestampDesc(symbol.toUpperCase())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(null));
    }
}
