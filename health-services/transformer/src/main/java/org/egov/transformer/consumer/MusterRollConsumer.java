package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.models.musterRoll.MusterRoll;
import org.egov.transformer.models.musterRoll.MusterRollRequest;
import org.egov.transformer.producer.TransformerErrorProducer;
import org.egov.transformer.transformationservice.MusterRollTransformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class MusterRollConsumer {
    private final ObjectMapper objectMapper;
    private final MusterRollTransformationService musterRollTransformationService;
    private final TransformerErrorProducer errorQueueProducer;

    @Autowired
    public MusterRollConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper,
                              MusterRollTransformationService musterRollTransformationService,
                              TransformerErrorProducer errorQueueProducer) {
        this.objectMapper = objectMapper;
        this.musterRollTransformationService = musterRollTransformationService;
        this.errorQueueProducer = errorQueueProducer;
    }

    @KafkaListener(topics = {"${transformer.consumer.save.musterroll.topic}",
            "${transformer.consumer.update.musterroll.topic}"})
    public void consumeMusterRoll(ConsumerRecord<String, Object> payload,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            MusterRollRequest musterRollRequest = objectMapper.readValue((String) payload.value(), MusterRollRequest.class);
            MusterRoll musterRoll = musterRollRequest.getMusterRoll();
            musterRollTransformationService.transform(musterRoll);
        } catch (Exception exception) {
            log.error("TRANSFORMER error in musterRoll consumer {}", ExceptionUtils.getStackTrace(exception));
            errorQueueProducer.sendToErrorTopic(payload.value(), topic, exception);
        }
    }
}
