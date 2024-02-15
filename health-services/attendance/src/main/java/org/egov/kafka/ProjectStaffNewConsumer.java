package org.egov.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.project.ProjectRequest;
import org.egov.common.models.project.ProjectStaffRequest;
import org.egov.service.AttendanceRegisterService;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class ProjectStaffNewConsumer {
    @Autowired
    private AttendanceRegisterService attendanceRegisterService;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "${project.staff.kafka.create.topic}")
    public void bulkStaffCreateNew(Map<String, Object> consumerRecord,
                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            log.info("Project Staff Consumer Started for project update.");
            log.info("TOPIC ---> "+ topic);
            ProjectStaffRequest projectRequest = objectMapper.convertValue(consumerRecord, ProjectStaffRequest.class);
//            attendanceRegisterService.updateAttendanceRegister(RequestInfoWrapper.builder().requestInfo(projectRequest.getRequestInfo()).build(), projectRequest.getProjects());
            log.info("Project Staff - "+projectRequest.getProjectStaff().toString());
        } catch (Exception exception) {
            log.error("Error in Project Staff Consumer", exception);
            log.error("Exception trace: ", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("HCM_PROJECT_STAFF_CREATE", exception.getMessage());
        }
    }
}
