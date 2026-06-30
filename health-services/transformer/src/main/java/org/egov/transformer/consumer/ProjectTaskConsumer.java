package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.project.Task;
import org.egov.transformer.producer.TransformerErrorProducer;
import org.egov.transformer.transformationservice.ProjectTaskTransformationService;
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
    private final ObjectMapper objectMapper;
    private final ProjectTaskTransformationService projectTaskTransformationService;
    private final TransformerErrorProducer errorQueueProducer;

    @Autowired
    public ProjectTaskConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper,
                               ProjectTaskTransformationService projectTaskTransformationService,
                               TransformerErrorProducer errorQueueProducer) {
        this.objectMapper = objectMapper;
        this.projectTaskTransformationService = projectTaskTransformationService;
        this.errorQueueProducer = errorQueueProducer;
    }

    @KafkaListener(topics = { "${transformer.consumer.bulk.create.project.task.topic}",
            "${transformer.consumer.bulk.update.project.task.topic}"})
    public void consumeTask(ConsumerRecord<String, Object> payload,
                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<Task> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            Task[].class));
            projectTaskTransformationService.transform(payloadList);
        } catch (Exception exception) {
            log.error("error in project task bulk consumer {}", ExceptionUtils.getStackTrace(exception));
            errorQueueProducer.sendToErrorTopic(payload.value(), topic, exception);
        }
    }
}
