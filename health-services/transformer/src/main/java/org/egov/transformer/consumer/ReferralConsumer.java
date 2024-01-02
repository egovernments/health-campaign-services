package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralRequest;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.handler.TransformationHandler;
import org.egov.transformer.models.downstream.ReferralIndexV1;
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
public class ReferralConsumer {
    private final TransformationHandler<Referral> transformationHandler;

    private final ObjectMapper objectMapper;

    @Autowired
    public ReferralConsumer(TransformationHandler<Referral> transformationHandler,
                              @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.transformationHandler = transformationHandler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"${transformer.consumer.create.referral.topic}"})
    public void consumeReferral(ConsumerRecord<String, Object> payload,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<ReferralRequest> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(), ReferralRequest.class));
            List<Referral> collect = payloadList.stream().map(p -> p.getReferral()).collect(Collectors.toList());
            transformationHandler.handle(collect, Operation.REFERRAL);
        } catch (Exception exception) {
            log.error("error in referral consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}
