package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.attendance.AttendanceRegister;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.MusterRollIndexV1;
import org.egov.transformer.models.musterRoll.MusterRoll;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.AttendanceRegisterService;
import org.egov.transformer.service.BoundaryService;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class MusterRollTransformationService {

    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final ObjectMapper objectMapper;
    private final BoundaryService boundaryService;
    private final AttendanceRegisterService attendanceRegisterService;
    private final CommonUtils commonUtils;
    private final ProjectService projectService;

    public MusterRollTransformationService(TransformerProperties transformerProperties, Producer producer, ObjectMapper objectMapper, BoundaryService boundaryService, AttendanceRegisterService attendanceRegisterService, CommonUtils commonUtils, ProjectService projectService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.boundaryService = boundaryService;
        this.attendanceRegisterService = attendanceRegisterService;
        this.commonUtils = commonUtils;
        this.projectService = projectService;
    }

    public void transform(MusterRoll musterRoll){
        log.info("transforming for MUSTER ROLL id's {}", musterRoll.getMusterRollNumber());
        String topic = transformerProperties.getTransformerProducerMusterRollIndexV1Topic();
        MusterRollIndexV1 musterRollIndexV1 = transformMusterRoll(musterRoll);
        log.info("transformation success for MUSTER ROLL id's {}", musterRollIndexV1.getMusterRoll().getMusterRollNumber());
        producer.push(topic, musterRollIndexV1);

    }

    public MusterRollIndexV1 transformMusterRoll(MusterRoll musterRoll){
        Map<String, String> boundaryHierarchy = null;
        Map<String, String> boundaryHierarchyCode = null;
        String attendanceRegisterId = musterRoll.getRegisterId();
        AttendanceRegister attendanceRegister = attendanceRegisterService.findAttendanceRegisterById(attendanceRegisterId, musterRoll.getTenantId(), musterRoll.getAuditDetails().getCreatedBy());
        String projectId = null;
        if (ObjectUtils.isNotEmpty(attendanceRegister)) {
            projectId = attendanceRegister.getReferenceId();
            if (StringUtils.isNotBlank(attendanceRegister.getLocalityCode())) {
                BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(attendanceRegister.getLocalityCode(), attendanceRegister.getTenantId());
                boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
                boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
            } else if (StringUtils.isNotBlank(projectId)) {
                BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(attendanceRegister.getReferenceId(), attendanceRegister.getTenantId());
                boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
                boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
            }
        }
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        if (StringUtils.isNotBlank(projectId)) {
            String projectTypeId = projectService.getProjectTypeIdFromProjectId(projectId, musterRoll.getTenantId());
            additionalDetails.put(PROJECT_ID, projectId);
            additionalDetails.put(PROJECT_TYPE_ID, projectTypeId);
        }

        return MusterRollIndexV1.builder()
                .musterRoll(musterRoll)
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .taskDates(commonUtils.getDateFromEpoch(musterRoll.getAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(musterRoll.getAuditDetails().getLastModifiedTime()))
                .additionalDetails(additionalDetails)
                .build();

    }
}
