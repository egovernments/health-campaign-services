package org.egov.kafka;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.service.AttendanceLogService;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.AttendanceLogRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class AttendanceLogConsumer {

    @Autowired
    private AttendanceLogService attendanceLogService;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "${attendance.log.kafka.consumer.bulk.create.topic}")
    public void bulkCreate(Map<String, Object> consumerRecord,
                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            AttendanceLogRequest request = objectMapper.convertValue(consumerRecord, AttendanceLogRequest.class);
            attendanceLogService.createAttendanceLog(request);
        } catch (Exception exception) {
            log.error("Error in Attendance Log consumer bulk create", exception);
            log.error("Exception trace: ", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("HCM_ATTENDANCE_LOG_CREATE", exception.getMessage());
        }
    }

    @KafkaListener(topics = "${attendance.log.kafka.consumer.bulk.update.topic}")
    public void bulkUpdate(Map<String, Object> consumerRecord,
                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            AttendanceLogRequest request = objectMapper.convertValue(consumerRecord, AttendanceLogRequest.class);
            attendanceLogService.updateAttendanceLog(request);
        } catch (Exception exception) {
            log.error("Error in Attendance Log consumer bulk update", exception);
            log.error("Exception trace: ", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("HCM_ATTENDANCE_LOG_UPDATE", exception.getMessage());
        }
    }

}
