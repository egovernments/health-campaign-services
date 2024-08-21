package org.egov.transformer.consumer;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.handler.TransformationHandler;
import org.egov.transformer.service.UserActionLocationCaptureAggregationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProjectTaskConsumer {

    private final TransformationHandler<Task> transformationHandler;

    private final ObjectMapper objectMapper;

    private final UserActionLocationCaptureAggregationService userActionLocationCaptureAggregationService;

    @Autowired
    public ProjectTaskConsumer(
            TransformationHandler<Task> transformationHandler,
            @Qualifier("objectMapper") ObjectMapper objectMapper,
            UserActionLocationCaptureAggregationService userActionLocationCaptureAggregationService
    ) {
        this.transformationHandler = transformationHandler;
        this.objectMapper = objectMapper;
        this.userActionLocationCaptureAggregationService = userActionLocationCaptureAggregationService;
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
            log.error("error in project task bulk consumer", exception);
        }
    }

    @KafkaListener(topics = {"${transformer.consumer.bulk.create.project.location.capture.topic}"})
    public void consumeUserAction(ConsumerRecord<String, Object> payload,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<UserAction> payloadList = Arrays.asList(
                    objectMapper.readValue((String) payload.value(), UserAction[].class)
            );
            userActionLocationCaptureAggregationService.processUserActionLocationCapture(payloadList);
        } catch (Exception exception) {
            log.error("error in project user action bulk consumer", exception);
        }
    }
}
