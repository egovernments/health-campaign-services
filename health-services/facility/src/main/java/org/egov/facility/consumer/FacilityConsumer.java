package org.egov.facility.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkRequest;
import org.egov.facility.service.FacilityService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class FacilityConsumer {

    private final FacilityService service;
    private final ObjectMapper objectMapper;

    public FacilityConsumer(FacilityService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${facility.consumer.bulk.create.topic}")
    public List<Facility> bulkCreate(Map<String, Object> consumerRecord,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            FacilityBulkRequest request = objectMapper.convertValue(consumerRecord, FacilityBulkRequest.class);
            return service.create(request, true);
        } catch (Exception exception) {
            log.error("error in facility consumer bulk create: {}", ExceptionUtils.getStackTrace(exception));
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${facility.consumer.bulk.update.topic}")
    public List<Facility> bulkUpdate(Map<String, Object> consumerRecord,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            FacilityBulkRequest request = objectMapper.convertValue(consumerRecord, FacilityBulkRequest.class);
            return service.update(request, true);
        } catch (Exception exception) {
            log.error("error in facility consumer bulk update: {}", ExceptionUtils.getStackTrace(exception));
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${facility.consumer.bulk.delete.topic}")
    public List<Facility> bulkDelete(Map<String, Object> consumerRecord,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            FacilityBulkRequest request = objectMapper.convertValue(consumerRecord, FacilityBulkRequest.class);
            return service.delete(request, true);
        } catch (Exception exception) {
            log.error("error in facility consumer bulk delete: {}", ExceptionUtils.getStackTrace(exception));
            return Collections.emptyList();
        }
    }
}
