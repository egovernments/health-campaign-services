package org.egov.individual.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.pii.BulkEnvelopeService;
import org.egov.individual.service.IndividualService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class IndividualConsumer {

    private final IndividualService individualService;

    private final ObjectMapper objectMapper;

    private final BulkEnvelopeService bulkEnvelopeService;

    @Autowired
    public IndividualConsumer(IndividualService individualService,
                              @Qualifier("objectMapper") ObjectMapper objectMapper,
                              BulkEnvelopeService bulkEnvelopeService) {
        this.individualService = individualService;
        this.objectMapper = objectMapper;
        this.bulkEnvelopeService = bulkEnvelopeService;
    }

    /**
     * Bulk messages parked while enc-service was down (ENCRYPTED or PLAIN, see payloadType).
     * One message per poll: the replay may wait for enc-service, and a poll of many messages would
     * then run past max.poll.interval.ms, so Kafka would hand the same messages out again.
     */
    @KafkaListener(topics = "${egov.bulk.dlq.topic}",  // the topic messages are parked to (mapped from individual.bulk.dlq.topic)
            properties = "max.poll.records=1")
    public void replayBulkDlq(Map<String, Object> consumerRecord) {
        bulkEnvelopeService.replayDlq(consumerRecord);
    }

    @KafkaListener(topics = "${individual.consumer.bulk.create.topic}")
    public List<Individual> bulkCreate(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            IndividualBulkRequest request = bulkEnvelopeService.open(consumerRecord, IndividualBulkRequest.class);
            return individualService.createFromQueue(request);
        } catch (Exception exception) {
            if (bulkEnvelopeService.parkIfEncFailed(topic, consumerRecord, exception)) {
                return Collections.emptyList();
            }
            log.error("error in individual consumer bulk create", exception);
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${individual.consumer.bulk.update.topic}")
    public List<Individual> bulkUpdate(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            IndividualBulkRequest request = bulkEnvelopeService.open(consumerRecord, IndividualBulkRequest.class);
            return individualService.updateFromQueue(request);
        } catch (Exception exception) {
            if (bulkEnvelopeService.parkIfEncFailed(topic, consumerRecord, exception)) {
                return Collections.emptyList();
            }
            log.error("error in individual consumer bulk update", exception);
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${individual.consumer.bulk.delete.topic}")
    public List<Individual> bulkDelete(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            IndividualBulkRequest request = bulkEnvelopeService.open(consumerRecord, IndividualBulkRequest.class);
            return individualService.deleteFromQueue(request);
        } catch (Exception exception) {
            if (bulkEnvelopeService.parkIfEncFailed(topic, consumerRecord, exception)) {
                return Collections.emptyList();
            }
            log.error("error in individual consumer bulk delete", exception);
            return Collections.emptyList();
        }
    }
}
