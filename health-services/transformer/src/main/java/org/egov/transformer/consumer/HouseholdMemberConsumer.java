package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.household.HouseholdMember;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.handler.TransformationHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class HouseholdMemberConsumer {

    private final ObjectMapper objectMapper;

    private final TransformationHandler<HouseholdMember> transformationHandler;

    public HouseholdMemberConsumer(ObjectMapper objectMapper, TransformationHandler<HouseholdMember> transformationHandler) {
        this.objectMapper = objectMapper;
        this.transformationHandler = transformationHandler;
    }

    @KafkaListener(topics = { "${transformer.consumer.save.household.member.topic}","${transformer.consumer.update.household.member.topic}",
            })
    public void consumeHouseholdMember(ConsumerRecord<String, Object> payload,
                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<HouseholdMember> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            HouseholdMember[].class));
            transformationHandler.handle(payloadList, Operation.HOUSEHOLD_MEMBER);
        } catch (Exception exception) {
            log.error("error in household member consumer create {}", ExceptionUtils.getStackTrace(exception));
        }
    }

}