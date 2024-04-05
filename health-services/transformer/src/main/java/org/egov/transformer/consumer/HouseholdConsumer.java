package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.household.Household;
import org.egov.transformer.service.HouseholdService;
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
public class HouseholdConsumer {
    private final ObjectMapper objectMapper;

    private final HouseholdService householdService;

    @Autowired
    public HouseholdConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, HouseholdService householdService) {
        this.objectMapper = objectMapper;
        this.householdService = householdService;
    }

    @KafkaListener(topics = {"${transformer.consumer.create.household.topic}",
            "${transformer.consumer.update.household.topic}"})
    public void consumeHouseholds(ConsumerRecord<String, Object> payload,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<Household> households = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            Household[].class));
            householdService.transform(households);
        } catch (Exception exception) {
            log.error("error in household consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}

