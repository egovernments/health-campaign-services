package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.referralmanagement.sideeffect.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.egov.transformer.transformationservice.SideEffectTransformationService;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class SideEffectConsumer {


    private final ObjectMapper objectMapper;

    private final SideEffectTransformationService sideEffectTransformationService;

    @Autowired
    public SideEffectConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, SideEffectTransformationService sideEffectTransformationService) {

        this.objectMapper = objectMapper;
        this.sideEffectTransformationService = sideEffectTransformationService;
    }

    @KafkaListener(topics = {"${transformer.consumer.create.side.effect.topic}",
            "${transformer.consumer.update.side.effect.topic}"})
    public void consumeSideEffect(ConsumerRecord<String, Object> payload,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<SideEffect> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            SideEffect[].class));
            sideEffectTransformationService.transform(payloadList);
        } catch (Exception exception) {
            log.error("TRANSFORMER error in side effect consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}