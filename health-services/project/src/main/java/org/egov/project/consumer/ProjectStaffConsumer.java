package org.egov.project.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffBulkRequest;
import org.egov.project.service.ProjectStaffService;
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
public class ProjectStaffConsumer {

    private final ProjectStaffService service;
    private final ObjectMapper objectMapper;

    public ProjectStaffConsumer(ProjectStaffService service, @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${project.staff.consumer.bulk.create.topic}")
    public List<ProjectStaff> bulkCreate(Map<String, Object> consumerRecord,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ProjectStaffBulkRequest request = objectMapper.convertValue(consumerRecord, ProjectStaffBulkRequest.class);
            return service.create(request, true);
        } catch (Exception exception) {
            log.error("error in project staff consumer bulk create", exception);
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${project.staff.consumer.bulk.update.topic}")
    public List<ProjectStaff> bulkUpdate(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ProjectStaffBulkRequest request = objectMapper.convertValue(consumerRecord, ProjectStaffBulkRequest.class);
            return service.update(request, true);
        } catch (Exception exception) {
            log.error("error in project staff consumer bulk update", exception);
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${project.staff.consumer.bulk.delete.topic}")
    public List<ProjectStaff> bulkDelete(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ProjectStaffBulkRequest request = objectMapper.convertValue(consumerRecord, ProjectStaffBulkRequest.class);
            return service.delete(request, true);
        } catch (Exception exception) {
            log.error("error in project staff consumer bulk delete", exception);
            return Collections.emptyList();
        }
    }

}
