package com.marketai.dashboard.controller;

import com.marketai.dashboard.model.User;
import com.marketai.dashboard.repository.UserRepository;
import com.marketai.dashboard.service.WatchlistService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;
    private final WatchlistService watchlistService;

    public UserController(UserRepository userRepository,
                          WatchlistService watchlistService) {
        this.userRepository = userRepository;
        this.watchlistService = watchlistService;
    }

    // GET /api/user/profile
    @GetMapping("/profile")
    public ResponseEntity<User> getProfile(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/user/watchlist
    @GetMapping("/watchlist")
    public ResponseEntity<List<String>> getWatchlist(Authentication auth) {
        return ResponseEntity.ok(watchlistService.getWatchlist(auth.getName()));
    }

    // POST /api/user/watchlist?symbol=BTCUSDT
    @PostMapping("/watchlist")
    public ResponseEntity<List<String>> addToWatchlist(
            Authentication auth,
            @RequestParam String symbol) {
        return ResponseEntity.ok(watchlistService.addSymbol(auth.getName(), symbol));
    }

    // DELETE /api/user/watchlist?symbol=BTCUSDT
    @DeleteMapping("/watchlist")
    public ResponseEntity<List<String>> removeFromWatchlist(
            Authentication auth,
            @RequestParam String symbol) {
        return ResponseEntity.ok(watchlistService.removeSymbol(auth.getName(), symbol));
    }

    // PUT /api/user/currency
    @PutMapping("/currency")
    public ResponseEntity<User> updateCurrency(
            Authentication auth,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                watchlistService.updateCurrency(auth.getName(), body.get("currency")));
    }

    // PUT /api/user/notifications
    @PutMapping("/notifications")
    public ResponseEntity<User> updateNotifications(
            Authentication auth,
            @RequestBody Map<String, Boolean> body) {
        return ResponseEntity.ok(
                watchlistService.updateNotificationPreference(
                        auth.getName(), body.get("enabled")));
    }
}