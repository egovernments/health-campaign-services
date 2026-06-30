package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.transformer.producer.ErrorQueueProducer;
import org.egov.transformer.transformationservice.HfReferralTransformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class HfReferralConsumer {
    private final ObjectMapper objectMapper;
    private final HfReferralTransformationService hfReferralTransformationService;
    private final ErrorQueueProducer errorQueueProducer;

    @Autowired
    public HfReferralConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper,
                              HfReferralTransformationService hfReferralTransformationService,
                              ErrorQueueProducer errorQueueProducer) {
        this.objectMapper = objectMapper;
        this.hfReferralTransformationService = hfReferralTransformationService;
        this.errorQueueProducer = errorQueueProducer;
    }

    @KafkaListener(topics = {"${transformer.consumer.create.hfreferral.topic}",
            "${transformer.consumer.update.hfreferral.topic}"})
    public void consumeReferral(ConsumerRecord<String, Object> payload,
                                @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<HFReferral> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            HFReferral[].class));
            hfReferralTransformationService.transform(payloadList);
        } catch (Exception exception) {
            log.error("error in hfReferral consumer {}", ExceptionUtils.getStackTrace(exception));
            errorQueueProducer.sendToErrorTopic(payload.value(), topic, exception);
        }
    }
}
