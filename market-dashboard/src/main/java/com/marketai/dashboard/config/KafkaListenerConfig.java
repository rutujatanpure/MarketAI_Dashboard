package com.marketai.dashboard.config;

import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaListenerConfig.class);

    // ✅ Direct @Value injection — KafkaProperties null issue bypass
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:market-group}")
    private String groupId;

    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    @Value("${spring.kafka.properties.security.protocol:PLAINTEXT}")
    private String securityProtocol;

    @Value("${spring.kafka.properties.ssl.ca.cert:}")
    private String caCert;

    @Value("${spring.kafka.properties.ssl.access.cert:}")
    private String accessCert;

    @Value("${spring.kafka.properties.ssl.access.key:}")
    private String accessKey;

    @PostConstruct
    public void init() {
        log.info("📡 Kafka Config:");
        log.info("   Bootstrap Servers : {}", bootstrapServers);
        log.info("   Consumer Group    : {}", groupId);
        log.info("   Auto Offset Reset : {}", autoOffsetReset);
        log.info("   Security Protocol : {}", securityProtocol);
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

        // ✅ SSL config for Aiven Kafka
        if ("SSL".equalsIgnoreCase(securityProtocol)) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
            props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PEM");
            props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PEM");

            if (!caCert.isBlank()) {
                props.put(SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG, caCert.trim());
            }
            if (!accessCert.isBlank()) {
                props.put(SslConfigs.SSL_KEYSTORE_CERTIFICATE_CHAIN_CONFIG, accessCert.trim());
            }
            if (!accessKey.isBlank()) {
                props.put(SslConfigs.SSL_KEYSTORE_KEY_CONFIG, accessKey.trim());
            }
            log.info("   SSL              : ENABLED (Aiven mTLS)");
        } else {
            log.info("   SSL              : DISABLED");
        }

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
    kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setConcurrency(3);
        factory.setCommonErrorHandler(
                new DefaultErrorHandler(new FixedBackOff(2000L, 3)));
        return factory;
    }
}
