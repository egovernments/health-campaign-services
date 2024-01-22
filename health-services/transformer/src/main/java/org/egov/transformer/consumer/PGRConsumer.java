package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.handler.TransformationHandler;
import org.egov.transformer.models.pgr.Service;
import org.egov.transformer.models.pgr.ServiceRequest;
import org.egov.transformer.service.PGRTransformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PGRConsumer {
    private final TransformationHandler<Service> transformationHandler;

    private final ObjectMapper objectMapper;

    private final PGRTransformationService pgrTransformationService;

    @Autowired
    public PGRConsumer(TransformationHandler<Service> transformationHandler,
                       @Qualifier("objectMapper") ObjectMapper objectMapper, PGRTransformationService pgrTransformationService) {
        this.transformationHandler = transformationHandler;
        this.objectMapper = objectMapper;
        this.pgrTransformationService = pgrTransformationService;
    }

    @KafkaListener(topics = {"${transformer.consumer.create.pgr.topic}"})
    public void consumeServiceTask(ConsumerRecord<String, Object> payload,
                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<ServiceRequest> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(), ServiceRequest.class));
            List<Service> collect = payloadList.stream().map(p -> p.getService()).collect(Collectors.toList());
            transformationHandler.handle(collect, Operation.PGR);
            pgrTransformationService.transform(collect);
        } catch (Exception exception) {
            log.error("error in PGR consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }

}
