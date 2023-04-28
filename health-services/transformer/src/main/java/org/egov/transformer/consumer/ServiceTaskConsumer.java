package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.servicerequest.web.models.Service;
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
public class ServiceTaskConsumer {
    private final TransformationHandler<Service> transformationHandler;

    private final ObjectMapper objectMapper;

    @Autowired
    public ServiceTaskConsumer(TransformationHandler<Service> transformationHandler,
                                @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.transformationHandler = transformationHandler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"${transformer.consumer.create.service.topic}"})
    public void consumeStaff(ConsumerRecord<String, Object> payload,
                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<Service> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            Service[].class));
            transformationHandler.handle(payloadList, Operation.SERVICE);
        } catch (Exception exception) {
            log.error("error in service task consumer", exception);
        }
    }

}
