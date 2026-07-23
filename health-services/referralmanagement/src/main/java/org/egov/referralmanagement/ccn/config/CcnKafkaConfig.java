package org.egov.referralmanagement.ccn.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Dedicated Kafka listener factory for the CCN consumer.
 *
 * <p>The save-HFReferral topic carries a JSON ARRAY ({@code List<HFReferral>} from
 * GenericRepository.save). The service-wide value deserializer is
 * {@code HashMapDeserializer} (JsonDeserializer&lt;HashMap&gt;), which cannot deserialize an array.
 * So this factory uses a plain {@link StringDeserializer} and the consumer parses the JSON itself.
 * Isolated: it does not alter the global Kafka config or any other consumer.</p>
 */
@Configuration
public class CcnKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean(name = "ccnKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> ccnKafkaListenerContainerFactory(CcnProperties p) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, p.getConsumerGroup());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
        return factory;
    }
}
