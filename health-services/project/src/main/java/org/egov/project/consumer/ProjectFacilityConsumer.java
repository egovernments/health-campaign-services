package org.egov.project.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.project.service.ProjectFacilityService;
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
public class ProjectFacilityConsumer {

    private final ProjectFacilityService service;
    private final ObjectMapper objectMapper;

    public ProjectFacilityConsumer(ProjectFacilityService service, @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${project.facility.consumer.bulk.create.topic}")
    public List<ProjectFacility> bulkCreate(Map<String, Object> consumerRecord,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ProjectFacilityBulkRequest request = objectMapper.convertValue(consumerRecord, ProjectFacilityBulkRequest.class);
            return service.create(request, true);
        } catch (Exception exception) {
            log.error("error in project facility consumer bulk create", exception);
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${project.facility.consumer.bulk.update.topic}")
    public List<ProjectFacility> bulkUpdate(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ProjectFacilityBulkRequest request = objectMapper.convertValue(consumerRecord, ProjectFacilityBulkRequest.class);
            return service.update(request, true);
        } catch (Exception exception) {
            log.error("error in project facility consumer bulk update", exception);
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${project.facility.consumer.bulk.delete.topic}")
    public List<ProjectFacility> bulkDelete(Map<String, Object> consumerRecord,
                                            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ProjectFacilityBulkRequest request = objectMapper.convertValue(consumerRecord, ProjectFacilityBulkRequest.class);
            return service.delete(request, true);
        } catch (Exception exception) {
            log.error("error in project facility consumer bulk delete", exception);
            return Collections.emptyList();
        }
    }

}
