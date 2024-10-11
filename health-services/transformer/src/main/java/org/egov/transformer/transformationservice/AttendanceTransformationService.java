package org.egov.transformer.transformationservice;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.individual.Name;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.attendance.AttendanceLog;
import org.egov.transformer.models.attendance.AttendanceRegister;
import org.egov.transformer.models.attendance.IndividualEntry;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.AttendanceLogIndexV1;
import org.egov.transformer.models.downstream.AttendanceRegisterIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.AttendanceRegisterService;
import org.egov.transformer.service.BoundaryService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class AttendanceTransformationService {

    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final UserService userService;
    private final BoundaryService boundaryService;

    private final CommonUtils commonUtils;

    private final AttendanceRegisterService attendanceRegisterService;

    public AttendanceTransformationService(TransformerProperties transformerProperties, Producer producer, UserService userService, BoundaryService boundaryService, CommonUtils commonUtils, AttendanceRegisterService attendanceRegisterService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.userService = userService;
        this.boundaryService = boundaryService;
        this.commonUtils = commonUtils;
        this.attendanceRegisterService = attendanceRegisterService;
    }

    public void transform(List<AttendanceLog> payloadList) {
        if (payloadList.isEmpty()) {
            log.info("Payload list is empty in ATTENDANCE_TRANSFORMATION");
            return;
        }

        String attendanceRegisterTopic = transformerProperties.getTransformerProducerAttendanceRegisterIndexV1Topic();

        AttendanceLog attendanceLog = payloadList.get(0);

        String registerId = attendanceLog.getRegisterId();
        String tenantId = attendanceLog.getTenantId();
        String userUuid = attendanceLog.getAuditDetails().getCreatedBy();
        AttendanceRegister attendanceRegister = attendanceRegisterService.findAttendanceRegisterById(registerId, tenantId, userUuid);


        String topic = transformerProperties.getTransformerProducerAttendanceLogIndexV1Topic();
        log.info("transforming attendanceLog for ids {}", payloadList.stream().map(AttendanceLog::getId).collect(Collectors.toList()));

        List<AttendanceLogIndexV1> transformedPayloadList = payloadList.stream().map(attendanceLog1 -> transform(attendanceLog1, attendanceRegister)).collect(Collectors.toList());
        log.info("transformation successful for attendance log");
        producer.push(topic, transformedPayloadList);

        log.info("transforming attendanceRegister for id {}", registerId);

        if (attendanceRegister == null) {
            log.info("Attendance Register is null Skipping transformation");
            return;
        }

        AttendanceRegisterIndexV1 attendanceRegisterIndexV1 = transformRegister(attendanceRegister);

        producer.push(attendanceRegisterTopic, Collections.singletonList(attendanceRegisterIndexV1));
        log.info("transformation successful for attendance register");
    }

    public AttendanceLogIndexV1 transform(AttendanceLog attendanceLog, AttendanceRegister attendanceRegister) {
        Name attendeeName = attendanceRegisterService.fetchAttendeesInfo(
                        Collections.singletonList(attendanceLog.getIndividualId()),
                        attendanceLog.getTenantId())
                .get(attendanceLog.getIndividualId());
        Map<String, String> userInfoMap = userService.getUserInfo(attendanceLog.getTenantId(), attendanceLog.getAuditDetails().getCreatedBy());
        String projectIdProjectTypeId = commonUtils.projectDetailsFromUserId(attendanceLog.getAuditDetails().getCreatedBy(), attendanceLog.getTenantId());
        Map<String, String> boundaryHierarchy = null;
        Map<String, String> boundaryHierarchyCode = null;
        if (!StringUtils.isEmpty(projectIdProjectTypeId)) {
            String projectId = projectIdProjectTypeId.split(":")[0];
            BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(projectId, attendanceLog.getTenantId());
            boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
            boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
        }
        AttendanceLogIndexV1 attendanceLogIndexV1 = AttendanceLogIndexV1.builder()
                .attendanceLog(attendanceLog)
                .attendeeName(attendeeName)
                .userName(userInfoMap.get(USERNAME))
                .role(userInfoMap.get(ROLE))
                .attendanceTime(commonUtils.getTimeStampFromEpoch(attendanceLog.getTime().longValue()))
                .registerName(attendanceRegister != null ? attendanceRegister.getName() : null)
                .registerServiceCode(attendanceRegister != null ? attendanceRegister.getServiceCode() : null)
                .registerNumber(attendanceRegister != null ? attendanceRegister.getRegisterNumber() : null)
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .build();
        return attendanceLogIndexV1;
    }

    public AttendanceRegisterIndexV1 transformRegister(AttendanceRegister attendanceRegister) {
        List<String> attendeesIndIds = attendanceRegister.getAttendees().stream().map(IndividualEntry::getIndividualId).collect(Collectors.toList());
        Map<String, Name> attendeesInfo = attendanceRegisterService.fetchAttendeesInfo(attendeesIndIds, attendanceRegister.getTenantId());
        AttendanceRegisterIndexV1 attendanceRegisterIndexV1 = AttendanceRegisterIndexV1.builder()
                .attendanceRegister(attendanceRegister)
                .attendeesInfo(attendeesInfo)
                .transformerTimeStamp(commonUtils.getTimeStampFromEpoch(System.currentTimeMillis()))
                .build();
        return attendanceRegisterIndexV1;
    }

}

