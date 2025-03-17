package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.individual.Individual;
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
import org.egov.transformer.service.IndividualService;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
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
    private final ObjectMapper objectMapper;

    private final CommonUtils commonUtils;
    private final ProjectService projectService;

    private final AttendanceRegisterService attendanceRegisterService;
    private final IndividualService individualService;

    public AttendanceTransformationService(TransformerProperties transformerProperties, Producer producer, UserService userService, BoundaryService boundaryService, ObjectMapper objectMapper, CommonUtils commonUtils, AttendanceRegisterService attendanceRegisterService, ProjectService projectService, IndividualService individualService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.userService = userService;
        this.boundaryService = boundaryService;
        this.objectMapper = objectMapper;
        this.commonUtils = commonUtils;
        this.attendanceRegisterService = attendanceRegisterService;
        this.projectService = projectService;
        this.individualService = individualService;
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

    public void transformRegister(List<AttendanceRegister> attendanceRegisterList) {
        log.info("transforming for attendanceRegister id's {}", attendanceRegisterList.stream()
                .map(AttendanceRegister::getId).collect(Collectors.toList()));
        String topic = transformerProperties.getTransformerProducerAttendanceRegisterIndexV1Topic();
        List<AttendanceRegisterIndexV1> attendanceRegisterIndexV1List = attendanceRegisterList.stream()
                .map(this::transformRegister)
                .collect(Collectors.toList());
        log.info("transformation success for attendanceRegister id's {}", attendanceRegisterIndexV1List.stream()
                .map(AttendanceRegisterIndexV1::getAttendanceRegister)
                .map(AttendanceRegister::getId)
                .collect(Collectors.toList()));
        producer.push(topic, attendanceRegisterIndexV1List);
    }

    public AttendanceLogIndexV1 transform(AttendanceLog attendanceLog, AttendanceRegister attendanceRegister) {
        Name attendeeName = attendanceRegisterService.fetchAttendeesInfo(
                        Collections.singletonList(attendanceLog.getIndividualId()),
                        attendanceLog.getTenantId())
                .get(attendanceLog.getIndividualId());
        Map<String, String> userInfoMap = userService.getUserInfo(attendanceLog.getTenantId(), attendanceLog.getAuditDetails().getCreatedBy());

        BoundaryHierarchyResult boundaryHierarchyResult = getBoundaryHierarchyByCodeOrProjectId(attendanceLog.getAdditionalDetails(), attendanceLog.getAuditDetails().getCreatedBy(), attendanceLog.getTenantId());
        Map<String, String> boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
        Map<String, String> boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();

        Individual individual = individualService.getIndividualById(attendanceLog.getIndividualId(), attendanceLog.getTenantId());
        String individualUsername;
        String projectTypeId = null;
        if(individual.getUserDetails() != null) {
            individualUsername = individual.getUserDetails().getUsername();
            attendanceLog.setUserName(individualUsername);
        }
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        String projectIdProjectTypeId = commonUtils.projectDetailsFromUserId(attendanceLog.getAuditDetails().getCreatedBy(), attendanceLog.getTenantId());

        if (!StringUtils.isEmpty(projectIdProjectTypeId)) {
            projectTypeId = projectIdProjectTypeId.split(":")[1];
        }
        String cycleIndex = commonUtils.fetchCycleIndex(attendanceLog.getTenantId(), projectTypeId, attendanceLog.getAuditDetails());
        additionalDetails.put(CYCLE_INDEX, cycleIndex);
        additionalDetails.put(PROJECT_TYPE_ID, projectTypeId);

        AttendanceLogIndexV1 attendanceLogIndexV1 = AttendanceLogIndexV1.builder()
                .attendanceLog(attendanceLog)
                .attendeeName(attendeeName)
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .attendanceTime(commonUtils.getTimeStampFromEpoch(attendanceLog.getTime().longValue()))
                .registerName(attendanceRegister != null ? attendanceRegister.getName() : null)
                .registerServiceCode(attendanceRegister != null ? attendanceRegister.getServiceCode() : null)
                .registerNumber(attendanceRegister != null ? attendanceRegister.getRegisterNumber() : null)
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .additionalDetails(additionalDetails)
                .build();
        return attendanceLogIndexV1;
    }

    public AttendanceRegisterIndexV1 transformRegister(AttendanceRegister attendanceRegister) {
        Map<String, String> boundaryHierarchy = null;
        Map<String, String> boundaryHierarchyCode = null;
        Map<String, Name> attendeesInfo = null;
        String projectId = attendanceRegister.getReferenceId();
        //Updated this logic as attendee will be empty during register creation
        if (!CollectionUtils.isEmpty(attendanceRegister.getAttendees())) {
            List<String> attendeesIndIds = attendanceRegister.getAttendees().stream().map(IndividualEntry::getIndividualId).collect(Collectors.toList());
            attendeesInfo = attendanceRegisterService.fetchAttendeesInfo(attendeesIndIds, attendanceRegister.getTenantId());
        }
        if (StringUtils.isNotBlank(attendanceRegister.getLocalityCode())) {
            BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(attendanceRegister.getLocalityCode(), attendanceRegister.getTenantId());
            boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
            boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
        } else if (StringUtils.isNotBlank(projectId)) {
            BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(attendanceRegister.getReferenceId(), attendanceRegister.getTenantId());
            boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
            boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
        }
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        if (StringUtils.isNotBlank(projectId)) {
            String projectTypeId = projectService.getProjectTypeIdFromProjectId(projectId, attendanceRegister.getTenantId());
            additionalDetails.put(PROJECT_ID, projectId);
            additionalDetails.put(PROJECT_TYPE_ID, projectTypeId);
        }

        AttendanceRegisterIndexV1 attendanceRegisterIndexV1 = AttendanceRegisterIndexV1.builder()
                .attendanceRegister(attendanceRegister)
                .attendeesInfo(attendeesInfo)
                .transformerTimeStamp(commonUtils.getTimeStampFromEpoch(System.currentTimeMillis()))
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .additionalDetails(additionalDetails)
                .build();
        return attendanceRegisterIndexV1;
    }

    private BoundaryHierarchyResult getBoundaryHierarchyByCodeOrProjectId(JsonNode additionalDetails, String createdBy, String tenantId) {
        BoundaryHierarchyResult boundaryHierarchyResult = new BoundaryHierarchyResult();
        String boundaryCode = commonUtils.getLocalityCodeFromAdditionalFields(null,additionalDetails);
        if (StringUtils.isNotEmpty(boundaryCode)) {
            boundaryHierarchyResult =  boundaryService.getBoundaryHierarchyWithLocalityCode(boundaryCode, tenantId);
        }
        else {
            String projectIdProjectTypeId = commonUtils.projectDetailsFromUserId(createdBy, tenantId);
            if (!StringUtils.isEmpty(projectIdProjectTypeId)) {
                String projectId = projectIdProjectTypeId.split(":")[0];
                boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(projectId, tenantId);
            }
        }
        return boundaryHierarchyResult;
    }

}

