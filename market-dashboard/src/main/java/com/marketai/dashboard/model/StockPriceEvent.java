package com.marketai.dashboard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Document(collection = "stock_prices")
@CompoundIndexes({
        @CompoundIndex(name = "symbol_ts",
                def = "{'symbol': 1, 'timestamp': -1}"),
        @CompoundIndex(name = "symbol_status_idx",
                def = "{'symbol': 1, 'marketStatus': 1}")
})
public class StockPriceEvent {

    @Id
    private String id;

    @Indexed                    // ← Sirf symbol pe rakho
    private String symbol;

    private double price;
    private double open;
    private double high;
    private double low;
    private double previousClose;
    private double change;
    private double changePercent;
    private long   volume;
    private long   latestTradingDay;

    private String name;
    private String exchange="NSE";
    private String currency  = "INR";
    private String type      = "stock";
    private double fiftyTwoWeekHigh;
    private double fiftyTwoWeekLow;
    private String marketStatus;

    // ✅ NO @Indexed — annotation bilkul nahi
    @Field("expires_at")
    private Instant expiresAt;

    // ✅ NO @Indexed — annotation bilkul nahi
    private Instant timestamp = Instant.now();

    // ── Constructors ──────────────────────────────────────────────────────────

    public StockPriceEvent() {}

    public StockPriceEvent(String symbol, double price, double changePercent) {
        this.symbol        = symbol;
        this.price         = price;
        this.changePercent = changePercent;
        this.timestamp     = Instant.now();
        this.expiresAt     = Instant.now().plusSeconds(604_800);
        setMarketStatusAuto();
    }

    // ── Market Status ─────────────────────────────────────────────────────────

    public void setMarketStatusAuto() {

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        int totalMinutes = now.getHour() * 60 + now.getMinute();

        // NSE Trading Hours:
        // Pre-open: 9:00–9:15 (540–555)
        // Regular: 9:15–15:30 (555–930)

        if (totalMinutes >= 555 && totalMinutes < 930) {
            this.marketStatus = "open";
        } else if (totalMinutes >= 540 && totalMinutes < 555) {
            this.marketStatus = "pre-open";
        } else {
            this.marketStatus = "closed";
        }
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getOpen() { return open; }
    public void setOpen(double open) { this.open = open; }

    public double getHigh() { return high; }
    public void setHigh(double high) { this.high = high; }

    public double getLow() { return low; }
    public void setLow(double low) { this.low = low; }

    public double getPreviousClose() { return previousClose; }
    public void setPreviousClose(double pc) { this.previousClose = pc; }

    public double getChange() { return change; }
    public void setChange(double change) { this.change = change; }

    public double getChangePercent() { return changePercent; }
    public void setChangePercent(double cp) { this.changePercent = cp; }

    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }

    public long getLatestTradingDay() { return latestTradingDay; }
    public void setLatestTradingDay(long ltd) { this.latestTradingDay = ltd; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getFiftyTwoWeekHigh() { return fiftyTwoWeekHigh; }
    public void setFiftyTwoWeekHigh(double v) { this.fiftyTwoWeekHigh = v; }

    public double getFiftyTwoWeekLow() { return fiftyTwoWeekLow; }
    public void setFiftyTwoWeekLow(double v) { this.fiftyTwoWeekLow = v; }

    public String getMarketStatus() { return marketStatus; }
    public void setMarketStatus(String ms) { this.marketStatus = ms; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant e) { this.expiresAt = e; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}