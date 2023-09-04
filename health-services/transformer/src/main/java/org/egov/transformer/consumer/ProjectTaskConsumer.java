package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.project.Task;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.handler.TransformationHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class ProjectTaskConsumer {

    private final TransformationHandler<Task> transformationHandler;

    private final ObjectMapper objectMapper;

    @Autowired
    public ProjectTaskConsumer(TransformationHandler<Task> transformationHandler,
                               @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.transformationHandler = transformationHandler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = { "${transformer.consumer.bulk.create.project.task.topic}",
            "${transformer.consumer.bulk.update.project.task.topic}"})
    public void consumeTask(ConsumerRecord<String, Object> payload,
                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<Task> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            Task[].class));
            transformationHandler.handle(payloadList, Operation.TASK);
        } catch (Exception exception) {
            log.error("error in project task bulk consumer", ExceptionUtils.getStackTrace(exception));
        }
    }
}
