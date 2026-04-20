package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.transformer.transformationservice.UserActionTransformationService;
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
public class UserActionConsumer {
    private final ObjectMapper objectMapper;
    private final UserActionTransformationService userActionTransformationService;

    @Autowired
    public UserActionConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, UserActionTransformationService userActionTransformationService) {
        this.objectMapper = objectMapper;
        this.userActionTransformationService = userActionTransformationService;
    }

    @KafkaListener(topics = {"${transformer.consumer.create.user.action.topic}",
            "${transformer.consumer.update.user.action.topic}"})
    public void consumeUserActions(ConsumerRecord<String, Object> payload,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<UserAction> userActions = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            UserAction[].class));
            userActionTransformationService.transform(userActions);
        } catch (Exception exception) {
            log.error("error in user action consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}