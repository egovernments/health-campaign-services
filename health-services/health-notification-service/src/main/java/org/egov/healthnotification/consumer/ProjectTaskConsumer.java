package org.egov.healthnotification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskStatus;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.service.PostDistributionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Kafka consumer for Project Task events.
 * Listens to task creation topic and triggers notification scheduling
 * based on the task details (e.g., stock distribution events).
 */
@Component
@Slf4j
public class ProjectTaskConsumer {

    private final PostDistributionService postDistributionService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProjectTaskConsumer(PostDistributionService postDistributionService,
                               @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.postDistributionService = postDistributionService;
        this.objectMapper = objectMapper;
    }

    /**

     * @param payload The Kafka ConsumerRecord containing the raw JSON string
     * @param topic The Kafka topic from which the message was received
     */
    @KafkaListener(topics = "${project.task.consumer.create.topic}")
    public void consumeTaskCreate(ConsumerRecord<String, Object> payload,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            log.info("Received task create event from topic: {}", topic);

            List<Task> tasks = Arrays.asList(objectMapper.readValue(
                    (String) payload.value(),
                    Task[].class));

            if (tasks == null || tasks.isEmpty()) {
                log.info("No tasks found in the message. Skipping notification processing.");
                return;
            }

            log.info("Received {} tasks from Kafka", tasks.size());

            // Filter tasks based on status and deliveryStrategy
            List<Task> filteredTasks = tasks.stream()
                    .filter(task -> isTaskEligibleForNotification(task))
                    .collect(Collectors.toList());

            log.info("Filtered {} tasks out of {} for notification scheduling (status=ADMINISTRATION_SUCCESS, deliveryStrategy=DIRECT if present)",
                    filteredTasks.size(), tasks.size());

            if (!filteredTasks.isEmpty()) {
                // Process distribution tasks and schedule notifications
                postDistributionService.processDistributionTasks(filteredTasks);
                log.info("Successfully processed {} filtered tasks", filteredTasks.size());
            } else {
                log.info("No tasks match the filtering criteria. Skipping notification processing.");
            }

        } catch (Exception exception) {
            log.error("Error processing task create event from topic: {}", topic, exception);
        }
    }

    /**
     * Checks if a task is eligible for notification processing.
     * A task is eligible if:
     * 1. Status is ADMINISTRATION_SUCCESS
     * 2. deliveryStrategy in additionalFields is "DIRECT" (only checked if deliveryStrategy field exists)
     *
     * If deliveryStrategy field is not present (e.g., HOUSEHOLD beneficiaries),
     * only the status check is applied.
     *
     * @param task The task to check
     * @return true if task meets the conditions, false otherwise
     */
    private boolean isTaskEligibleForNotification(Task task) {
        // Check if status is ADMINISTRATION_SUCCESS
        if (task.getStatus() != TaskStatus.ADMINISTRATION_SUCCESS) {
            log.debug("Task {} skipped: status is {} (expected ADMINISTRATION_SUCCESS)",
                    task.getId(), task.getStatus());
            return false;
        }

        // Check if deliveryStrategy is DIRECT
        if (!hasDeliveryStrategyDirect(task)) {
            log.debug("Task {} skipped: deliveryStrategy is not DIRECT", task.getId());
            return false;
        }

        log.debug("Task {} is eligible for notification", task.getId());
        return true;
    }

    /**
     * Checks if the task has deliveryStrategy set to "DIRECT" in additionalFields.
     * If deliveryStrategy field is not present (e.g., for HOUSEHOLD beneficiaries),
     * returns true to allow status-only filtering.
     *
     * @param task The task to check
     * @return true if deliveryStrategy is DIRECT or not present, false if deliveryStrategy exists but is not DIRECT
     */
    private boolean hasDeliveryStrategyDirect(Task task) {
        AdditionalFields additionalFields = task.getAdditionalFields();

        // Check if additionalFields exists
        if (additionalFields == null || additionalFields.getFields() == null) {
            // No additionalFields, allow task through (status-only filter)
            return true;
        }

        // Check if deliveryStrategy field exists
        boolean deliveryStrategyExists = additionalFields.getFields().stream()
                .anyMatch(field -> Constants.DELIVERY_STRATEGY_KEY.equals(field.getKey()));

        if (!deliveryStrategyExists) {
            // deliveryStrategy field not present (HOUSEHOLD case), allow task through
            log.debug("Task {} has no deliveryStrategy field, allowing through (status-only filter)", task.getId());
            return true;
        }

        // deliveryStrategy exists, check if value is "DIRECT"
        boolean isDirect = additionalFields.getFields().stream()
                .anyMatch(field -> Constants.DELIVERY_STRATEGY_KEY.equals(field.getKey())
                        && Constants.DELIVERY_STRATEGY_DIRECT.equals(field.getValue()));

        log.debug("Task {} has deliveryStrategy field, value is {}",
                task.getId(), isDirect ? "DIRECT" : "not DIRECT");
        return isDirect;
    }
}
