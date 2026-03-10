package com.marketai.dashboard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated watchlist document per user.
 * Separate from the User document for cleaner updates and scalability.
 *
 * Collection: watchlists
 * One document per userId.
 */
@Document(collection = "watchlists")
public class Watchlist {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    @Indexed(unique = true)
    private String email;

    private List<WatchlistEntry> cryptoSymbols = new ArrayList<>();
    private List<WatchlistEntry> stockSymbols  = new ArrayList<>();

    private Instant updatedAt = Instant.now();

    // ── Constructors ──────────────────────────────────────────────────────────

    public Watchlist() {}

    public Watchlist(String userId, String email) {
        this.userId    = userId;
        this.email     = email;
        this.updatedAt = Instant.now();
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    public void addCrypto(String symbol, String displayName) {
        boolean exists = cryptoSymbols.stream()
                .anyMatch(e -> e.getSymbol().equalsIgnoreCase(symbol));
        if (!exists) {
            cryptoSymbols.add(new WatchlistEntry(symbol.toUpperCase(), displayName));
            updatedAt = Instant.now();
        }
    }

    public void removeCrypto(String symbol) {
        cryptoSymbols.removeIf(e -> e.getSymbol().equalsIgnoreCase(symbol));
        updatedAt = Instant.now();
    }

    public void addStock(String symbol, String displayName) {
        boolean exists = stockSymbols.stream()
                .anyMatch(e -> e.getSymbol().equalsIgnoreCase(symbol));
        if (!exists) {
            stockSymbols.add(new WatchlistEntry(symbol.toUpperCase(), displayName));
            updatedAt = Instant.now();
        }
    }

    public void removeStock(String symbol) {
        stockSymbols.removeIf(e -> e.getSymbol().equalsIgnoreCase(symbol));
        updatedAt = Instant.now();
    }

    public List<String> getAllSymbols() {
        List<String> all = new ArrayList<>();
        cryptoSymbols.forEach(e -> all.add(e.getSymbol()));
        stockSymbols.forEach(e -> all.add(e.getSymbol()));
        return all;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getId()                                    { return id; }
    public void   setId(String id)                          { this.id = id; }

    public String getUserId()                                { return userId; }
    public void   setUserId(String userId)                  { this.userId = userId; }

    public String getEmail()                                 { return email; }
    public void   setEmail(String email)                    { this.email = email; }

    public List<WatchlistEntry> getCryptoSymbols()          { return cryptoSymbols; }
    public void setCryptoSymbols(List<WatchlistEntry> c)   { this.cryptoSymbols = c; }

    public List<WatchlistEntry> getStockSymbols()           { return stockSymbols; }
    public void setStockSymbols(List<WatchlistEntry> s)    { this.stockSymbols = s; }

    public Instant getUpdatedAt()                            { return updatedAt; }
    public void    setUpdatedAt(Instant updatedAt)          { this.updatedAt = updatedAt; }

    // ── Embedded entry ────────────────────────────────────────────────────────

    public static class WatchlistEntry {
        private String symbol;
        private String displayName;
        private Instant addedAt = Instant.now();

        public WatchlistEntry() {}

        public WatchlistEntry(String symbol, String displayName) {
            this.symbol      = symbol;
            this.displayName = displayName;
            this.addedAt     = Instant.now();
        }

        public String  getSymbol()                      { return symbol; }
        public void    setSymbol(String symbol)        { this.symbol = symbol; }

        public String  getDisplayName()                 { return displayName; }
        public void    setDisplayName(String dn)       { this.displayName = dn; }

        public Instant getAddedAt()                     { return addedAt; }
        public void    setAddedAt(Instant addedAt)     { this.addedAt = addedAt; }
    }
}