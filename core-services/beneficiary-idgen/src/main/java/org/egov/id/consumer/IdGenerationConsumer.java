package org.egov.id.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.idgen.IDPoolGenerationKafkaRequest;
import org.egov.common.models.idgen.IdRecord;
import org.egov.common.models.idgen.IdRecordBulkRequest;
import org.egov.id.service.IdDispatchService;
import org.egov.id.service.IdGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Kafka consumer class for handling bulk ID generation updates.
 * Listens to configured Kafka topic and processes incoming messages.
 */
@Component
@Slf4j
public class IdGenerationConsumer {

    private final IdDispatchService idDispatchService;

    private final IdGenerationService idGenerationService;

    private final ObjectMapper objectMapper;

    /**
     * Constructor to inject required services and object mapper.
     *
     * @param idDispatchService Service for dispatching IDs
     * @param objectMapper Jackson ObjectMapper for converting Kafka message payloads
     */
    @Autowired
    public IdGenerationConsumer(IdDispatchService idDispatchService, IdGenerationService idGenerationService, @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.idDispatchService =  idDispatchService;
        this.idGenerationService = idGenerationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Kafka listener method for bulk update topic.
     * Converts incoming Kafka record to IdRecordBulkRequest and invokes dispatch update service.
     *
     * @param consumerRecord The message payload as a Map
     * @param topic Kafka topic name from which the message was received
     * @return List of updated IdRecords or empty list in case of exception
     */
    @KafkaListener(topics = "${kafka.topics.consumer.bulk.update.topic}")
    public List<IdRecord> bulkUpdate(Map<String, Object> consumerRecord,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            IdRecordBulkRequest request = objectMapper.convertValue(consumerRecord, IdRecordBulkRequest.class);
            return idDispatchService.update(request, true);
        } catch (Exception exception) {
            log.error("error in individual consumer bulk update", exception);
            return Collections.emptyList();
        }
    }

    /**
     * Kafka listener method for handling asynchronous ID pool creation requests.
     * Converts incoming Kafka message to IDPoolGenerationKafkaRequest and processes it.
     *
     * @param message The message payload as a Map
     */
    @KafkaListener(topics = "${kafka.topics.consumer.bulk.create.topic}")
    public void consumeIDPoolCreationAsyncRequest(Map<String, Object> message) {
        try {
            IDPoolGenerationKafkaRequest request = new ObjectMapper().convertValue(message, IDPoolGenerationKafkaRequest.class);
            idGenerationService.handleAsyncIdPoolRequest(request);
        } catch (Exception e) {
            log.error("Failed to process async ID pool generation", e);
        }
    }

}
