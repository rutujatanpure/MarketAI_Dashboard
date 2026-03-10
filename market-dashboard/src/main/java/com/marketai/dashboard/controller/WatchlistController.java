package com.marketai.dashboard.controller;

import com.marketai.dashboard.model.Watchlist;
import com.marketai.dashboard.model.User;
import com.marketai.dashboard.repository.UserRepository;
import com.marketai.dashboard.repository.WatchlistRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistRepository watchlistRepository;
    private final UserRepository      userRepository;

    public WatchlistController(WatchlistRepository watchlistRepository,
                               UserRepository userRepository) {
        this.watchlistRepository = watchlistRepository;
        this.userRepository      = userRepository;
    }

    /**
     * GET /api/watchlist
     * Returns the authenticated user's full watchlist.
     */
    @GetMapping
    public ResponseEntity<Watchlist> getWatchlist(Authentication auth) {
        String email = auth.getName();
        return watchlistRepository.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(createEmpty(email)));
    }

    /**
     * POST /api/watchlist/crypto?symbol=BTCUSDT&name=Bitcoin
     * Adds a crypto symbol to the watchlist.
     */
    @PostMapping("/crypto")
    public ResponseEntity<Watchlist> addCrypto(
            Authentication auth,
            @RequestParam String symbol,
            @RequestParam(defaultValue = "") String name) {
        Watchlist wl = getOrCreate(auth.getName());
        wl.addCrypto(symbol, name.isBlank() ? symbol : name);
        return ResponseEntity.ok(watchlistRepository.save(wl));
    }

    /**
     * DELETE /api/watchlist/crypto?symbol=BTCUSDT
     */
    @DeleteMapping("/crypto")
    public ResponseEntity<Watchlist> removeCrypto(
            Authentication auth,
            @RequestParam String symbol) {
        Watchlist wl = getOrCreate(auth.getName());
        wl.removeCrypto(symbol);
        return ResponseEntity.ok(watchlistRepository.save(wl));
    }

    /**
     * POST /api/watchlist/stock?symbol=AAPL&name=Apple
     */
    @PostMapping("/stock")
    public ResponseEntity<Watchlist> addStock(
            Authentication auth,
            @RequestParam String symbol,
            @RequestParam(defaultValue = "") String name) {
        Watchlist wl = getOrCreate(auth.getName());
        wl.addStock(symbol, name.isBlank() ? symbol : name);
        return ResponseEntity.ok(watchlistRepository.save(wl));
    }

    /**
     * DELETE /api/watchlist/stock?symbol=AAPL
     */
    @DeleteMapping("/stock")
    public ResponseEntity<Watchlist> removeStock(
            Authentication auth,
            @RequestParam String symbol) {
        Watchlist wl = getOrCreate(auth.getName());
        wl.removeStock(symbol);
        return ResponseEntity.ok(watchlistRepository.save(wl));
    }

    /**
     * DELETE /api/watchlist/clear
     * Clears the entire watchlist.
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, String>> clear(Authentication auth) {
        watchlistRepository.findByEmail(auth.getName()).ifPresent(wl -> {
            wl.getCryptoSymbols().clear();
            wl.getStockSymbols().clear();
            watchlistRepository.save(wl);
        });
        return ResponseEntity.ok(Map.of("message", "Watchlist cleared"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Watchlist getOrCreate(String email) {
        return watchlistRepository.findByEmail(email).orElseGet(() -> {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            return new Watchlist(user.getId(), email);
        });
    }

    private Watchlist createEmpty(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return new Watchlist(user.getId(), email);
    }
}