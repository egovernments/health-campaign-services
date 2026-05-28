package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.models.upstream.Service;
import org.egov.transformer.models.upstream.ServiceRequest;
import org.egov.transformer.transformationservice.ServiceTaskTransformationService;
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
public class ServiceTaskConsumer {
    private final ObjectMapper objectMapper;
    private final ServiceTaskTransformationService serviceTaskTransformationService;

    @Autowired
    public ServiceTaskConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, ServiceTaskTransformationService serviceTaskTransformationService) {
        this.objectMapper = objectMapper;
        this.serviceTaskTransformationService = serviceTaskTransformationService;
    }

    @KafkaListener(topics = {"${transformer.consumer.create.service.topic}"})
    public void consumeServiceTask(ConsumerRecord<String, Object> payload,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<ServiceRequest> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(), ServiceRequest.class));
            List<Service> collect = payloadList.stream().map(p -> p.getService()).collect(Collectors.toList());
            serviceTaskTransformationService.transform(collect);
        } catch (Exception exception) {
            log.error("TRANSFORMER error in service task consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }

}
