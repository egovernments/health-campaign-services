package org.egov.kafka;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.project.ProjectRequest;
import org.egov.service.AttendanceRegisterService;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AttendanceRegisterConsumer {
    @Autowired
    private AttendanceRegisterService attendanceRegisterService;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "${project.management.system.kafka.update.topic}")
    public void projectUpdate(Map<String, Object> consumerRecord,
                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ProjectRequest projectRequest = objectMapper.convertValue(consumerRecord, ProjectRequest.class);
            attendanceRegisterService.updateAttendanceRegister(RequestInfoWrapper.builder().requestInfo(projectRequest.getRequestInfo()).build(), projectRequest.getProjects());
        } catch (Exception exception) {
            log.error("Error in Attendance Register consumer update", exception);
            log.error("Exception trace: ", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("HCM_ATTENDANCE_REGISTER_UPDATE", exception.getMessage());
        }
    }
}
