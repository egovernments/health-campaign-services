package org.egov.project.consumer;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.project.service.ProjectResourceService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ProjectResourceConsumer {

    private final ProjectResourceService service;
    private final ObjectMapper objectMapper;

    public ProjectResourceConsumer(ProjectResourceService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }


    @KafkaListener(topics = "${project.resource.consumer.bulk.create.topic}")
    public List<ProjectResource> bulkCreate(Map<String, Object> consumerRecord,
                                            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ProjectResourceBulkRequest request = objectMapper.convertValue(consumerRecord, ProjectResourceBulkRequest.class);
            return service.create(request, true);
        } catch (Exception exception) {
            log.error("error in project resource consumer bulk create", exception);
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${project.resource.consumer.bulk.update.topic}")
    public List<ProjectResource> bulkUpdate(Map<String, Object> consumerRecord,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ProjectResourceBulkRequest request = objectMapper.convertValue(consumerRecord, ProjectResourceBulkRequest.class);
            return service.update(request, true);
        } catch (Exception exception) {
            log.error("error in project resource consumer bulk update", exception);
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${project.resource.consumer.bulk.delete.topic}")
    public List<ProjectResource> bulkDelete(Map<String, Object> consumerRecord,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ProjectResourceBulkRequest request = objectMapper.convertValue(consumerRecord, ProjectResourceBulkRequest.class);
            return service.delete(request, true);
        } catch (Exception exception) {
            log.error("error in project resource consumer bulk delete", exception);
            return Collections.emptyList();
        }
    }
}
