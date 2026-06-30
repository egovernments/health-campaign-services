package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.models.attendance.StaffPermission;
import org.egov.transformer.models.attendance.StaffPermissionRequest;
import org.egov.transformer.producer.ErrorQueueProducer;
import org.egov.transformer.transformationservice.AttendanceStaffTransformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;


@Slf4j
@Component
public class AttendanceStaffConsumer {
    private final ObjectMapper objectMapper;
    private final AttendanceStaffTransformationService attendanceStaffTransformationService;
    private final ErrorQueueProducer errorQueueProducer;

    @Autowired
    public AttendanceStaffConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper,
                                   AttendanceStaffTransformationService attendanceStaffTransformationService,
                                   ErrorQueueProducer errorQueueProducer) {
        this.objectMapper = objectMapper;
        this.attendanceStaffTransformationService = attendanceStaffTransformationService;
        this.errorQueueProducer = errorQueueProducer;
    }

    @KafkaListener(topics = {"${transformer.consumer.save.attendance.staff.topic}",
            "${transformer.consumer.update.attendance.staff.topic}"})
    public void consumeAttendanceStaff(ConsumerRecord<String, Object> payload,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            StaffPermissionRequest staffPermissionRequest = objectMapper.readValue((String) payload.value(), StaffPermissionRequest.class);
            List<StaffPermission> payloadList = staffPermissionRequest.getStaff();
            attendanceStaffTransformationService.transform(payloadList);
        } catch (Exception exception) {
            log.error("TRANSFORMER error in attendance staff consumer {}", ExceptionUtils.getStackTrace(exception));
            errorQueueProducer.sendToErrorTopic(payload.value(), topic, exception);
        }
    }
}
