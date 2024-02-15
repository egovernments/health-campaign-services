package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.household.Household;
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

    @Autowired
    public HouseholdConsumer(@Qualifier("objectMapper")ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = { "${transformer.consumer.create.household.topic}",
                "${transformer.consumer.update.household.topic}"})
        public void consumeHouseholds(ConsumerRecord<String, Object> payload,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
            try {
                List<Household> households = Arrays.asList(objectMapper
                        .readValue((String) payload.value(),
                                Household[].class));
            } catch (Exception exception) {
                log.error("error in household consumer", exception);
            }
        }
    }

