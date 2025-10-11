package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.models.attendance.*;
import org.egov.transformer.transformationservice.AttendanceTransformationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;


@Slf4j
@Component
public class AttendanceConsumer {
    private final ObjectMapper objectMapper;
    private final AttendanceTransformationService attendanceTransformationService;

    @Autowired
    public AttendanceConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, AttendanceTransformationService attendanceTransformationService) {
        this.objectMapper = objectMapper;
        this.attendanceTransformationService = attendanceTransformationService;
    }

    @KafkaListener(topics = {"${transformer.consumer.save.attendance.log.topic}",
            "${transformer.consumer.update.attendance.log.topic}"})
    public void consumeAttendanceLog(ConsumerRecord<String, Object> payload,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            AttendanceLogRequest attendanceLogRequest = objectMapper.readValue((String) payload.value(), AttendanceLogRequest.class);
            List<AttendanceLog> payloadList = attendanceLogRequest.getAttendance();
            attendanceTransformationService.transform(payloadList);
        } catch (Exception exception) {
            log.error("TRANSFORMER error in attendance consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }

    @KafkaListener(topics = {"${transformer.consumer.save.attendance.register.topic}",
            "${transformer.consumer.update.attendance.register.topic}"})
    public void consumeAttendanceRegister(ConsumerRecord<String, Object> payload,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            AttendanceRegisterRequest attendanceRegisterRequest = objectMapper.readValue((String) payload.value(), AttendanceRegisterRequest.class);
            List<AttendanceRegister> payloadList = attendanceRegisterRequest.getAttendanceRegister();
            attendanceTransformationService.transformRegister(payloadList);
        } catch (Exception exception) {
            log.error("TRANSFORMER error in attendance consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}
