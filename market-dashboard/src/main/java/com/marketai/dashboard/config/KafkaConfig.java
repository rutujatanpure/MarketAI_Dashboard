package com.marketai.dashboard.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.crypto-prices}")
    private String cryptoPricesTopic;

    @Value("${kafka.topics.stock-prices}")
    private String stockPricesTopic;

    @Value("${kafka.topics.ai-analysis}")
    private String aiAnalysisTopic;

    @Value("${kafka.topics.anomaly-alerts}")
    private String anomalyAlertsTopic;

    // ── Auto-create topics on startup ─────────────────────────────────────────

    @Bean
    public NewTopic cryptoPricesTopic() {
        return TopicBuilder.name(cryptoPricesTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic stockPricesTopic() {
        return TopicBuilder.name(stockPricesTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic aiAnalysisTopic() {
        return TopicBuilder.name(aiAnalysisTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic anomalyAlertsTopic() {
        return TopicBuilder.name(anomalyAlertsTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public StringJsonMessageConverter jsonMessageConverter() {
        return new StringJsonMessageConverter();
    }
}