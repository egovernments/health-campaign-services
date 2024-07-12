package org.egov.project.consumer;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskAction;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.project.service.ClosedHouseholdTaskService;
import org.egov.project.service.TrackActivityTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class IRSConsumer {

    private final ClosedHouseholdTaskService closedHouseholdTaskService;

    private final TrackActivityTaskService trackActivityTaskService;

    private final ObjectMapper objectMapper;

    @Autowired
    public IRSConsumer(ClosedHouseholdTaskService closedHouseholdTaskService, TrackActivityTaskService trackActivityTaskService, ObjectMapper objectMapper) {
        this.closedHouseholdTaskService = closedHouseholdTaskService;
        this.trackActivityTaskService = trackActivityTaskService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${project.task.closed.household.consumer.bulk.create.topic}, ${project.task.track.activity.consumer.bulk.create.topic}")
    public List<Task> bulkCreate(Map<String, Object> consumerRecord,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            TaskBulkRequest request = objectMapper.convertValue(consumerRecord, TaskBulkRequest.class);
            return request.getTasks().stream().anyMatch(task -> task.getAction() == TaskAction.CLOSED_HOUSEHOLD) ?
                    closedHouseholdTaskService.create(request, true) : trackActivityTaskService.create(request, true);
        }  catch (Exception exception) {
            log.error("error in project task consumer bulk create", ExceptionUtils.getStackTrace(exception));
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${project.task.closed.household.consumer.bulk.update.topic}, ${project.task.track.activity.consumer.bulk.update.topic}")
    public List<Task> bulkUpdate(Map<String, Object> consumerRecord,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            TaskBulkRequest request = objectMapper.convertValue(consumerRecord, TaskBulkRequest.class);
            return request.getTasks().stream().anyMatch(task -> task.getAction() == TaskAction.CLOSED_HOUSEHOLD) ?
                    closedHouseholdTaskService.update(request, true) : trackActivityTaskService.update(request, true);
        } catch (Exception exception) {
            log.error("error in project task consumer bulk update", ExceptionUtils.getStackTrace(exception));
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${project.task.closed.household.consumer.bulk.delete.topic}, ${project.task.track.activity.consumer.bulk.delete.topic}")
    public List<Task> bulkDelete(Map<String, Object> consumerRecord,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            TaskBulkRequest request = objectMapper.convertValue(consumerRecord, TaskBulkRequest.class);
            return request.getTasks().stream().anyMatch(task -> task.getAction() == TaskAction.CLOSED_HOUSEHOLD) ?
                    closedHouseholdTaskService.delete(request, true) : trackActivityTaskService.delete(request, true);
        } catch (Exception exception) {
            log.error("error in project task consumer bulk delete", ExceptionUtils.getStackTrace(exception));
            return Collections.emptyList();
        }
    }
}
