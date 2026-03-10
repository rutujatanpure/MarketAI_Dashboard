package com.marketai.dashboard.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    @PostConstruct
    public void init() {
        log.info("🔌 WebSocket Config:");
        log.info("   Endpoint        : /ws (SockJS enabled)");
        log.info("   Broker Topics   : /topic, /queue");
        log.info("   App Prefix      : /app");
        log.info("   CORS            : All origins allowed (*)");
        log.info("   ── Crypto Subscriptions ──────────────");
        log.info("     💰 Prices     → /topic/prices/BTCUSDT");
        log.info("     💰 Prices     → /topic/prices/ETHUSDT");
        log.info("     💰 Prices     → /topic/prices/BNBUSDT");
        log.info("     💰 Prices     → /topic/prices/SOLUSDT");
        log.info("     💰 Prices     → /topic/prices/XRPUSDT");
        log.info("     🤖 Analysis   → /topic/analysis/{{symbol}}");
        log.info("   ── Stock Subscriptions ───────────────");
        log.info("     📈 Stocks     → /topic/stocks/AAPL");
        log.info("     📈 Stocks     → /topic/stocks/GOOGL");
        log.info("     📈 Stocks     → /topic/stocks/TSLA");
        log.info("     📈 Stocks     → /topic/stocks/MSFT");
        log.info("     📈 Stocks     → /topic/stocks/NVDA");
        log.info("     📈 All Stocks → /topic/stocks");
        log.info("   ── Alert Subscriptions ───────────────");
        log.info("     🚨 Old Alerts → /topic/alerts");
        log.info("     🚨 Old Alerts → /topic/alerts/{{symbol}}");
        log.info("     🆕 New Alerts → /topic/alerts/all   (all symbols)");
        log.info("     🆕 New Alerts → /topic/alerts/{{symbol}} (same topic)");
        log.info("   ── NEW: Indicator Subscriptions ──────");
        log.info("     📊 Indicators → /topic/indicators/{{symbol}}");
        log.info("   ── Prediction Subscriptions ──────────");
        log.info("     🔮 Prediction → /topic/prediction/{{symbol}}");
        log.info("   Status          : ✅ Running");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}