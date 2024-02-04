package org.egov.transformer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.individual.Name;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.attendance.AttendanceLog;
import org.egov.transformer.models.attendance.AttendanceRegister;
import org.egov.transformer.models.downstream.AttendanceLogIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class AttendanceTransformationService {

    private final TransformerProperties transformerProperties;
    private final ObjectMapper objectMapper;
    private final Producer producer;
    private final UserService userService;
    private final ProjectService projectService;
    private final IndividualService individualService;

    private final CommonUtils commonUtils;

    private final AttendanceRegisterService attendanceRegisterService;

    public AttendanceTransformationService(TransformerProperties transformerProperties,
                                           ObjectMapper objectMapper, Producer producer, UserService userService, ProjectService projectService, IndividualService individualService, CommonUtils commonUtils, AttendanceRegisterService attendanceRegisterService) {
        this.transformerProperties = transformerProperties;
        this.objectMapper = objectMapper;
        this.producer = producer;
        this.userService = userService;
        this.projectService = projectService;
        this.individualService = individualService;
        this.commonUtils = commonUtils;
        this.attendanceRegisterService = attendanceRegisterService;
    }

    public void transform(List<AttendanceLog> payloadList) {
        String topic = transformerProperties.getTransformerProducerAttendanceLogIndexV1Topic();
        log.info("transforming for ids {}", payloadList.stream()
                .map(AttendanceLog::getId).collect(Collectors.toList()));
        List<AttendanceLogIndexV1> transformedPayloadList = payloadList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        log.info("transformation successful");
        producer.push(topic,
                transformedPayloadList);
    }

    public AttendanceLogIndexV1 transform(AttendanceLog attendanceLog) {
        AttendanceRegister attendanceRegister = attendanceRegisterService.findAttendanceRegisterById(Collections.singletonList(attendanceLog.getRegisterId()), attendanceLog.getTenantId(), attendanceLog.getAuditDetails().getCreatedBy());
        Name individualName = individualService.getIndividualNameById(attendanceLog.getIndividualId(), attendanceLog.getTenantId());
        Map<String, String> userInfoMap = userService.getUserInfo(attendanceLog.getTenantId(), attendanceLog.getAuditDetails().getCreatedBy());
        AttendanceLogIndexV1 attendanceLogIndexV1 = AttendanceLogIndexV1.builder()
                .attendanceLog(attendanceLog)
                .nameObject(individualName)
                .familyName(individualName.getFamilyName())
                .givenName(individualName.getGivenName())
                .userName(userInfoMap.get(USERNAME))
                .role(userInfoMap.get(ROLE))
                .attendanceTime(commonUtils.getTimeStampFromEpoch(attendanceLog.getTime().longValue()))
                .attendanceRegister(attendanceRegister)
                .build();
        return attendanceLogIndexV1;
    }

}

