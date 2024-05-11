package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.models.pgr.Service;
import org.egov.transformer.models.pgr.ServiceRequest;
import org.egov.transformer.transformationservice.PGRTransformationService;
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

    private final ObjectMapper objectMapper;

    private final PGRTransformationService pgrTransformationService;

    @Autowired
    public PGRConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, PGRTransformationService pgrTransformationService) {
        this.objectMapper = objectMapper;
        this.pgrTransformationService = pgrTransformationService;
    }

    @KafkaListener(topics = {"${transformer.consumer.create.pgr.topic}", "${transformer.consumer.update.pgr.topic}"})
    public void consumeServiceTask(ConsumerRecord<String, Object> payload,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<ServiceRequest> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(), ServiceRequest.class));
            List<Service> collect = payloadList.stream().map(p -> p.getService()).collect(Collectors.toList());
            pgrTransformationService.transform(collect);
        } catch (Exception exception) {
            log.error("TRANSFORMER error in PGR consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }

}
