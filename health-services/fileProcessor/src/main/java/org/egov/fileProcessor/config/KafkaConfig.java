package org.egov.fileProcessor.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@EnableKafka
@Configuration
public class KafkaConfig {
    @Value("${bootstrap-server}")
    private String bootstrap_server;
    @Value("${kafka-groupId}")
    private String groupId;
    @Bean
    public ConsumerFactory<String, String> consumerFactory()
     {
         Map<String, Object> config = new HashMap<>();


         config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap_server);
         config.put(ConsumerConfig.GROUP_ID_CONFIG,groupId);
         config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
         config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

         return new DefaultKafkaConsumerFactory<>(config);
     }


     @Bean
     public ConcurrentKafkaListenerContainerFactory concurrentKafkaListenerContainerFactory()
     {
         ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
         factory.setConsumerFactory(consumerFactory());
         return factory;
     }
}
