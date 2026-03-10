package com.marketai.dashboard.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    // Returns empty list — news feature disabled
    @GetMapping
    public ResponseEntity<List<?>> getNews(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(List.of());
    }
}