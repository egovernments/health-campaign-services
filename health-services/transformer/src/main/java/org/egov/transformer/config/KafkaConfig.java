package org.egov.transformer.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;

@Configuration
public class KafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactoryLow(
            ConsumerFactory<String, String> consumerFactory, TransformerProperties transformerProperties) {

        // copy existing properties
        Map<String, Object> props = new HashMap<>(consumerFactory.getConfigurationProperties());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, transformerProperties.getTransformerConsumerCustomFactoryTopicSize());

        // build a new consumer factory with overridden poll size
        ConsumerFactory<String, String> lowPollConsumerFactory =
                new org.springframework.kafka.core.DefaultKafkaConsumerFactory<>(
                        props,
                        consumerFactory.getKeyDeserializer(),
                        consumerFactory.getValueDeserializer()
                );

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConcurrency(transformerProperties.getTransformerConsumerCustomFactoryTopicConcurrency());
        factory.setConsumerFactory(lowPollConsumerFactory);

        return factory;
    }
}

