package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.project.Task;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.handler.TransformationHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class ProjectTaskConsumer {

    private final TransformationHandler<Task> transformationHandler;

    private final ObjectMapper objectMapper;

    private final List<Task> taskBatch = new ArrayList<>();

    private long startTimeMillis;

    private final TransformerProperties transformerProperties;

    @Autowired
    public ProjectTaskConsumer(TransformationHandler<Task> transformationHandler,
                               @Qualifier("objectMapper") ObjectMapper objectMapper, TransformerProperties transformerProperties) {
        this.transformationHandler = transformationHandler;
        this.objectMapper = objectMapper;

        this.transformerProperties = transformerProperties;
    }

    @KafkaListener(topics = { "${transformer.consumer.bulk.create.project.task.topic}",
            "${transformer.consumer.bulk.update.project.task.topic}"})
    public void consumeTask(ConsumerRecord<String, Object> payload,
                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<Task> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            Task[].class));
            taskBatch.addAll(payloadList);
//            transformationHandler.handle(payloadList, Operation.TASK);
//            List<ProjectTaskIndexV1> rec = newProjectTaskTransformationService.transformNew(payloadList);

            long batchProcessingInterval = transformerProperties.getTaskTimeLimit() * 1000;
            if (System.currentTimeMillis() - startTimeMillis >= batchProcessingInterval ||
                    taskBatch.size() >= transformerProperties.getTaskBatchSize()) {
                processTaskBatch();
            }
        } catch (Exception exception) {
            log.error("error in project task bulk consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
    private void processTaskBatch() {
        if (!taskBatch.isEmpty()) {
            try {
                transformationHandler.handle(taskBatch, Operation.TASK);
//                List<ProjectTaskIndexV1> rec = newProjectTaskTransformationService.transformNew(taskBatch);
            } catch (Exception exception) {
                log.error("Error processing task batch: {}", ExceptionUtils.getStackTrace(exception));
            } finally {
                taskBatch.clear(); // Clear the batch after processing
                startTimeMillis = System.currentTimeMillis();
            }
        }
    }
}
