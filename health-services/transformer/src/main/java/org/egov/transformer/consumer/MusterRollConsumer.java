package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.models.musterRoll.MusterRoll;
import org.egov.transformer.models.musterRoll.MusterRollRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class MusterRollConsumer {

    private final ObjectMapper objectMapper;


    public MusterRollConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"${transformer.consumer.muster.roll.create.topic}",
            "${transformer.consumer.muster.roll.update.topic}"})
    public void consumeMusterHolds(ConsumerRecord<String, Object> payload,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            MusterRollRequest musterRollRequest = objectMapper.readValue((String) payload.value(), MusterRollRequest.class);
            MusterRoll payloadList = musterRollRequest.getMusterRoll();
        } catch (Exception exception) {
            log.error("error in muster roll consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}
