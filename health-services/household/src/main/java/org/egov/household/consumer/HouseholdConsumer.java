package org.egov.household.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.pii.BulkEnvelopeService;
import org.egov.household.service.HouseholdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class HouseholdConsumer {

    private final HouseholdService householdService;

    private final BulkEnvelopeService bulkEnvelopeService;

    @Autowired
    public HouseholdConsumer(HouseholdService householdService,
                             BulkEnvelopeService bulkEnvelopeService) {
        this.householdService = householdService;
        this.bulkEnvelopeService = bulkEnvelopeService;
    }

    /**
     * Bulk messages parked while enc-service was down (ENCRYPTED or PLAIN, see payloadType).
     * One message per poll: the replay may wait for enc-service, and a poll of many messages would
     * then run past max.poll.interval.ms, so Kafka would hand the same messages out again.
     */
    @KafkaListener(topics = "${egov.bulk.dlq.topic}",  // the topic messages are parked to (mapped from household.bulk.dlq.topic)
            properties = "max.poll.records=1")
    public void replayBulkDlq(Map<String, Object> consumerRecord) {
        bulkEnvelopeService.replayDlq(consumerRecord);
    }

    @KafkaListener(topics = "${household.consumer.bulk.create.topic}")
    public List<Household> bulkCreate(Map<String, Object> consumerRecord,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            HouseholdBulkRequest request = bulkEnvelopeService.open(consumerRecord, HouseholdBulkRequest.class);
            return householdService.createFromQueue(request);
        } catch (Exception exception) {
            if (bulkEnvelopeService.parkIfEncFailed(topic, consumerRecord, exception)) {
                return Collections.emptyList();
            }
            log.error("error in household consumer bulk create: {}", ExceptionUtils.getStackTrace(exception));
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${household.consumer.bulk.update.topic}")
    public List<Household> bulkUpdate(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            HouseholdBulkRequest request = bulkEnvelopeService.open(consumerRecord, HouseholdBulkRequest.class);
            return householdService.updateFromQueue(request);
        } catch (Exception exception) {
            if (bulkEnvelopeService.parkIfEncFailed(topic, consumerRecord, exception)) {
                return Collections.emptyList();
            }
            log.error("error in household consumer bulk update: {}", ExceptionUtils.getStackTrace(exception));
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${household.consumer.bulk.delete.topic}")
    public List<Household> bulkDelete(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            HouseholdBulkRequest request = bulkEnvelopeService.open(consumerRecord, HouseholdBulkRequest.class);
            return householdService.deleteFromQueue(request);
        } catch (Exception exception) {
            if (bulkEnvelopeService.parkIfEncFailed(topic, consumerRecord, exception)) {
                return Collections.emptyList();
            }
            log.error("error in household consumer bulk delete: {}", ExceptionUtils.getStackTrace(exception));
            return Collections.emptyList();
        }
    }
}
