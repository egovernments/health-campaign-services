package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.AdditionalFields;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.ProjectInfo;
import org.egov.transformer.models.downstream.HfReferralIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.BoundaryService;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class HfReferralTransformationService {

    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final UserService userService;
    private final ProjectService projectService;
    private final BoundaryService boundaryService;

    private final CommonUtils commonUtils;

    private final ObjectMapper objectMapper;
    private static final Set<String> ADDITIONAL_DETAILS_INTEGER_FIELDS = new HashSet<>(Arrays.asList(AGE_IN_MONTHS));

    public HfReferralTransformationService(TransformerProperties transformerProperties,
                                           Producer producer, UserService userService, ProjectService projectService, BoundaryService boundaryService, CommonUtils commonUtils, ObjectMapper objectMapper) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.userService = userService;
        this.projectService = projectService;
        this.boundaryService = boundaryService;
        this.commonUtils = commonUtils;
        this.objectMapper = objectMapper;
    }

    public void transform(List<HFReferral> payloadList) {
        String topic = transformerProperties.getTransformerProducerHfReferralIndexV1Topic();
        log.info("transforming for ids {}", payloadList.stream()
                .map(HFReferral::getId).collect(Collectors.toList()));
        List<HfReferralIndexV1> transformedPayloadList = payloadList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        log.info("transformation successful");
        producer.push(topic,
                transformedPayloadList);
    }

    public HfReferralIndexV1 transform(HFReferral hfReferral) {
        String tenantId = hfReferral.getTenantId();
        String projectId = hfReferral.getProjectId();
        ProjectInfo projectInfo = projectService.getProjectInfoByProjectId(projectId, tenantId);
        AdditionalFields additionalFields = hfReferral.getAdditionalFields();
        String localityCode = commonUtils.getLocalityCodeFromAdditionalFields(additionalFields);
        BoundaryHierarchyResult boundaryHierarchyResult = null;
        if(localityCode != null) {
            boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, tenantId, projectInfo.getHierarchyType());
        } else {
            boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(projectId, tenantId);
        }

        Map<String, String> userInfoMap = userService.getUserInfo(tenantId, hfReferral.getClientAuditDetails().getCreatedBy());

        String cycleIndex = projectService.fetchCycleIndexFromProjectAdditionalDetails(tenantId, projectId, projectInfo.getProjectTypeId(), hfReferral.getClientAuditDetails().getCreatedTime());
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        additionalDetails.put(CYCLE_INDEX, cycleIndex);

        AdditionalFields hfReferralAdditionalFields = hfReferral.getAdditionalFields();
        addAdditionalDetails(hfReferralAdditionalFields, additionalDetails);

        HfReferralIndexV1 hfReferralIndexV1 = HfReferralIndexV1.builder()
                .hfReferral(hfReferral)
                .userName(userInfoMap.get(USERNAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .boundaryHierarchy(boundaryHierarchyResult.getBoundaryHierarchy())
                .boundaryHierarchyCode(boundaryHierarchyResult.getBoundaryHierarchyCode())
                .taskDates(commonUtils.getDateFromEpoch(hfReferral.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(hfReferral.getAuditDetails().getLastModifiedTime()))
                .additionalDetails(additionalDetails)
                .build();
        hfReferralIndexV1.setProjectInfo(projectId, projectInfo);

        return hfReferralIndexV1;
    }
    private void addAdditionalDetails(AdditionalFields additionalFields, ObjectNode additionalDetails) {
        additionalFields.getFields().forEach(field -> {
            String key = field.getKey();
            String value = field.getValue();
            if (ADDITIONAL_DETAILS_INTEGER_FIELDS.contains(key)) {
                try {
                    additionalDetails.put(key, Integer.valueOf(value));
                } catch (NumberFormatException e) {
                    log.warn("Invalid number format for key '{}': value '{}'. Storing as null.", key, value);
                    additionalDetails.put(key, (JsonNode) null);
                }
            } else {
                additionalDetails.put(key, value);
            }
        });
    }
}

