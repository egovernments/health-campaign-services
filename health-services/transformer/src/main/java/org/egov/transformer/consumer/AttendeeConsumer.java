package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.models.attendance.AttendanceLog;
import org.egov.transformer.models.attendance.AttendanceLogRequest;
import org.egov.transformer.models.attendance.AttendeeCreateRequest;
import org.egov.transformer.models.attendance.IndividualEntry;
import org.egov.transformer.transformationservice.AttendanceTransformationService;
import org.egov.transformer.transformationservice.AttendeeTransformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;


@Slf4j
@Component
public class AttendeeConsumer {
    private final ObjectMapper objectMapper;
    private final AttendeeTransformationService attendeeTransformationService;

    @Autowired
    public AttendeeConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, AttendeeTransformationService attendeeTransformationService) {
        this.objectMapper = objectMapper;
        this.attendeeTransformationService = attendeeTransformationService;
    }

    @KafkaListener(topics = {"${transformer.consumer.save.attendee.topic}",
            "${transformer.consumer.update.attendee.topic}"})
    public void consumeAttendee(ConsumerRecord<String, Object> payload,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            AttendeeCreateRequest attendeeCreateRequest = objectMapper.readValue((String) payload.value(), AttendeeCreateRequest.class);
            List<IndividualEntry> payloadList = attendeeCreateRequest.getAttendees();
            attendeeTransformationService.transform(payloadList);
        } catch (Exception exception) {
            log.error("TRANSFORMER error in attendee consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}
