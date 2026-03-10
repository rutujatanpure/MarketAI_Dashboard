package com.marketai.dashboard.service;

import com.marketai.dashboard.model.BacktestResult;
import com.marketai.dashboard.model.BacktestResult.SignalDetail;
import com.marketai.dashboard.model.CryptoPriceEvent;
import com.marketai.dashboard.model.TechnicalIndicator;
import com.marketai.dashboard.repository.BacktestRepository;
import com.marketai.dashboard.repository.MarketPriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class BacktestingEngine {

    private static final Logger log = LoggerFactory.getLogger(BacktestingEngine.class);

    // ── Backtesting parameters ─────────────────────────────────────────────
    private static final double DEFAULT_Z_THRESHOLD    = 2.5;
    private static final int    DEFAULT_RISK_THRESHOLD = 70;
    private static final int    LOOKAHEAD_POINTS       = 10;  // check price after N events
    private static final double CORRECT_MOVE_PCT       = 0.5; // 0.5% = signal confirmed
    private static final int    MAX_RECENT_SIGNALS     = 20;  // for UI display
    private static final double RISK_FREE_RATE         = 6.5; // Indian risk-free (% annual)

    private final MarketPriceRepository priceRepository;
    private final BacktestRepository    backtestRepository;
    private final TechnicalIndicatorService technicalIndicatorService;

    public BacktestingEngine(MarketPriceRepository priceRepository,
                             BacktestRepository backtestRepository,
                             TechnicalIndicatorService technicalIndicatorService) {
        this.priceRepository         = priceRepository;
        this.backtestRepository      = backtestRepository;
        this.technicalIndicatorService = technicalIndicatorService;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAIN ENTRY — Async backtest run
    // Triggered by: Admin clicking "Run Backtest" OR @Scheduled weekly
    // ═══════════════════════════════════════════════════════════════════════
    @Async("backtestExecutor")
    public void runBacktest(String symbol, String strategyType, int daysBack) {
        log.info("🔬 Starting backtest: symbol={} strategy={} daysBack={}",
                symbol, strategyType, daysBack);
        long start = System.currentTimeMillis();

        try {
            // ── Fetch historical data ──────────────────────────────────────
            Instant from = Instant.now().minus(daysBack, ChronoUnit.DAYS);
            List<CryptoPriceEvent> history = priceRepository
                    .findBySymbolAndTimestampAfterOrderByTimestampAsc(symbol, from);

            if (history.size() < 50) {
                log.warn("⚠️ Insufficient data for backtest: {} only has {} points",
                        symbol, history.size());
                saveMinimalResult(symbol, strategyType, history.size());
                return;
            }

            log.info("📊 Backtesting {} with {} data points ({} days)",
                    symbol, history.size(), daysBack);

            // ── Run the appropriate strategy ───────────────────────────────
            BacktestResult result = switch (strategyType) {
                case "ANOMALY_DETECTION" -> runAnomalyBacktest(symbol, history);
                case "RISK_SCORE"        -> runRiskScoreBacktest(symbol, history);
                case "CONFLUENCE"        -> runConfluenceBacktest(symbol, history);
                default                  -> runAnomalyBacktest(symbol, history);
            };

            result.setStrategyType(strategyType);
            result.setDataFrom(from);
            result.setDataTo(Instant.now());
            result.setTotalDataPoints(history.size());
            result.setRunAt(Instant.now());

            // ── Calculate grade ────────────────────────────────────────────
            result.setGrade(computeGrade(result));
            result.setSummary(buildSummary(result));

            // ── Save ───────────────────────────────────────────────────────
            backtestRepository.save(result);

            long elapsed = System.currentTimeMillis() - start;
            log.info("✅ Backtest complete: {} {} | accuracy={:.1f}% precision={:.1f}% " +
                            "F1={:.2f} Sharpe={:.2f} PnL={:.1f}% grade={} time={}ms",
                    symbol, strategyType,
                    result.getAccuracy() * 100, result.getPrecision() * 100,
                    result.getF1Score(), result.getSharpeRatio(),
                    result.getSimulatedPnlPercent(), result.getGrade(), elapsed);

        } catch (Exception e) {
            log.error("❌ Backtest failed for {} {}: {}", symbol, strategyType, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANOMALY DETECTION BACKTEST
    // Signal: Z-Score > 2.5 → predict price reversal within LOOKAHEAD_POINTS
    // Correct if: price moved >= CORRECT_MOVE_PCT in predicted direction
    // ═══════════════════════════════════════════════════════════════════════
    private BacktestResult runAnomalyBacktest(String symbol,
                                              List<CryptoPriceEvent> history) {
        BacktestResult result = new BacktestResult();
        result.setSymbol(symbol);
        result.setZScoreThreshold(DEFAULT_Z_THRESHOLD);

        int tp = 0, fp = 0, fn = 0;
        int totalSignals   = 0;
        int correctSignals = 0;

        // P&L simulation variables
        double capital         = 10000.0;  // ₹10,000 start
        double maxCapital      = capital;
        double minCapital      = capital;
        double maxDrawdown     = 0.0;
        List<Double> returns   = new ArrayList<>();
        List<SignalDetail> recentSignals = new ArrayList<>();

        // Sliding window for Z-Score calculation
        Deque<Double>  priceWindow  = new ArrayDeque<>();
        Deque<Double>  volumeWindow = new ArrayDeque<>();

        // Count actual anomalies (ground truth)
        int actualAnomalies = 0;

        for (int i = 0; i < history.size() - LOOKAHEAD_POINTS; i++) {
            CryptoPriceEvent event = history.get(i);

            // Update windows
            priceWindow.addLast(event.getPrice());
            if (priceWindow.size() > 100) priceWindow.pollFirst();
            volumeWindow.addLast(event.getVolume());
            if (volumeWindow.size() > 100) volumeWindow.pollFirst();

            if (priceWindow.size() < 20) continue;

            // Compute Z-Score from rolling window
            double[] priceArr = priceWindow.stream().mapToDouble(Double::doubleValue).toArray();
            double   mean     = Arrays.stream(priceArr).average().orElse(event.getPrice());
            double   variance = Arrays.stream(priceArr)
                    .map(p -> (p - mean) * (p - mean)).average().orElse(0);
            double   std      = Math.sqrt(variance);
            double   zScore   = std > 0 ? (event.getPrice() - mean) / std : 0.0;

            // Ground truth: did a significant move happen within lookahead?
            double futurePrice = history.get(i + LOOKAHEAD_POINTS).getPrice();
            double actualMove  = ((futurePrice - event.getPrice()) / event.getPrice()) * 100.0;
            boolean wasAnomaly = Math.abs(actualMove) >= CORRECT_MOVE_PCT * 3;

            if (wasAnomaly) actualAnomalies++;

            // Our prediction: anomaly if |Z| > threshold
            boolean predictedAnomaly = Math.abs(zScore) >= DEFAULT_Z_THRESHOLD;

            if (predictedAnomaly) {
                totalSignals++;
                String signalType = zScore > 0 ? "SELL" : "BUY";  // Z>0=overpriced→SELL
                boolean correct   = false;

                if (wasAnomaly) {
                    tp++;
                    // Check direction
                    if (signalType.equals("SELL") && actualMove < -CORRECT_MOVE_PCT) {
                        correct = true;
                        correctSignals++;
                        // Simulate trade profit
                        double tradePnl = Math.abs(actualMove) * 0.8; // 80% capture
                        capital += capital * (tradePnl / 100.0);
                        returns.add(tradePnl);
                    } else if (signalType.equals("BUY") && actualMove > CORRECT_MOVE_PCT) {
                        correct = true;
                        correctSignals++;
                        double tradePnl = actualMove * 0.8;
                        capital += capital * (tradePnl / 100.0);
                        returns.add(tradePnl);
                    } else {
                        fp++;
                        double tradePnl = -Math.abs(actualMove) * 0.5; // stop-loss
                        capital += capital * (tradePnl / 100.0);
                        returns.add(tradePnl);
                    }
                } else {
                    fp++;
                    returns.add(-0.2); // transaction cost
                }

                // Track drawdown
                maxCapital = Math.max(maxCapital, capital);
                minCapital = Math.min(minCapital, capital);
                if (maxCapital > 0) {
                    double drawdown = (maxCapital - capital) / maxCapital * 100.0;
                    maxDrawdown = Math.max(maxDrawdown, drawdown);
                }

                // Store recent signal for UI
                if (recentSignals.size() < MAX_RECENT_SIGNALS) {
                    recentSignals.add(new SignalDetail(
                            event.getTimestamp(), signalType,
                            event.getPrice(), futurePrice, actualMove,
                            correct, zScore, 0
                    ));
                }
            } else if (wasAnomaly) {
                fn++; // We missed a real anomaly
            }
        }

        // ── Calculate metrics ──────────────────────────────────────────────
        double accuracy   = totalSignals > 0 ? (double) correctSignals / totalSignals : 0;
        double precision  = (tp + fp) > 0    ? (double) tp / (tp + fp)               : 0;
        double recall     = (tp + fn) > 0    ? (double) tp / (tp + fn)               : 0;
        double f1         = (precision + recall) > 0
                ? 2.0 * precision * recall / (precision + recall) : 0;

        result.setTotalSignals(totalSignals);
        result.setCorrectSignals(correctSignals);
        result.setIncorrectSignals(totalSignals - correctSignals);
        result.setFalsePositives(fp);
        result.setFalseNegatives(fn);
        result.setAccuracy(Math.round(accuracy * 10000.0) / 10000.0);
        result.setPrecision(Math.round(precision * 10000.0) / 10000.0);
        result.setRecall(Math.round(recall * 10000.0) / 10000.0);
        result.setF1Score(Math.round(f1 * 10000.0) / 10000.0);

        // ── P&L Metrics ────────────────────────────────────────────────────
        double simulatedPnl = ((capital - 10000.0) / 10000.0) * 100.0;
        result.setSimulatedPnlPercent(Math.round(simulatedPnl * 100.0) / 100.0);
        result.setMaxDrawdownPercent(Math.round(maxDrawdown * 100.0) / 100.0);

        // Buy-and-hold comparison
        if (!history.isEmpty()) {
            double firstPrice = history.get(0).getPrice();
            double lastPrice  = history.get(history.size()-1).getPrice();
            double bah        = ((lastPrice - firstPrice) / firstPrice) * 100.0;
            result.setPnlVsBenchmark(Math.round((simulatedPnl - bah) * 100.0) / 100.0);
        }

        // ── Sharpe Ratio ───────────────────────────────────────────────────
        if (!returns.isEmpty()) {
            double meanReturn = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double stdDev     = Math.sqrt(returns.stream()
                    .mapToDouble(r -> (r - meanReturn) * (r - meanReturn))
                    .average().orElse(0));
            double dailyRf    = RISK_FREE_RATE / 252.0 / 100.0;
            double sharpe     = stdDev > 0
                    ? (meanReturn/100.0 - dailyRf) / (stdDev/100.0) * Math.sqrt(252)
                    : 0;
            result.setSharpeRatio(Math.round(sharpe * 100.0) / 100.0);

            // Win rate
            long   wins     = returns.stream().filter(r -> r > 0).count();
            result.setWinRate(Math.round((double) wins / returns.size() * 10000.0) / 10000.0);

            // Avg win/loss
            OptionalDouble avgWin = returns.stream().filter(r -> r > 0)
                    .mapToDouble(Double::doubleValue).average();
            OptionalDouble avgLoss = returns.stream().filter(r -> r < 0)
                    .mapToDouble(Double::doubleValue).average();
            result.setAvgWinPercent(avgWin.isPresent()
                    ? Math.round(avgWin.getAsDouble() * 100.0) / 100.0 : 0);
            result.setAvgLossPercent(avgLoss.isPresent()
                    ? Math.round(avgLoss.getAsDouble() * 100.0) / 100.0 : 0);
        }

        result.setRecentSignals(recentSignals);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RISK SCORE BACKTEST
    // Signal: risk score > threshold → predict drawdown within LOOKAHEAD
    // ═══════════════════════════════════════════════════════════════════════
    private BacktestResult runRiskScoreBacktest(String symbol,
                                                List<CryptoPriceEvent> history) {
        BacktestResult result = new BacktestResult();
        result.setSymbol(symbol);
        result.setRiskScoreThreshold(DEFAULT_RISK_THRESHOLD);

        int tp = 0, fp = 0, fn = 0, totalSignals = 0, correct = 0;
        double capital = 10000.0, maxCap = 10000.0, maxDrawdown = 0.0;
        List<Double> returns = new ArrayList<>();
        List<SignalDetail> recentSignals = new ArrayList<>();

        Deque<Double> priceWindow  = new ArrayDeque<>();
        Deque<Double> volumeWindow = new ArrayDeque<>();

        for (int i = 20; i < history.size() - LOOKAHEAD_POINTS; i++) {
            CryptoPriceEvent event = history.get(i);

            priceWindow.addLast(event.getPrice());
            if (priceWindow.size() > 100) priceWindow.pollFirst();
            volumeWindow.addLast(event.getVolume());
            if (volumeWindow.size() > 100) volumeWindow.pollFirst();

            // Simple risk score computation (mirroring SmartRiskEngine logic)
            double[] prices = priceWindow.stream().mapToDouble(Double::doubleValue).toArray();
            double mean  = Arrays.stream(prices).average().orElse(event.getPrice());
            double var   = Arrays.stream(prices).map(p -> (p-mean)*(p-mean)).average().orElse(0);
            double std   = Math.sqrt(var);
            double zScore = std > 0 ? Math.abs((event.getPrice() - mean) / std) : 0;

            double avgVol = volumeWindow.stream().mapToDouble(Double::doubleValue).average().orElse(1);
            double volRatio = avgVol > 0 ? event.getVolume() / avgVol : 1.0;

            // Simplified risk score (0-100)
            int riskScore = (int) Math.min(100,
                    zScore * 20 + (volRatio > 2 ? 30 : volRatio > 1.5 ? 15 : 0)
                            + Math.abs(event.getPriceChange()) * 3);

            double futurePrice = history.get(i + LOOKAHEAD_POINTS).getPrice();
            double actualMove  = ((futurePrice - event.getPrice()) / event.getPrice()) * 100.0;
            boolean wasDrawdown = actualMove < -CORRECT_MOVE_PCT * 2;

            boolean highRisk = riskScore >= DEFAULT_RISK_THRESHOLD;
            if (highRisk) {
                totalSignals++;
                boolean isCorrect = wasDrawdown;
                if (isCorrect) { tp++; correct++; }
                else           { fp++; }

                double tradePnl = isCorrect ? Math.abs(actualMove) * 0.6 : -0.3;
                capital += capital * (tradePnl / 100.0);
                returns.add(tradePnl);
                maxCap = Math.max(maxCap, capital);
                maxDrawdown = Math.max(maxDrawdown, maxCap > 0 ? (maxCap-capital)/maxCap*100 : 0);

                if (recentSignals.size() < MAX_RECENT_SIGNALS) {
                    recentSignals.add(new SignalDetail(
                            event.getTimestamp(), "SELL",
                            event.getPrice(), futurePrice, actualMove,
                            isCorrect, zScore, riskScore));
                }
            } else if (wasDrawdown) {
                fn++;
            }
        }

        fillMetrics(result, tp, fp, fn, totalSignals, correct, capital, maxDrawdown, returns);
        result.setRecentSignals(recentSignals);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONFLUENCE BACKTEST
    // Signal: all timeframes agree → price moved in predicted direction?
    // ═══════════════════════════════════════════════════════════════════════
    private BacktestResult runConfluenceBacktest(String symbol,
                                                 List<CryptoPriceEvent> history) {
        BacktestResult result = new BacktestResult();
        result.setSymbol(symbol);

        int tp = 0, fp = 0, fn = 0, totalSignals = 0, correct = 0;
        double capital = 10000.0, maxCap = 10000.0, maxDrawdown = 0.0;
        List<Double> returns = new ArrayList<>();
        List<SignalDetail> recentSignals = new ArrayList<>();

        // Need at least 30 points for confluence simulation
        for (int i = 30; i < history.size() - LOOKAHEAD_POINTS; i++) {
            CryptoPriceEvent event = history.get(i);
            List<CryptoPriceEvent> window = history.subList(
                    Math.max(0, i - 30), i);

            // Simulate 4 timeframe agreement (using different window sizes)
            int buyCount = 0, sellCount = 0;
            int[] windowSizes = {5, 10, 15, 30};

            for (int ws : windowSizes) {
                List<CryptoPriceEvent> tfWindow = history.subList(
                        Math.max(0, i - ws), i);
                if (tfWindow.size() < 5) continue;

                double rsi = computeSimpleRSI(
                        tfWindow.stream().mapToDouble(CryptoPriceEvent::getPrice)
                                .boxed().collect(Collectors.toList()), 14);

                if      (rsi < 35) buyCount++;
                else if (rsi > 65) sellCount++;
            }

            int maxCount = Math.max(buyCount, sellCount);
            String signal = buyCount > sellCount ? "BUY" : "SELL";

            if (maxCount >= 3) { // Confluence: 3+ timeframes agree
                totalSignals++;
                double futurePrice = history.get(i + LOOKAHEAD_POINTS).getPrice();
                double actualMove  = ((futurePrice - event.getPrice()) / event.getPrice()) * 100.0;

                boolean isCorrect = (signal.equals("BUY")  && actualMove >  CORRECT_MOVE_PCT) ||
                        (signal.equals("SELL") && actualMove < -CORRECT_MOVE_PCT);

                if (isCorrect) { tp++; correct++; }
                else           { fp++; }

                double tradePnl = isCorrect ? Math.abs(actualMove) * 0.75 : -0.4;
                capital += capital * (tradePnl / 100.0);
                returns.add(tradePnl);
                maxCap = Math.max(maxCap, capital);
                maxDrawdown = Math.max(maxDrawdown, maxCap > 0 ? (maxCap-capital)/maxCap*100 : 0);

                if (recentSignals.size() < MAX_RECENT_SIGNALS) {
                    recentSignals.add(new SignalDetail(
                            event.getTimestamp(), signal,
                            event.getPrice(), futurePrice, actualMove, isCorrect, 0, 0));
                }
            }

            // Count missed: price moved but we had no confluence
            double futurePrice = history.get(i + LOOKAHEAD_POINTS).getPrice();
            double move = Math.abs(((futurePrice - event.getPrice()) / event.getPrice()) * 100.0);
            if (maxCount < 3 && move > CORRECT_MOVE_PCT * 3) fn++;
        }

        fillMetrics(result, tp, fp, fn, totalSignals, correct, capital, maxDrawdown, returns);
        result.setRecentSignals(recentSignals);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Shared metric calculation
    // ═══════════════════════════════════════════════════════════════════════
    private void fillMetrics(BacktestResult r, int tp, int fp, int fn,
                             int total, int correct, double capital,
                             double maxDrawdown, List<Double> returns) {
        double accuracy  = total > 0    ? (double) correct / total : 0;
        double precision = (tp+fp) > 0 ? (double) tp / (tp+fp)    : 0;
        double recall    = (tp+fn) > 0 ? (double) tp / (tp+fn)    : 0;
        double f1        = (precision+recall) > 0
                ? 2*precision*recall/(precision+recall) : 0;

        r.setTotalSignals(total);
        r.setCorrectSignals(correct);
        r.setIncorrectSignals(total - correct);
        r.setFalsePositives(fp);
        r.setFalseNegatives(fn);
        r.setAccuracy(round2(accuracy));
        r.setPrecision(round2(precision));
        r.setRecall(round2(recall));
        r.setF1Score(round2(f1));

        double pnl = ((capital - 10000.0) / 10000.0) * 100.0;
        r.setSimulatedPnlPercent(round2(pnl));
        r.setMaxDrawdownPercent(round2(maxDrawdown));

        if (!returns.isEmpty()) {
            double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double std  = Math.sqrt(returns.stream()
                    .mapToDouble(rv -> (rv-mean)*(rv-mean)).average().orElse(0));
            double rf   = RISK_FREE_RATE / 252.0 / 100.0;
            double sharpe = std > 0 ? (mean/100.0 - rf) / (std/100.0) * Math.sqrt(252) : 0;
            r.setSharpeRatio(round2(sharpe));

            long wins = returns.stream().filter(rv -> rv > 0).count();
            r.setWinRate(round2((double) wins / returns.size()));

            OptionalDouble avgWin  = returns.stream().filter(rv -> rv > 0).mapToDouble(Double::doubleValue).average();
            OptionalDouble avgLoss = returns.stream().filter(rv -> rv < 0).mapToDouble(Double::doubleValue).average();
            r.setAvgWinPercent(avgWin.isPresent()  ? round2(avgWin.getAsDouble())  : 0);
            r.setAvgLossPercent(avgLoss.isPresent() ? round2(avgLoss.getAsDouble()) : 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Grade Assignment
    // ═══════════════════════════════════════════════════════════════════════
    private String computeGrade(BacktestResult r) {
        double score = 0;
        score += r.getF1Score()    * 30;   // 30% weight on F1
        score += r.getSharpeRatio() > 0 ? Math.min(r.getSharpeRatio() / 2.0, 1.0) * 25 : 0;
        score += r.getWinRate()    * 20;   // 20% win rate
        score += (r.getSimulatedPnlPercent() > 0 ? Math.min(r.getSimulatedPnlPercent()/20,1.0) : 0) * 15;
        score += (r.getPnlVsBenchmark() > 0 ? Math.min(r.getPnlVsBenchmark()/10,1.0) : 0) * 10;

        if (score >= 80) return "A+";
        if (score >= 70) return "A";
        if (score >= 60) return "B";
        if (score >= 50) return "C";
        return "D";
    }

    private String buildSummary(BacktestResult r) {
        return String.format(
                "%s strategy on %s: %d signals | Precision %.0f%% | Recall %.0f%% | " +
                        "F1=%.2f | PnL %.1f%% | Sharpe %.2f | MaxDD %.1f%% | Grade %s",
                r.getStrategyType(), r.getSymbol(), r.getTotalSignals(),
                r.getPrecision()*100, r.getRecall()*100, r.getF1Score(),
                r.getSimulatedPnlPercent(), r.getSharpeRatio(),
                r.getMaxDrawdownPercent(), r.getGrade()
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private double computeSimpleRSI(List<Double> prices, int period) {
        if (prices.size() < period + 1) return 50.0;
        double gain = 0, loss = 0;
        for (int i = prices.size()-period; i < prices.size(); i++) {
            double d = prices.get(i) - prices.get(i-1);
            if (d > 0) gain += d; else loss += Math.abs(d);
        }
        gain /= period;
        loss /= period;
        if (loss == 0) return 100;
        return 100 - (100 / (1 + gain/loss));
    }

    private void saveMinimalResult(String symbol, String strategy, int dataPoints) {
        BacktestResult r = new BacktestResult();
        r.setSymbol(symbol);
        r.setStrategyType(strategy);
        r.setRunAt(Instant.now());
        r.setTotalDataPoints(dataPoints);
        r.setSummary("Insufficient data: " + dataPoints + " points (need 50+)");
        r.setGrade("N/A");
        backtestRepository.save(r);
    }

    private double round2(double v) { return Math.round(v * 10000.0) / 10000.0; }

    // ── Public query methods ───────────────────────────────────────────────
    public List<BacktestResult> getLatestResults(String symbol) {
        return backtestRepository.findBySymbolOrderByRunAtDesc(symbol);
    }

    public Optional<BacktestResult> getBestResult(String symbol) {
        return backtestRepository.findTopBySymbolOrderByF1ScoreDesc(symbol);
    }

    public List<BacktestResult> getAllLatest() {
        return backtestRepository.findAll().stream()
                .sorted(Comparator.comparing(BacktestResult::getRunAt).reversed())
                .limit(20).collect(Collectors.toList());
    }

    // ── Schedule weekly auto-backtest (runs every Sunday 2am) ─────────────
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 2 * * SUN")
    public void weeklyAutoBacktest() {
        log.info("📅 Weekly auto-backtest starting...");
        List<String> symbols = List.of("BTCUSDT", "ETHUSDT", "SOLUSDT",
                "BNBUSDT", "RELIANCE", "INFY", "TCS");
        for (String s : symbols) {
            runBacktest(s, "ANOMALY_DETECTION", 90);
            runBacktest(s, "RISK_SCORE",        90);
        }
    }
}