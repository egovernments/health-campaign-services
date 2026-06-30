package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.models.devicetoken.DeviceToken;
import org.egov.transformer.models.devicetoken.DeviceTokenRequest;
import org.egov.transformer.producer.TransformerErrorProducer;
import org.egov.transformer.transformationservice.DeviceTokenTransformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;


@Slf4j
@Component
public class DeviceTokenConsumer {
    private final ObjectMapper objectMapper;
    private final DeviceTokenTransformationService deviceTokenTransformationService;
    private final TransformerErrorProducer errorQueueProducer;

    @Autowired
    public DeviceTokenConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper,
                               DeviceTokenTransformationService deviceTokenTransformationService,
                               TransformerErrorProducer errorQueueProducer) {
        this.objectMapper = objectMapper;
        this.deviceTokenTransformationService = deviceTokenTransformationService;
        this.errorQueueProducer = errorQueueProducer;
    }

    @KafkaListener(topics = {"${transformer.consumer.save.device.token.topic}"})
    public void consumeDeviceToken(ConsumerRecord<String, Object> payload,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            DeviceTokenRequest deviceTokenRequest = objectMapper.readValue((String) payload.value(), DeviceTokenRequest.class);
            List<DeviceToken> payloadList = deviceTokenRequest.getDeviceTokens();
            deviceTokenTransformationService.transform(payloadList);
        } catch (Exception exception) {
            log.error("TRANSFORMER error in DeviceToken CONSUMER {}", ExceptionUtils.getStackTrace(exception));
            errorQueueProducer.sendToErrorTopic(payload.value(), topic, exception);
        }
    }
}
