package com.marketai.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "kafka")
public class KafkaProperties {

    private String bootstrapServers;
    private Consumer consumer;
    private Producer producer;
    private Map<String, String> topics;

    public static class Consumer {
        private String groupId;
        private String autoOffsetReset;

        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }

        public String getAutoOffsetReset() { return autoOffsetReset; }
        public void setAutoOffsetReset(String autoOffsetReset) { this.autoOffsetReset = autoOffsetReset; }
    }

    public static class Producer {
        private String acks;

        public String getAcks() { return acks; }
        public void setAcks(String acks) { this.acks = acks; }
    }

    // Getters & Setters
    public String getBootstrapServers() { return bootstrapServers; }
    public void setBootstrapServers(String bootstrapServers) { this.bootstrapServers = bootstrapServers; }

    public Consumer getConsumer() { return consumer; }
    public void setConsumer(Consumer consumer) { this.consumer = consumer; }

    public Producer getProducer() { return producer; }
    public void setProducer(Producer producer) { this.producer = producer; }

    public Map<String, String> getTopics() { return topics; }
    public void setTopics(Map<String, String> topics) { this.topics = topics; }
}
