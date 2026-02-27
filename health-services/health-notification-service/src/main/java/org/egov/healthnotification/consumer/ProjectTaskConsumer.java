package org.egov.healthnotification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.project.Task;
import org.egov.healthnotification.service.PostDistributionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

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
     * Consumes task creation events from Kafka topic.
     * The topic contains an array of Task objects (Task[]) which is deserialized
     * and converted to a List for processing.
     *
     * @param payload The Kafka ConsumerRecord containing Task[] array
     * @param topic The Kafka topic from which the message was received
     */
    @KafkaListener(topics = "${project.task.consumer.create.topic}")
    public void consumeTaskCreate(ConsumerRecord<String, Object> payload,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            log.info("Received task create event from topic: {}", topic);

            // Deserialize Task array from Kafka message
            List<Task> tasks = Arrays.asList(objectMapper.readValue(
                    (String) payload.value(),
                    Task[].class));

            log.info("Processing {} tasks for notification scheduling", tasks.size());

            // Process distribution tasks and schedule notifications
            postDistributionService.processDistributionTasks(tasks);

            log.info("Successfully processed {} tasks", tasks.size());

        } catch (Exception exception) {
            log.error("Error processing task create event from topic: {}", topic, exception);
        }
    }
}
