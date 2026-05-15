package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.MusterRollIndexV1;
import org.egov.transformer.models.downstream.ProjectInfo;
import org.egov.transformer.models.musterRoll.MusterRoll;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.BillService;
import org.egov.transformer.service.BoundaryService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class MusterRollTransformationService {

    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final UserService userService;
    private final BoundaryService boundaryService;

    private final CommonUtils commonUtils;


    public MusterRollTransformationService(TransformerProperties transformerProperties, Producer producer, UserService userService, BoundaryService boundaryService, CommonUtils commonUtils) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.userService = userService;
        this.boundaryService = boundaryService;
        this.commonUtils = commonUtils;
    }

    public void transform(MusterRoll musterRoll) {

        String musterRollIndexV1Topic = transformerProperties.getTransformerProducerMusterRollIndexV1Topic();
        log.info("transforming MUSTER ROLL for id {}", musterRoll.getId());

        List<MusterRollIndexV1> musterRollIndexV1List = transformMusterRoll(musterRoll);
        producer.push(musterRollIndexV1Topic, musterRollIndexV1List);

        log.info("transformation successful for MUSTER ROLL for id {}", musterRoll.getId());

    }

    public List<MusterRollIndexV1> transformMusterRoll(MusterRoll musterRoll) {

        String tenantId = musterRoll.getTenantId();
        BoundaryHierarchyResult boundaryHierarchyResult = new BoundaryHierarchyResult();
        ProjectInfo projectInfo = commonUtils.projectDetailsFromUserId(musterRoll.getAuditDetails().getCreatedBy(), tenantId);
        if (ObjectUtils.isNotEmpty(projectInfo) && StringUtils.isNotEmpty(projectInfo.getProjectId())) {
            String projectId = projectInfo.getProjectId();
            boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(projectId, tenantId);
        }

        Map<String, String> boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
        Map<String, String> boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();

        Map<String, String> userInfoMap = userService.getUserInfo(musterRoll.getTenantId(), musterRoll.getAuditDetails().getLastModifiedBy());

        List<MusterRollIndexV1> musterRollIndexV1List = new ArrayList<>();

        MusterRollIndexV1 original = buildMusterRollIndex(
                musterRoll,
                userInfoMap,
                boundaryHierarchy,
                boundaryHierarchyCode
        );

        original.setEdited(false);
        musterRollIndexV1List.add(original);
        String editTimestamp = getEditTimestamp(musterRoll.getAdditionalDetails());

        if (editTimestamp != null) {
            MusterRollIndexV1 editedCopy = buildMusterRollIndex(
                    musterRoll,
                    userInfoMap,
                    boundaryHierarchy,
                    boundaryHierarchyCode
            );

            editedCopy.setEdited(true);
            editedCopy.setId(musterRoll.getId() + HYPHEN + editTimestamp);
            musterRollIndexV1List.add(editedCopy);
        }
        return musterRollIndexV1List;
    }

    public String getEditTimestamp(Object additionalDetails) {

        if (additionalDetails == null) return null;
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode editInfo = objectMapper
                .convertValue(additionalDetails, JsonNode.class)
                .path("editInfo");

        if (editInfo.isMissingNode() || editInfo.isNull()) return null;

        JsonNode editedTime = editInfo.get("attendanceUpdatedAtEpochMs");
        if (editedTime != null && !editedTime.isNull()) {
            return editedTime.asText();
        }
        return null;
    }

    private MusterRollIndexV1 buildMusterRollIndex(
            MusterRoll musterRoll,
            Map<String, String> userInfoMap,
            Map<String, String> boundaryHierarchy,
            Map<String, String> boundaryHierarchyCode) {

        MusterRollIndexV1 index = MusterRollIndexV1.builder()
                .id(musterRoll.getId())
                .musterRoll(musterRoll)
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .build();

        commonUtils.addProjectDetailsForUserIdAndTenantId(
                index,
                musterRoll.getAuditDetails().getLastModifiedBy(),
                musterRoll.getTenantId()
        );

        return index;
    }
}

