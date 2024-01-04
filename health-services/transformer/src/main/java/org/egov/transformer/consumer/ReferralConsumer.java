package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.transformer.handler.TransformationHandler;
import org.egov.transformer.service.ReferralService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;

import java.util.Arrays;
import java.util.List;
@Slf4j
public class ReferralConsumer {
    private final ObjectMapper objectMapper;
    private final TransformationHandler<Referral> transformationHandler;
    private final ReferralService referralService;

    @Autowired
    public ReferralConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, TransformationHandler<Referral> transformationHandler, ReferralService referralService) {
        this.objectMapper = objectMapper;
        this.transformationHandler = transformationHandler;
        this.referralService = referralService;
    }

    @KafkaListener(topics = {"${transformer.consumer.create.referral.topic}",
            "${transformer.consumer.update.referral.topic}"})
    public void consumeReferral(ConsumerRecord<String, Object> payload,
                                @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<Referral> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            Referral[].class));
            referralService.transform(payloadList);
        } catch (Exception exception) {
            log.error("error in referral consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}
