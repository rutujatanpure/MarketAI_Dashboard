# 📈 MarketAI — Real-Time Crypto & Stock Intelligence Platform

<div align="center">

![Java](https://img.shields.io/badge/Java_17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React_18-20232A?style=for-the-badge&logo=react&logoColor=61DAFB)
![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-231F20?style=for-the-badge&logo=apachekafka&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-4EA94B?style=for-the-badge&logo=mongodb&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white)

A production-grade financial intelligence dashboard with live Binance WebSocket data, statistical anomaly detection, AI-powered market analysis, and real-time alerts.

[Features](#-features) • [Tech Stack](#-tech-stack) • [Quick Start](#-quick-start) • [API Docs](#-api-reference) • [Deployment](#-deployment)

</div>

---

## 🖥️ Screenshots

> Dark navy dashboard with real-time candlestick charts, technical indicators, risk scoring, and AI analysis.

```
Dashboard → Live prices | Candlestick chart | RSI/MACD/Bollinger | Risk Score | AI Commentary
Admin     → System health | Backtest results | User management | Prometheus metrics
Watchlist → Custom symbol tracking with alerts
```

---

## ✨ Features

### 📊 Real-Time Market Data
- **Live Binance WebSocket** — BTC, ETH, SOL, BNB, XRP tick data (no API key needed)
- **NSE/BSE Stocks** — RELIANCE, TCS, INFY, HDFCBANK, 30+ Indian stocks with realistic price simulation
- **Candlestick / Line / Depth charts** — TradingView Lightweight Charts
- **Multi-currency** — INR / USD / EUR conversion

### 🔬 Technical Analysis Engine
| Indicator | Description |
|-----------|-------------|
| RSI (14-period) | Overbought / Oversold signals |
| MACD (12/26/9) | Bullish / Bearish crossover |
| Bollinger Bands (20, 2σ) | Upper/Lower band touch alerts |
| ATR (14-period) | Volatility in absolute + % |
| **Z-Score Anomaly** | Rolling 100-pt window — flags Z > 2.5 deviations |
| Volume Analysis | Spike detection, pump/dump probability |

### 🛡️ Risk Intelligence
- **Composite Risk Score (0–100)** — Price (30%) + RSI (25%) + Volume (20%) + Z-Score (25%)
- **Pump & Dump Detection** — Phase labeling: Accumulation → Pump → Dump
- **Multi-Timeframe Confluence** — Signal confirmed only when 3 of 4 timeframes agree
- **Portfolio Risk API** — Aggregate risk across watchlist

### 🤖 AI Analysis (Gemini)
- Market commentary in natural language (gemma-3-27b-it)
- Short-term price prediction with confidence level
- 200 calls/day rate limit with graceful cached fallback

### 📉 Backtesting Engine
- 3 strategies: **Anomaly Detection**, **Risk Score**, **Confluence**
- Full P&L simulation — Sharpe Ratio, Win Rate, Max Drawdown, F1 Score
- Grade system: A+ / A / B / C / D
- Weekly auto-run every Sunday 2am

### 🔐 Auth & Alerts
- JWT authentication (HS256, 7-day expiry)
- Role-based access: `ROLE_USER` → Dashboard, `ROLE_ADMIN` → Admin panel
- Email alerts via Gmail SMTP on anomaly / high-risk events
- WebSocket push notifications

### 📡 Observability
- **Prometheus + Grafana** — 50+ custom Micrometer metrics
- **Spring Actuator** — `/actuator/health` shows MongoDB/Redis/Kafka status
- Custom counters: AI calls, anomaly detections, Kafka message rates

---

## 🛠️ Tech Stack

### Backend
| Technology | Version | Purpose |
|-----------|---------|---------|
| Java | 17 | Core language |
| Spring Boot | 3.x | Framework, REST API, DI |
| Spring Security | 6.x | JWT auth, role-based access |
| Apache Kafka | 3.x | Message broker — price event pipeline |
| Zookepper |
| Redis | 7 | Cache — sub-5ms price lookups |
| MongoDB | 7 (Atlas) | Primary database — documents store |
| Spring WebSocket (STOMP) | — | Real-time price push to browser |
| Gemini API | gemma-3-27b-it | AI market analysis |
| Spring Actuator + Micrometer | — | Prometheus metrics, health checks |
| Spring Mail | — | Gmail SMTP email alerts |
| Maven | 3.x | Build tool |

### Frontend
| Technology | Version | Purpose |
|-----------|---------|---------|
| React | 18 | UI framework |
| Vite | 5 | Build tool, HMR dev server |
| React Router | v6 | SPA routing |
| Axios | — | HTTP client + JWT interceptor |
| SockJS + STOMP.js | — | WebSocket client |
| Lightweight Charts (TradingView) | — | Candlestick / Line / Depth charts |
| Context API | — | Global auth state |

### Infrastructure
| Tool | Purpose |
|------|---------|
| Docker + Docker Compose | Local Kafka + Zookeeper + Redis |
| Railway | Cloud deployment (Spring Boot + Redis) |
| Vercel / Netlify | Frontend static hosting |
| MongoDB Atlas | Managed MongoDB (Free tier: 512MB) |
| Prometheus + Grafana | Metrics visualization |

---

## ⚡ Quick Start

### Prerequisites
- Java 17+
- Node.js 18+
- Docker Desktop
- Maven 3.8+

### 1. Clone the repo
```bash
git clone https://github.com/yourusername/marketai.git
cd marketai
```

### 2. Start infrastructure (Kafka + Redis)
```bash
docker-compose up -d
# Verify: docker ps should show kafka, zookeeper, redis running
```

### 3. Configure environment
```bash
# Copy the template
cp backend/src/main/resources/application-template.yml \
   backend/src/main/resources/application.yml

# Edit application.yml and fill in:
# - mongodb.uri (MongoDB Atlas connection string)
# - gemini.api-key
# - mail.username + mail.password (Gmail app password)
# - jwt.secret (any 32+ char string)
```

### 4. Start backend
```bash
cd backend
./mvnw clean spring-boot:run

# Expected output:
# ✅ Started MarketDashboardApplication in X seconds
# 🚀 Starting Binance live feed producer...
# ✅ Connected to Binance WebSocket — streaming BTC/ETH/SOL/BNB/XRP
# 🌱 StockDataSeeder complete — 35 seeded, 0 failed
```

### 5. Start frontend
```bash
cd frontend
npm install
npm run dev
# Opens at http://localhost:5173
```

### 6. Login
```
URL:      http://localhost:5173
Email:    admin@marketai.com   (create via /api/auth/register first)
Password: Admin123
```

---

## 📁 Project Structure

```
marketai/
├── backend/
│   ├── src/main/java/com/marketai/dashboard/
│   │   ├── config/
│   │   │   ├── AsyncConfig.java          # Thread pools for Kafka, backtest, AI
│   │   │   ├── KafkaConfig.java          # Topic creation, consumer groups
│   │   │   ├── RedisConfig.java          # Cache TTL configuration
│   │   │   ├── SecurityConfig.java       # JWT filter chain, CORS, role rules
│   │   │   └── WebSocketConfig.java      # STOMP endpoint, message broker
│   │   ├── controller/
│   │   │   ├── AuthController.java       # /api/auth/login, register
│   │   │   ├── TechnicalIndicatorController.java  # /api/indicators/*
│   │   │   ├── BacktestAndRiskController.java     # /api/risk/*, /api/confluence/*, /api/backtest/*
│   │   │   ├── AiController.java         # /api/ai/*
│   │   │   ├── MarketController.java     # /api/crypto/*, /api/stock/*
│   │   │   └── AdminController.java      # /api/admin/*
│   │   ├── consumer/
│   │   │   ├── CryptoPriceConsumer.java  # Reads crypto-prices-topic → analyze
│   │   │   └── StockPriceConsumer.java   # Reads stock-prices-topic → analyze
│   │   ├── producer/
│   │   │   ├── BinanceLiveFeedProducer.java  # WebSocket → Kafka publisher
│   │   │   └── StockPriceProducer.java        # NSE/BSE price publisher
│   │   ├── service/
│   │   │   ├── TechnicalIndicatorService.java  # RSI, MACD, BB, ATR, Z-Score
│   │   │   ├── SmartRiskEngine.java            # Composite risk scoring
│   │   │   ├── MultiTimeframeService.java      # 4-timeframe confluence
│   │   │   ├── BacktestingEngine.java          # Strategy backtesting
│   │   │   ├── AiAnalysisService.java          # Gemini API integration
│   │   │   ├── StockDataSeeder.java            # NSE/BSE seed on startup
│   │   │   └── PricePredictionService.java     # AI price forecasting
│   │   ├── model/                        # MongoDB documents
│   │   └── repository/                   # Spring Data MongoDB repos
│   └── src/main/resources/
│       └── application.yml               # ⚠️ NOT committed (see .gitignore)
│
├── frontend/
│   ├── src/
│   │   ├── pages/
│   │   │   ├── Dashboard.jsx             # Main trading dashboard
│   │   │   ├── Home.jsx                  # Landing page
│   │   │   ├── Login.jsx                 # Auth page
│   │   │   ├── AdminDashboard.jsx        # Admin panel
│   │   │   └── Watchlist.jsx             # User watchlist
│   │   ├── components/
│   │   │   ├── Navbar.jsx
│   │   │   ├── CandlestickChart.jsx
│   │   │   ├── CoinCard.jsx
│   │   │   ├── StockCard.jsx
│   │   │   └── WatchlistPanel.jsx
│   │   └── context/
│   │       └── AuthContext.jsx            # JWT token + user state
│   └── .env.local                        # ⚠️ NOT committed
│
├── docker-compose.yml                    # Kafka + Zookeeper + Redis
├── Dockerfile                            # Spring Boot container
├── .gitignore
└── README.md
```

---

## 🌊 Data Flow Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         DATA INGESTION                          │
│                                                                 │
│  Binance WebSocket ──────► BinanceLiveFeedProducer              │
│  (BTC/ETH/SOL/BNB/XRP)           │                             │
│                                   │  crypto-prices-topic        │
│  StockDataSeeder ─────────────────►         KAFKA               │
│  (NSE/BSE stocks, 5 min)          │  stock-prices-topic         │
└───────────────────────────────────┼─────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                        PROCESSING ENGINE                        │
│                                                                 │
│  CryptoPriceConsumer ──► TechnicalIndicatorService              │
│  StockPriceConsumer  ──►   ├── RSI / MACD / Bollinger / ATR     │
│                             ├── Z-Score Anomaly Detection       │
│                             ├── Volume Spike / Pump-Dump        │
│                             └── Composite Risk Score            │
│                                         │                       │
│                             SmartRiskEngine                     │
│                             MultiTimeframeService               │
│                             BacktestingEngine (async)           │
│                             AiAnalysisService (async)           │
└─────────────────────────────┬───────────────────────────────────┘
                               │
                    ┌──────────┴──────────┐
                    │                     │
                    ▼                     ▼
              MongoDB Atlas           Redis Cache
              (persist all)        (latest 5s TTL)
                    │                     │
                    └──────────┬──────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                           API LAYER                             │
│                                                                 │
│  REST  /api/indicators/latest?symbol=BTCUSDT                    │
│        /api/risk/latest?symbol=RELIANCE                         │
│        /api/confluence/latest?symbol=ETHUSDT                    │
│        /api/ai/latest?symbol=BTCUSDT                            │
│        /api/backtest/best?symbol=SOLUSDT                        │
│                                                                 │
│  WS    /topic/prices/{symbol}   ← live price push               │
│        /topic/alerts/{symbol}   ← anomaly/risk alerts           │
└─────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                        REACT FRONTEND                           │
│                                                                 │
│  Dashboard.jsx                                                  │
│  ├── Polls REST every 30s (indicators, risk, confluence, ai)    │
│  ├── Subscribes WebSocket /topic/prices/{symbol}                │
│  └── TradingView Lightweight Charts for candlesticks            │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔌 API Reference

> All endpoints (except `/api/auth/*`) require `Authorization: Bearer <token>` header.

### Authentication
```http
POST /api/auth/register
POST /api/auth/login
```

### Technical Indicators
```http
GET /api/indicators/latest?symbol=BTCUSDT
GET /api/indicators/summary?symbol=RELIANCE
GET /api/indicators/history?symbol=ETHUSDT
GET /api/indicators/anomalies/recent
GET /api/indicators/high-risk?minScore=70
GET /api/indicators/pump-dump
GET /api/indicators/volume-spikes
```

### Risk & Confluence
```http
GET /api/risk/latest?symbol=BTCUSDT
GET /api/risk/high-risk?minScore=75
GET /api/risk/portfolio?symbols=BTCUSDT,ETHUSDT,RELIANCE
GET /api/confluence/latest?symbol=BTCUSDT
```

### AI Analysis
```http
GET /api/ai/latest?symbol=BTCUSDT       # last cached result
GET /api/ai/analyze?symbol=BTCUSDT      # trigger fresh analysis
```

### Backtesting
```http
POST /api/backtest/run?symbol=BTCUSDT&strategy=ANOMALY_DETECTION&days=90
GET  /api/backtest/results?symbol=BTCUSDT
GET  /api/backtest/best?symbol=BTCUSDT
GET  /api/backtest/system-accuracy
```

### Market Data
```http
GET /api/crypto/prices
GET /api/stock/prices
GET /api/market/history?symbol=BTCUSDT
```

### Admin (ROLE_ADMIN only)
```http
GET  /api/admin/stats
GET  /api/admin/users
POST /api/admin/backtest/run-all
```

### Observability
```http
GET /actuator/health     # MongoDB + Redis + Kafka status
GET /actuator/prometheus # All metrics for Grafana scraping
GET /actuator/metrics    # Available metric names
```

---

## 🔑 Environment Variables

Create `backend/src/main/resources/application.yml`:

```yaml
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/marketai}
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
  mail:
    username: ${MAIL_USERNAME:your@gmail.com}
    password: ${MAIL_PASSWORD:your-app-password}

jwt:
  secret: ${JWT_SECRET:YourSecretKeyMin32CharactersLong}
  expiration: 604800000

gemini:
  api-key: ${GEMINI_API_KEY:your-gemini-api-key}
  model: gemma-3-27b-it
  daily-limit: 200
```

Create `frontend/.env.local`:
```env
VITE_API_URL=http://localhost:8080
VITE_WS_URL=ws://localhost:8080/ws
```

---

## 🐳 Docker Compose (Local Dev)

```yaml
# docker-compose.yml
version: '3.8'
services:

  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    depends_on: [zookeeper]
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --maxmemory 100mb --maxmemory-policy allkeys-lru
```

```bash
docker-compose up -d      # start
docker-compose down       # stop
docker-compose logs kafka # check kafka logs
```

---

## ☁️ Deployment

### Backend — Railway

1. Push code to GitHub
2. Railway → New Project → Deploy from GitHub repo
3. Add **Redis** service: New → Database → Redis
4. Set environment variables in Railway dashboard:

```
MONGODB_URI        = mongodb+srv://...your-atlas-uri...
REDIS_HOST         = ${{Redis.RAILWAY_PRIVATE_DOMAIN}}
REDIS_PORT         = 6379
KAFKA_BOOTSTRAP    = your-upstash-kafka:9092
JWT_SECRET         = YourSecretKeyHere
GEMINI_API_KEY     = AIzaSy...
MAIL_USERNAME      = your@gmail.com
MAIL_PASSWORD      = xxxx xxxx xxxx xxxx
```

5. Add `Dockerfile` to root:

```dockerfile
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx400m", "-jar", "app.jar"]
```

6. Railway auto-deploys on every push to `main`

### Frontend — Vercel

```bash
cd frontend
npm run build           # creates dist/ folder

# Vercel CLI
npx vercel --prod

# Or connect GitHub repo to vercel.com
# Set env variable: VITE_API_URL = https://your-app.up.railway.app
```

---

## 🗄️ MongoDB Storage Management

> **Important:** MongoDB Atlas free tier = 512 MB. Without TTL indexes, this fills up within days.

### Add TTL Indexes (run once on startup)

Add to `AppStartupRunner.java`:
```java
// market_prices — auto-delete after 7 days
mongoTemplate.indexOps("market_prices")
    .ensureIndex(new Index("timestamp", Sort.Direction.ASC)
    .expire(7, TimeUnit.DAYS));

// technical_indicators — auto-delete after 3 days
mongoTemplate.indexOps("technical_indicators")
    .ensureIndex(new Index("timestamp", Sort.Direction.ASC)
    .expire(3, TimeUnit.DAYS));
```

### Throttle Kafka Saves

In `StockPriceConsumer.java` — save 1 in every 10 events:
```java
private final AtomicInteger counter = new AtomicInteger(0);

if (counter.incrementAndGet() % 10 == 0) {
    marketPriceRepository.save(event);
    counter.set(0);
}
// Always run analysis (in-memory — no DB write)
technicalIndicatorService.analyze(symbol, event, history);
```

### Manual Cleanup (if quota exceeded)

Go to [cloud.mongodb.com](https://cloud.mongodb.com) → Browse Collections → Drop:
1. `market_prices` (largest)
2. `technical_indicators`
3. `backtest_results`

---

## 🧪 Testing

### Manual API Testing

```bash
# 1. Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test User","email":"test@test.com","password":"Test123"}'

# 2. Login → copy the token from response
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"Test123"}'

# 3. Use token
TOKEN="eyJhbGci..."

curl http://localhost:8080/api/indicators/latest?symbol=BTCUSDT \
  -H "Authorization: Bearer $TOKEN"

curl http://localhost:8080/api/risk/latest?symbol=RELIANCE \
  -H "Authorization: Bearer $TOKEN"

curl http://localhost:8080/api/confluence/latest?symbol=ETHUSDT \
  -H "Authorization: Bearer $TOKEN"
```

### Verify Kafka is Working
```bash
# Watch messages flowing
docker exec -it <kafka-container> \
  kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic crypto-prices-topic \
  --from-beginning
```

### Verify Redis Caching
```bash
docker exec -it <redis-container> redis-cli
> KEYS *              # list all cached keys
> GET price:BTCUSDT  # get cached price
> TTL price:BTCUSDT  # check expiry (< 5 seconds)
```

### Health Check
```bash
curl http://localhost:8080/actuator/health | python3 -m json.tool
# Expected: {"status":"UP","components":{"mongo":{"status":"UP"},"redis":{"status":"UP"}}}
```

---

## 📊 Kafka Topics

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `crypto-prices-topic` | BinanceLiveFeedProducer | CryptoPriceConsumer | Live BTC/ETH/SOL/BNB/XRP prices |
| `stock-prices-topic` | StockDataSeeder | StockPriceConsumer | NSE/BSE stock prices |
| `ai-analysis-topic` | AiAnalysisService | — | AI result events |
| `anomaly-alerts-topic` | TechnicalIndicatorService | NotificationService | High Z-Score alerts |

---

## 🔒 Security Notes

- **Never commit** `application.yml` — it contains DB credentials, API keys, JWT secret
- **Never commit** `frontend/.env.local` — contains API URLs
- JWT secret must be **32+ characters** for HS256
- Gmail: use **App Password** (not your real password) — generate at myaccount.google.com/apppasswords
- Gemini API key: restrict to your IP in Google Cloud Console for production
- MongoDB Atlas: whitelist only Railway's IP range in production (not 0.0.0.0/0)

---

## 🤝 Contributing

```bash
# Create feature branch
git checkout -b feature/your-feature-name

# Make changes, then
git add .
git commit -m "feat: add your feature description"
git push origin feature/your-feature-name

# Open Pull Request on GitHub
```

---

## 📄 License

MIT License — see [LICENSE](LICENSE) file for details.

---

<div align="center">

Built with ☕ Java + ⚛️ React | Spring Boot 3 · Kafka · Redis · MongoDB · Gemini AI

</div>
