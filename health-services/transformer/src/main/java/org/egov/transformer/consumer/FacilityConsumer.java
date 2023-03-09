package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.handler.TransformationHandler;
import org.egov.transformer.models.upstream.Facility;
import org.egov.transformer.models.upstream.Facility;
import org.egov.transformer.models.upstream.FacilityRequest;
import org.egov.transformer.service.FacilityService;
import org.egov.transformer.service.FacilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class FacilityConsumer {
    private final TransformationHandler<Facility> transformationHandler;

    private final ObjectMapper objectMapper;

    private final FacilityService facilityService;

    @Autowired
    public FacilityConsumer(TransformationHandler<Facility> transformationHandler,
                           @Qualifier("objectMapper") ObjectMapper objectMapper, FacilityService facilityService) {
        this.transformationHandler = transformationHandler;
        this.objectMapper = objectMapper;
        this.facilityService = facilityService;
    }

    @KafkaListener(topics = { "${transformer.consumer.create.facility.topic}",
            "${transformer.consumer.update.facility.topic}"})
    public void consumeFacilitys(ConsumerRecord<String, Object> payload,
                                @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            FacilityRequest request = objectMapper
                    .readValue((String) payload.value(),
                            FacilityRequest.class);
            facilityService.updateFacilitiesInCache(request);
            transformationHandler.handle(request.getFacilities(), Operation.PROJECT);
        } catch (Exception exception) {
            log.error("error in facility consumer", exception);
        }
    }
}
