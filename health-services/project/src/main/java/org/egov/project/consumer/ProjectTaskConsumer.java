package org.egov.project.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.project.service.ProjectTaskService;
import org.egov.project.web.models.Task;
import org.egov.project.web.models.TaskBulkRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

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
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
        TaskBulkRequest request = objectMapper.convertValue(consumerRecord, TaskBulkRequest.class);
        return projectTaskService.create(request, true);
    }

    @KafkaListener(topics = "${project.task.consumer.bulk.update.topic}")
    public List<Task> bulkUpdate(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        TaskBulkRequest request = objectMapper.convertValue(consumerRecord, TaskBulkRequest.class);
        return projectTaskService.update(request, true);
    }

    @KafkaListener(topics = "${project.task.consumer.bulk.delete.topic}")
    public List<Task> bulkDelete(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        TaskBulkRequest request = objectMapper.convertValue(consumerRecord, TaskBulkRequest.class);
        return projectTaskService.delete(request, true);
    }
}
