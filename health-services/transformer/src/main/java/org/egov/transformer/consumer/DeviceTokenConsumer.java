package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.models.attendance.AttendanceLog;
import org.egov.transformer.models.attendance.AttendanceLogRequest;
import org.egov.transformer.models.devicetoken.DeviceToken;
import org.egov.transformer.models.devicetoken.DeviceTokenRequest;
import org.egov.transformer.transformationservice.AttendanceTransformationService;
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

    @Autowired
    public DeviceTokenConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, DeviceTokenTransformationService deviceTokenTransformationService) {
        this.objectMapper = objectMapper;
        this.deviceTokenTransformationService = deviceTokenTransformationService;
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
        }
    }
}
