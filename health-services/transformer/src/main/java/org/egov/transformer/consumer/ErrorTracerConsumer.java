package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.stock.Stock;
import org.egov.transformer.models.error.ErrorDetails;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.handler.TransformationHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.egov.common.models.*;
import org.egov.transformer.transformationservice.ErrorTracerTransformationService;


import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class ErrorTracerConsumer {

    private final ObjectMapper objectMapper;

    private final ErrorTracerTransformationService errorTracerTransformationService;

    public ErrorTracerConsumer(ObjectMapper objectMapper, ErrorTracerTransformationService errorTracerTransformationService) {
        this.objectMapper = objectMapper;
        this.errorTracerTransformationService = errorTracerTransformationService;
    }

    @KafkaListener(topics = { "${transformer.consumer.error.tracer.topic}"})
    public void consumeErrors(ConsumerRecord<String, Object> payload,
                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<ErrorDetails> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            ErrorDetails[].class));
            errorTracerTransformationService.transform(payloadList);
        } catch (Exception exception) {
            log.error("error in stock consumer bulk create", exception);
        }
    }
}
