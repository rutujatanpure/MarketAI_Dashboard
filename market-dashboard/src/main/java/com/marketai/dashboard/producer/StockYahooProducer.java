package com.marketai.dashboard.producer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.marketai.dashboard.controller.WebSocketController;
import com.marketai.dashboard.model.StockPriceEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class StockYahooProducer {

    private static final Logger log = LoggerFactory.getLogger(StockYahooProducer.class);

    // ✅ KafkaTemplate REMOVED — saves ~150MB RAM on free tier
    private final WebSocketController wsController;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private HttpClient httpClient;
    private String nseCookie = "";

    private final AtomicInteger nseFailCount = new AtomicInteger(0);
    private final AtomicInteger bseFailCount = new AtomicInteger(0);

    private static final Map<String, String> BSE_YAHOO_MAP = Map.ofEntries(
            Map.entry("RELIANCE-BSE",    "RELIANCE.BO"),
            Map.entry("HDFCBANK-BSE",    "HDFCBANK.BO"),
            Map.entry("INFY-BSE",        "INFY.BO"),
            Map.entry("TCS-BSE",         "TCS.BO"),
            Map.entry("ICICIBANK-BSE",   "ICICIBANK.BO"),
            Map.entry("SBIN-BSE",        "SBIN.BO"),
            Map.entry("AXISBANK-BSE",    "AXISBANK.BO"),
            Map.entry("ITC-BSE",         "ITC.BO"),
            Map.entry("KOTAKBANK-BSE",   "KOTAKBANK.BO"),
            Map.entry("LT-BSE",          "LT.BO"),
            Map.entry("HINDUNILVR-BSE",  "HINDUNILVR.BO"),
            Map.entry("BAJFINANCE-BSE",  "BAJFINANCE.BO"),
            Map.entry("MARUTI-BSE",      "MARUTI.BO"),
            Map.entry("SUNPHARMA-BSE",   "SUNPHARMA.BO"),
            Map.entry("TITAN-BSE",       "TITAN.BO"),
            Map.entry("WIPRO-BSE",       "WIPRO.BO"),
            Map.entry("NTPC-BSE",        "NTPC.BO"),
            Map.entry("POWERGRID-BSE",   "POWERGRID.BO"),
            Map.entry("TATASTEEL-BSE",   "TATASTEEL.BO"),
            Map.entry("HINDALCO-BSE",    "HINDALCO.BO"),
            Map.entry("BHARTIARTL-BSE",  "BHARTIARTL.BO"),
            Map.entry("BAJAJFINSV-BSE",  "BAJAJFINSV.BO"),
            Map.entry("HEROMOTOCO-BSE",  "HEROMOTOCO.BO"),
            Map.entry("INDUSINDBK-BSE",  "INDUSINDBK.BO"),
            Map.entry("ONGC-BSE",        "ONGC.BO"),
            Map.entry("ASIANPAINT-BSE",  "ASIANPAINT.BO"),
            Map.entry("TATACONSUM-BSE",  "TATACONSUM.BO"),
            Map.entry("MM-BSE",          "M&M.BO"),
            Map.entry("BAJAJ-AUTO-BSE",  "BAJAJ-AUTO.BO"),
            Map.entry("ADANIENT-BSE",    "ADANIENT.BO")
    );

    private static final Map<String, String> COMPANY_NAMES = Map.ofEntries(
            Map.entry("RELIANCE",   "Reliance Industries"),
            Map.entry("HDFCBANK",   "HDFC Bank"),
            Map.entry("INFY",       "Infosys"),
            Map.entry("TCS",        "Tata Consultancy"),
            Map.entry("ICICIBANK",  "ICICI Bank"),
            Map.entry("HINDUNILVR", "Hindustan Unilever"),
            Map.entry("SBIN",       "State Bank of India"),
            Map.entry("AXISBANK",   "Axis Bank"),
            Map.entry("ITC",        "ITC Limited"),
            Map.entry("KOTAKBANK",  "Kotak Mahindra Bank"),
            Map.entry("LT",         "Larsen & Toubro"),
            Map.entry("BAJFINANCE", "Bajaj Finance"),
            Map.entry("MARUTI",     "Maruti Suzuki"),
            Map.entry("SUNPHARMA",  "Sun Pharma"),
            Map.entry("TITAN",      "Titan Company"),
            Map.entry("WIPRO",      "Wipro"),
            Map.entry("NTPC",       "NTPC"),
            Map.entry("POWERGRID",  "Power Grid"),
            Map.entry("TECHM",      "Tech Mahindra"),
            Map.entry("HCLTECH",    "HCL Technologies"),
            Map.entry("TATASTEEL",  "Tata Steel"),
            Map.entry("HINDALCO",   "Hindalco"),
            Map.entry("BAJAJFINSV", "Bajaj Finserv"),
            Map.entry("HEROMOTOCO", "Hero MotoCorp"),
            Map.entry("MM",         "Mahindra & Mahindra"),
            Map.entry("BHARTIARTL", "Bharti Airtel"),
            Map.entry("INDUSINDBK", "IndusInd Bank"),
            Map.entry("ONGC",       "ONGC"),
            Map.entry("ADANIENT",   "Adani Enterprises"),
            Map.entry("ASIANPAINT", "Asian Paints"),
            Map.entry("TATACONSUM", "Tata Consumer"),
            Map.entry("BAJAJ-AUTO", "Bajaj Auto"),
            Map.entry("HDFC",       "HDFC Ltd")
    );

    public StockYahooProducer(WebSocketController wsController) {
        this.wsController = wsController;
    }

    @PostConstruct
    public void start() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        log.info("════════════════════════════════════════════════");
        log.info("📈 StockYahooProducer → NSE + BSE India");
        log.info("   NSE  : NSEIndia bulk API (Nifty 50, every 60s)");
        log.info("   BSE  : Yahoo Finance .BO  (Sensex 30, every 90s)");
        log.info("   Mode : Direct WebSocket push (no Kafka)");
        log.info("════════════════════════════════════════════════");

        initNseSession();
    }

    private void initNseSession() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.nseindia.com"))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                            + "AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .GET().build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            nseCookie = "";
            resp.headers().allValues("set-cookie")
                    .forEach(c -> nseCookie += c.split(";")[0] + "; ");

            log.info("✅ NSE session initialized (cookie len={})", nseCookie.length());
        } catch (Exception e) {
            log.error("❌ NSE session init failed: {}", e.getMessage());
        }
    }

    // ✅ 60s interval — free tier ke liye safe
    @Scheduled(fixedDelay = 60_000)
    public void fetchNseStocks() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.nseindia.com/api/equity-stockIndices?index=NIFTY%2050"))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                            + "AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Referer", "https://www.nseindia.com/")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Cookie", nseCookie)
                    .GET().build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            resp.headers().allValues("set-cookie")
                    .forEach(c -> nseCookie += c.split(";")[0] + "; ");

            if (resp.body().startsWith("<")) {
                log.warn("⚠️ NSE session expired — reinitializing...");
                initNseSession();
                return;
            }

            JsonNode root = mapper.readTree(resp.body());
            JsonNode data = root.path("data");
            if (data.isMissingNode() || !data.isArray()) return;

            int count = 0;
            for (JsonNode stock : data) {
                String symbol = stock.path("symbol").asText().trim();
                if (symbol.isBlank() || symbol.equalsIgnoreCase("NIFTY 50")) continue;

                double ltp       = stock.path("lastPrice").asDouble();
                double open      = stock.path("open").asDouble();
                double high      = stock.path("dayHigh").asDouble();
                double low       = stock.path("dayLow").asDouble();
                double close     = stock.path("previousClose").asDouble();
                double change    = stock.path("change").asDouble();
                double changePct = stock.path("pChange").asDouble();
                long   volume    = stock.path("totalTradedVolume").asLong();

                if (ltp <= 0) continue;

                StockPriceEvent event = buildStockEvent(
                        symbol, getCompanyName(symbol, "NSE"), "NSE",
                        ltp, open, high, low, close, change, changePct, volume);
                publishStock(event);
                count++;
            }

            nseFailCount.set(0);
            log.info("📈 NSE: {} Nifty 50 stocks → WebSocket ✅", count);

        } catch (Exception e) {
            int fails = nseFailCount.incrementAndGet();
            log.error("❌ NSE fetch failed (attempt {}): {}", fails, e.getMessage());
            if (fails >= 3) { nseFailCount.set(0); initNseSession(); }
        }
    }

    // ✅ 90s interval — free tier ke liye safe
    @Scheduled(fixedDelay = 90_000, initialDelay = 15_000)
    public void fetchBseStocks() {
        int success = 0;
        int failed  = 0;

        for (Map.Entry<String, String> entry : BSE_YAHOO_MAP.entrySet()) {
            String bseSymbol = entry.getKey();
            String yahooSym  = entry.getValue();
            String stockName = bseSymbol.replace("-BSE", "");

            try {
                String url = "https://query1.finance.yahoo.com/v8/finance/chart/"
                        + yahooSym + "?interval=1d&range=1d&includePrePost=false";

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                + "AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                        .header("Accept", "application/json")
                        .header("Referer", "https://finance.yahoo.com/")
                        .timeout(Duration.ofSeconds(10))
                        .GET().build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 429) {
                    Thread.sleep(5_000);
                    failed++;
                    continue;
                }
                if (resp.statusCode() != 200 || resp.body().isBlank()) { failed++; continue; }

                JsonNode root   = mapper.readTree(resp.body());
                JsonNode result = root.path("chart").path("result");
                if (!result.isArray() || result.isEmpty()) { failed++; continue; }

                JsonNode meta  = result.get(0).path("meta");
                double ltp     = meta.path("regularMarketPrice").asDouble();
                if (ltp <= 0) { failed++; continue; }

                double open  = meta.path("regularMarketOpen").asDouble();
                double high  = meta.path("regularMarketDayHigh").asDouble();
                double low   = meta.path("regularMarketDayLow").asDouble();
                double close = meta.path("chartPreviousClose").asDouble();
                long volume  = meta.path("regularMarketVolume").asLong();

                if (open  <= 0) open  = ltp;
                if (high  <= 0) high  = ltp;
                if (low   <= 0) low   = ltp;
                if (close <= 0) close = ltp;

                double change    = ltp - close;
                double changePct = close > 0 ? (change / close) * 100.0 : 0.0;

                StockPriceEvent event = buildStockEvent(
                        bseSymbol, getCompanyName(stockName, "BSE"), "BSE",
                        ltp, open, high, low, close, change, changePct, volume);
                publishStock(event);
                success++;

                Thread.sleep(200); // rate limit ke liye

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                failed++;
            }
        }

        log.info("📊 BSE: ✅ {} fetched, ❌ {} failed → WebSocket", success, failed);
        bseFailCount.set(success == 0 ? bseFailCount.incrementAndGet() : 0);
    }

    private StockPriceEvent buildStockEvent(String symbol, String name, String exchange,
                                            double ltp, double open, double high, double low,
                                            double close, double change, double changePct, long volume) {
        StockPriceEvent event = new StockPriceEvent();
        event.setSymbol(symbol);
        event.setName(name);
        event.setPrice(ltp);
        event.setOpen(open);
        event.setHigh(high);
        event.setLow(low);
        event.setPreviousClose(close);
        event.setChange(change);
        event.setChangePercent(changePct);
        event.setVolume(volume);
        event.setType("stock");
        event.setExchange(exchange);
        event.setCurrency("INR");
        event.setTimestamp(Instant.now());
        event.setMarketStatusAuto();
        event.setExpiresAt(Instant.now().plusSeconds(604_800));
        return event;
    }

    // ✅ Sirf WebSocket — NO Kafka
    private void publishStock(StockPriceEvent event) {
        try {
            wsController.broadcastStockPrice(event);
        } catch (Exception e) {
            log.error("❌ Publish failed for {}: {}", event.getSymbol(), e.getMessage());
        }
    }

    private String getCompanyName(String symbol, String exchange) {
        String key  = symbol.replace("-BSE", "");
        String name = COMPANY_NAMES.getOrDefault(key, key);
        return name + " (" + exchange + ")";
    }

    @PreDestroy
    public void stop() {
        log.info("🛑 StockYahooProducer stopped");
    }
}
