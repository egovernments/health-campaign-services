package org.egov.transformer.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.handler.TransformationHandler;
import org.egov.transformer.models.upstream.Task;
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

    private final TransformationHandler<Task> transformationHandler;

    private final ObjectMapper objectMapper;

    @Autowired
    public ProjectTaskConsumer(TransformationHandler<Task> transformationHandler,
                               @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.transformationHandler = transformationHandler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${transformer.consumer.bulk.create.project.task.topic}")
    public void bulkCreate(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            log.info("payload(s) received on topic {}", topic);
            List<Task> payloadList = objectMapper.convertValue(consumerRecord, new TypeReference<List<Task>>(){});
            transformationHandler.handle(payloadList, Operation.CREATE);
        } catch (Exception exception) {
            log.error("error in project task consumer bulk create", exception);
        }
    }
}
