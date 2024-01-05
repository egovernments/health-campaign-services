package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.referralmanagement.sideeffect.*;
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
import java.util.stream.Collectors;

@Component
@Slf4j
public class SideEffectConsumer {

    private final TransformationHandler<SideEffect> transformationHandler;

    private final ObjectMapper objectMapper;

    @Autowired
    public SideEffectConsumer(TransformationHandler<SideEffect> transformationHandler,
                              @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.transformationHandler = transformationHandler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"${transformer.consumer.create.side.effect.topic}",
            "${transformer.consumer.update.side.effect.topic}"})
    public void consumeSideEffect(ConsumerRecord<String, Object> payload,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            log.info("payload {}",payload);
            List<SideEffect> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            SideEffect[].class));
            log.info("payloadList {}",payloadList);
            transformationHandler.handle(payloadList, Operation.SIDE_EFFECT);
        } catch (Exception exception) {
            log.error("error in side effect consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}