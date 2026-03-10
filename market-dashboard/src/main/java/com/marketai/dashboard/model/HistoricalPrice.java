package com.marketai.dashboard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Stores OHLCV (Open/High/Low/Close/Volume) candle data.
 * Used for historical charts on the dashboard.
 * TTL will be set programmatically (30 days).
 */
@Document(collection = "historical_prices")
@CompoundIndexes({
        @CompoundIndex(name = "symbol_interval_ts", def = "{'symbol': 1, 'interval': 1, 'timestamp': -1}"),
        @CompoundIndex(name = "symbol_ts", def = "{'symbol': 1, 'timestamp': -1}")
})
public class HistoricalPrice {

    @Id
    private String id;

    @Indexed
    private String symbol;           // e.g. BTCUSDT, AAPL

    private String type;             // "crypto" or "stock"
    private String interval;         // "1m", "5m", "1h", "1d"

    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
    private long tradeCount;

    private boolean isClosed;        // Binance: true = candle is finalized

    private Instant timestamp = Instant.now(); // TTL will be applied programmatically

    // ── Constructors ──────────────────────────────────────────────────────────

    public HistoricalPrice() {}

    public HistoricalPrice(String symbol, String type, String interval,
                           double open, double high, double low,
                           double close, double volume, Instant timestamp) {
        this.symbol = symbol;
        this.type = type;
        this.interval = interval;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.timestamp = timestamp;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getInterval() { return interval; }
    public void setInterval(String interval) { this.interval = interval; }

    public double getOpen() { return open; }
    public void setOpen(double open) { this.open = open; }

    public double getHigh() { return high; }
    public void setHigh(double high) { this.high = high; }

    public double getLow() { return low; }
    public void setLow(double low) { this.low = low; }

    public double getClose() { return close; }
    public void setClose(double close) { this.close = close; }

    public double getVolume() { return volume; }
    public void setVolume(double volume) { this.volume = volume; }

    public long getTradeCount() { return tradeCount; }
    public void setTradeCount(long tradeCount) { this.tradeCount = tradeCount; }

    public boolean isClosed() { return isClosed; }
    public void setClosed(boolean closed) { isClosed = closed; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
