package com.marketai.dashboard.service;

import com.marketai.dashboard.model.CryptoPriceEvent;
import com.marketai.dashboard.model.HistoricalPrice;
import com.marketai.dashboard.model.StockPriceEvent;
import com.marketai.dashboard.repository.HistoricalPriceRepository;
import com.marketai.dashboard.repository.MarketPriceRepository;
import com.marketai.dashboard.repository.StockPriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates raw price ticks into OHLCV candles.
 */
@Service
public class HistoricalDataService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalDataService.class);

    private static final List<String> CRYPTO_SYMBOLS = List.of(
            "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT",
            "ADAUSDT", "DOGEUSDT", "AVAXUSDT", "DOTUSDT", "MATICUSDT"
    );

    private static final List<String> STOCK_SYMBOLS = List.of(
            "RELIANCE.NS",
            "TCS.NS",
            "INFY.NS",
            "HDFCBANK.NS",
            "ICICIBANK.NS"
    );

    private final MarketPriceRepository cryptoRepository;
    private final StockPriceRepository stockRepository;
    private final HistoricalPriceRepository historyRepository;

    public HistoricalDataService(MarketPriceRepository cryptoRepository,
                                 StockPriceRepository stockRepository,
                                 HistoricalPriceRepository historyRepository) {
        this.cryptoRepository = cryptoRepository;
        this.stockRepository = stockRepository;
        this.historyRepository = historyRepository;
    }

    // ─────────────────────────────────────────────────────────────
    // SCHEDULED TASKS
    // ─────────────────────────────────────────────────────────────

    @Scheduled(cron = "0 * * * * *")
    public void build1MinCandles() {
        Instant from = Instant.now().minus(2, ChronoUnit.MINUTES);
        Instant to = Instant.now().minus(1, ChronoUnit.MINUTES);
        buildCryptoCandles("1m", from, to);
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void build5MinCandles() {
        Instant from = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant to = Instant.now().minus(5, ChronoUnit.MINUTES);
        buildCryptoCandles("5m", from, to);
    }

    @Scheduled(cron = "0 0 * * * *")
    public void build1HourCandles() {
        Instant from = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant to = Instant.now().minus(1, ChronoUnit.HOURS);
        buildCryptoCandles("1h", from, to);
        buildStockCandles("1h", from, to);
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void buildDailyCandles() {
        Instant from = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant to = Instant.now().minus(1, ChronoUnit.DAYS);
        buildCryptoCandles("1d", from, to);
        buildStockCandles("1d", from, to);
    }

    // ─────────────────────────────────────────────────────────────
    // CANDLE BUILDERS
    // ─────────────────────────────────────────────────────────────

    private void buildCryptoCandles(String interval, Instant from, Instant to) {
        for (String symbol : CRYPTO_SYMBOLS) {
            try {
                List<CryptoPriceEvent> ticks =
                        cryptoRepository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(
                                symbol, from, to);

                if (ticks.isEmpty()) continue;

                HistoricalPrice candle = aggregateCryptoTicks(symbol, "crypto", interval, ticks);
                saveIfNotExists(candle);

            } catch (Exception e) {
                log.error("❌ Failed to build {} candle for {}: {}", interval, symbol, e.getMessage());
            }
        }
    }

    private void buildStockCandles(String interval, Instant from, Instant to) {
        for (String symbol : STOCK_SYMBOLS) {
            try {
                List<StockPriceEvent> ticks =
                        stockRepository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(
                                symbol, from, to);

                if (ticks.isEmpty()) continue;

                HistoricalPrice candle = aggregateStockTicks(symbol, "stock", interval, ticks);
                saveIfNotExists(candle);

            } catch (Exception e) {
                log.error("❌ Failed to build {} stock candle for {}: {}", interval, symbol, e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // AGGREGATION LOGIC
    // ─────────────────────────────────────────────────────────────

    private HistoricalPrice aggregateCryptoTicks(String symbol, String type,
                                                 String interval,
                                                 List<CryptoPriceEvent> ticks) {

        double open = ticks.get(0).getPrice();
        double close = ticks.get(ticks.size() - 1).getPrice();
        double high = ticks.stream().mapToDouble(CryptoPriceEvent::getPrice).max().orElse(open);
        double low = ticks.stream().mapToDouble(CryptoPriceEvent::getPrice).min().orElse(open);

        // FIXED → volume SUM (not average)
        double volume = ticks.stream().mapToDouble(CryptoPriceEvent::getVolume).sum();
        long trades = ticks.stream().mapToLong(CryptoPriceEvent::getTradeCount).sum();

        HistoricalPrice hp = new HistoricalPrice(symbol, type, interval,
                open, high, low, close, volume, ticks.get(0).getTimestamp());

        hp.setTradeCount(trades);
        hp.setClosed(true);
        return hp;
    }

    private HistoricalPrice aggregateStockTicks(String symbol, String type,
                                                String interval,
                                                List<StockPriceEvent> ticks) {

        double open = ticks.get(0).getPrice();
        double close = ticks.get(ticks.size() - 1).getPrice();
        double high = ticks.stream().mapToDouble(StockPriceEvent::getPrice).max().orElse(open);
        double low = ticks.stream().mapToDouble(StockPriceEvent::getPrice).min().orElse(open);

        // FIXED → volume SUM (not average)
        double volume = ticks.stream().mapToLong(StockPriceEvent::getVolume).sum();

        HistoricalPrice hp = new HistoricalPrice(symbol, type, interval,
                open, high, low, close, volume, ticks.get(0).getTimestamp());

        hp.setClosed(true);
        return hp;
    }

    // ─────────────────────────────────────────────────────────────
    // DUPLICATE PROTECTION
    // ─────────────────────────────────────────────────────────────

    private void saveIfNotExists(HistoricalPrice candle) {

        boolean exists = historyRepository
                .existsBySymbolAndIntervalAndTimestamp(
                        candle.getSymbol(),
                        candle.getInterval(),
                        candle.getTimestamp());

        if (!exists) {
            historyRepository.save(candle);
            log.debug("📊 Saved {} {} candle", candle.getSymbol(), candle.getInterval());
        } else {
            log.debug("⚠️ Duplicate candle skipped: {} {}", candle.getSymbol(), candle.getInterval());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // BACKFILL
    // ─────────────────────────────────────────────────────────────

    public void backfillLast24Hours() {
        log.info("🔄 Backfilling last 24h of hourly candles...");
        for (int h = 23; h >= 1; h--) {
            Instant from = Instant.now().minus(h + 1, ChronoUnit.HOURS);
            Instant to = Instant.now().minus(h, ChronoUnit.HOURS);
            buildCryptoCandles("1h", from, to);
            buildStockCandles("1h", from, to);
        }
        log.info("✅ Candle backfill complete");
    }

    // ─────────────────────────────────────────────────────────────
    // QUERY METHODS
    // ─────────────────────────────────────────────────────────────

    public List<HistoricalPrice> getCandles(String symbol, String interval, int limit) {

        List<HistoricalPrice> all =
                historyRepository.findTop200BySymbolAndIntervalOrderByTimestampDesc(
                        symbol.toUpperCase(), interval);

        int fromIdx = Math.max(0, all.size() - limit);
        List<HistoricalPrice> result = new ArrayList<>(all.subList(fromIdx, all.size()));

        result.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        return result;
    }

    public List<HistoricalPrice> getCandlesInRange(String symbol, String interval,
                                                   Instant from, Instant to) {

        return historyRepository
                .findBySymbolAndIntervalAndTimestampBetweenOrderByTimestampAsc(
                        symbol.toUpperCase(), interval, from, to);
    }
}