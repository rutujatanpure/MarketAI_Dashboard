package com.marketai.dashboard.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

@Configuration
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public NewTopic cryptoPricesTopic() {
        return TopicBuilder.name(kafkaProperties.getTopics().get("crypto-prices"))
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic stockPricesTopic() {
        return TopicBuilder.name(kafkaProperties.getTopics().get("stock-prices"))
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic aiAnalysisTopic() {
        return TopicBuilder.name(kafkaProperties.getTopics().get("ai-analysis"))
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic anomalyAlertsTopic() {
        return TopicBuilder.name(kafkaProperties.getTopics().get("anomaly-alerts"))
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public StringJsonMessageConverter jsonMessageConverter() {
        return new StringJsonMessageConverter();
    }
}
