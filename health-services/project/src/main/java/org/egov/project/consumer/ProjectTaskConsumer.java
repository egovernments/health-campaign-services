package org.egov.project.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.project.service.ProjectTaskService;
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
public class ProjectTaskConsumer {

    private final ProjectTaskService projectTaskService;

    private final ObjectMapper objectMapper;

    @Autowired
    public ProjectTaskConsumer(ProjectTaskService projectTaskService,
                              @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.projectTaskService = projectTaskService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${project.task.consumer.bulk.create.topic}")
    public List<Task> bulkCreate(Map<String, Object> consumerRecord,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            TaskBulkRequest request = objectMapper.convertValue(consumerRecord, TaskBulkRequest.class);
            return projectTaskService.create(request, true);
        }  catch (Exception exception) {
            log.error("error in project task consumer bulk create", exception);
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${project.task.consumer.bulk.update.topic}")
    public List<Task> bulkUpdate(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            TaskBulkRequest request = objectMapper.convertValue(consumerRecord, TaskBulkRequest.class);
            return projectTaskService.update(request, true);
        } catch (Exception exception) {
            log.error("error in project task consumer bulk update", exception);
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${project.task.consumer.bulk.delete.topic}")
    public List<Task> bulkDelete(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            TaskBulkRequest request = objectMapper.convertValue(consumerRecord, TaskBulkRequest.class);
            return projectTaskService.delete(request, true);
        } catch (Exception exception) {
            log.error("error in project task consumer bulk delete", exception);
            return Collections.emptyList();
        }
    }
}
