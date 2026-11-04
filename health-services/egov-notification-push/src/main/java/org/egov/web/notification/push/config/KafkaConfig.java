package org.egov.web.notification.push.config;

import org.apache.kafka.common.TopicPartition;
import org.egov.web.notification.push.service.PushMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class KafkaConfig {

    @Autowired
    private PushProperties properties;

    @Bean
    public DefaultErrorHandler pushKafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate, PushMetrics metrics) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, (record, ex) -> {
            String prefix = properties.statePrefix(record.topic());
            String topic = prefix + properties.getDeadLetterTopic();
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            metrics.deadLettered(prefix.endsWith("-") ? prefix.substring(0, prefix.length() - 1) : prefix,
                    cause.getClass().getSimpleName(), 1);
            log.error("PUSH_DEADLETTER sourceTopic={} offset={} deadLetterTopic={} reason={}",
                    record.topic(), record.offset(), topic, ex.getMessage());
            return new TopicPartition(topic, -1);
        });
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(properties.getKafkaErrorRetryIntervalMs(), properties.getKafkaErrorRetryAttempts()));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler pushKafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(pushKafkaErrorHandler);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> retryListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler pushKafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(pushKafkaErrorHandler);
        return factory;
    }

}
