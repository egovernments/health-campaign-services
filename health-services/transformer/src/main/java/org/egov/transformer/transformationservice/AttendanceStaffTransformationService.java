package org.egov.transformer.transformationservice;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.Name;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.attendance.AttendanceRegister;
import org.egov.transformer.models.attendance.IndividualEntry;
import org.egov.transformer.models.attendance.StaffPermission;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.AttendanceRegisterIndexV1;
import org.egov.transformer.models.downstream.AttendanceStaffIndexV1;
import org.egov.transformer.models.downstream.ProjectInfo;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.*;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.ROLE;
import static org.egov.transformer.Constants.USERNAME;

@Slf4j
@Component
public class AttendanceStaffTransformationService {

    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final UserService userService;
    private final BoundaryService boundaryService;

    private final CommonUtils commonUtils;

    private final AttendanceRegisterService attendanceRegisterService;
    private final IndividualService individualService;

    public AttendanceStaffTransformationService(TransformerProperties transformerProperties, Producer producer, UserService userService, BoundaryService boundaryService, CommonUtils commonUtils, AttendanceRegisterService attendanceRegisterService, ProjectService projectService, IndividualService individualService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.userService = userService;
        this.boundaryService = boundaryService;
        this.commonUtils = commonUtils;
        this.attendanceRegisterService = attendanceRegisterService;
        this.individualService = individualService;
    }

    public void transform(List<StaffPermission> payloadList) {
        if (payloadList.isEmpty()) {
            log.info("Payload list is empty in ATTENDEE_TRANSFORMATION");
            return;
        }

        String attendanceRegisterTopic = transformerProperties.getTransformerProducerAttendanceRegisterIndexV1Topic();

        StaffPermission staffPermission = payloadList.get(0);

        String registerId = staffPermission.getRegisterId();
        String tenantId = staffPermission.getTenantId();
        String userUuid = staffPermission.getAuditDetails().getCreatedBy();
        AttendanceRegister attendanceRegister = attendanceRegisterService.findAttendanceRegisterById(registerId, tenantId, userUuid);


        String topic = transformerProperties.getTransformerProducerAttendanceStaffIndexV1Topic();
        log.info("transforming attendees for ids {}", payloadList.stream().map(StaffPermission::getId).collect(Collectors.toList()));

        List<AttendanceStaffIndexV1> transformedPayloadList = payloadList.stream().map(staff1 -> transform(staff1, attendanceRegister)).collect(Collectors.toList());
        log.info("transformation successful for attendees");
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

    public AttendanceStaffIndexV1 transform(StaffPermission staff, AttendanceRegister attendanceRegister) {
        Individual individual = individualService.getIndividualById(staff.getUserId(), staff.getTenantId());

        AttendanceStaffIndexV1 attendanceStaffIndexV1 = AttendanceStaffIndexV1.builder()
                .staff(staff)
                .registerName(attendanceRegister != null ? attendanceRegister.getName() : null)
                .registerServiceCode(attendanceRegister != null ? attendanceRegister.getServiceCode() : null)
                .registerNumber(attendanceRegister != null ? attendanceRegister.getRegisterNumber() : null)
                .build();

        if (individual != null && individual.getUserDetails() != null) {
            attendanceStaffIndexV1.setStaffName(individual.getName());

            Map<String, String> userInfoMap = userService.getUserInfo(staff.getTenantId(), individual.getUserUuid());
            attendanceStaffIndexV1.setUserName(userInfoMap.get(USERNAME));
            attendanceStaffIndexV1.setRole(userInfoMap.get(ROLE));

            BoundaryHierarchyResult boundaryHierarchyResult = getBoundaryHierarchyByCodeOrProjectId(individual.getUserUuid(), staff.getTenantId());
            attendanceStaffIndexV1.setBoundaryHierarchyCode(boundaryHierarchyResult.getBoundaryHierarchyCode());
            attendanceStaffIndexV1.setBoundaryHierarchy(boundaryHierarchyResult.getBoundaryHierarchy());
            commonUtils.addProjectDetailsForUserIdAndTenantId(attendanceStaffIndexV1,
                    individual.getUserUuid(),
                    staff.getTenantId());
        }

        return attendanceStaffIndexV1;
    }

    public AttendanceRegisterIndexV1 transformRegister(AttendanceRegister attendanceRegister) {
        List<String> attendeesIndIds = attendanceRegister.getAttendees().stream().map(IndividualEntry::getIndividualId).collect(Collectors.toList());
        List<String> staffsIndIds = attendanceRegister.getStaff().stream().map(StaffPermission::getUserId).collect(Collectors.toList());

        Map<String, String> attendeesUserIds = attendanceRegisterService.fetchAttendeesInfo(attendeesIndIds, attendanceRegister.getTenantId());
        Map<String, String> staffsUserIds = attendanceRegisterService.fetchAttendeesInfo(staffsIndIds, attendanceRegister.getTenantId());

        Map<String, Map<String, String>> attendeesInfo = new HashMap<>();
        List<String> attendeeUserIds = new ArrayList<>(attendeesUserIds.values());

        for (String userId : attendeeUserIds) {
            Map<String, String> userInfoMap = userService.getUserInfo(attendanceRegister.getTenantId(), userId);
            attendeesInfo.put(userId, userInfoMap);
        }

        Map<String, Map<String, String>> staffsInfo = new HashMap<>();
        List<String> staffUserIds = new ArrayList<>(staffsUserIds.values());

        for (String userId: staffUserIds) {
            Map<String, String> userInfoMap = userService.getUserInfo(attendanceRegister.getTenantId(), userId);
            staffsInfo.put(userId, userInfoMap);
        }

        BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(attendanceRegister.getReferenceId(), attendanceRegister.getTenantId());

        AttendanceRegisterIndexV1 attendanceRegisterIndexV1 = AttendanceRegisterIndexV1.builder()
                .attendanceRegister(attendanceRegister)
                .attendeesInfo(attendeesInfo)
                .staffsInfo(staffsInfo)
                .attendeesCount((long) attendeesIndIds.size())
                .staffsCount((long) staffsIndIds.size())
                .transformerTimeStamp(commonUtils.getTimeStampFromEpoch(System.currentTimeMillis()))
                .boundaryHierarchy(boundaryHierarchyResult.getBoundaryHierarchy())
                .boundaryHierarchyCode(boundaryHierarchyResult.getBoundaryHierarchyCode())
                .build();
        return attendanceRegisterIndexV1;
    }

    private BoundaryHierarchyResult getBoundaryHierarchyByCodeOrProjectId(String createdBy, String tenantId) {
        BoundaryHierarchyResult boundaryHierarchyResult = new BoundaryHierarchyResult();

        ProjectInfo projectInfo = commonUtils.projectDetailsFromUserId(createdBy,tenantId);
        if (ObjectUtils.isNotEmpty(projectInfo) && StringUtils.isNotEmpty(projectInfo.getProjectId())) {
            String projectId = projectInfo.getProjectId();
            boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(projectId, tenantId);
        }

        return boundaryHierarchyResult;
    }

}

