package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.facility.Facility;
import org.egov.transformer.service.FacilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;


@Component
@Slf4j
public class FacilityConsumer {
    private final ObjectMapper objectMapper;

    private final FacilityService facilityService;

    @Autowired
    public FacilityConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, FacilityService facilityService) {
        this.objectMapper = objectMapper;
        this.facilityService = facilityService;
    }

    @KafkaListener(topics = { "${transformer.consumer.create.facility.topic}",
            "${transformer.consumer.update.facility.topic}"})
    public void consumeFacilities(ConsumerRecord<String, Object> payload,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<Facility> facilities = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            Facility[].class));
            facilityService.updateFacilitiesInCache(facilities);
        } catch (Exception exception) {
            log.error("error in facility consumer", ExceptionUtils.getStackTrace(exception));
        }
    }
}
