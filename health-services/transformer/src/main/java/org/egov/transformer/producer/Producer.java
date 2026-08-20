package org.egov.transformer.producer;

import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.kafka.CustomKafkaTemplate;
import org.egov.transformer.config.TransformerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

// NOTE: If tracer is disabled change CustomKafkaTemplate to KafkaTemplate in autowiring

@Service
@Slf4j
public class Producer {

    private final CustomKafkaTemplate<String, Object> kafkaTemplate;

    private final TransformerProperties transformerProperties;

    @Autowired
    public Producer(CustomKafkaTemplate<String, Object> kafkaTemplate, TransformerProperties transformerProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.transformerProperties = transformerProperties;
    }

    public void push(String topic, Object value) {
        kafkaTemplate.send(topic, value);
    }

    /**
     * Pushes a transformed list as several messages of at most the configured batch size. A single source
     * record can fan out into many index records, and sending the whole list as one Kafka message risks
     * breaching max.request.size on the broker.
     */
    public void pushInBatches(String topic, List<?> records) {
        if (CollectionUtils.isEmpty(records)) {
            return;
        }
        int batchSize = transformerProperties.getProducerPushBatchSize();
        if (batchSize <= 0 || records.size() <= batchSize) {
            push(topic, records);
            return;
        }
        for (int startIndex = 0; startIndex < records.size(); startIndex += batchSize) {
            int endIndex = Math.min(startIndex + batchSize, records.size());
            // Copied rather than passing the subList view, so the message never aliases the caller's list.
            push(topic, new ArrayList<>(records.subList(startIndex, endIndex)));
        }
        log.info("Pushed {} records to topic {} in batches of {}", records.size(), topic, batchSize);
    }
}
