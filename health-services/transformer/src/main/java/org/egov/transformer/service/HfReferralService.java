package org.egov.transformer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.Project;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.HfReferralIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class HfReferralService {

    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final UserService userService;
    private final ProjectService projectService;
    private final FacilityService facilityService;

    private final CommonUtils commonUtils;

    private final ObjectMapper objectMapper;

    public HfReferralService(TransformerProperties transformerProperties,
                             Producer producer, UserService userService, ProjectService projectService, FacilityService facilityService, CommonUtils commonUtils, ObjectMapper objectMapper) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.userService = userService;
        this.projectService = projectService;
        this.facilityService = facilityService;
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
        Map<String, String> boundaryLabelToNameMap = new HashMap<>();
        String projectId = hfReferral.getProjectId();
        Project project = projectService.getProject(projectId, tenantId);
        String projectTypeId = project.getProjectTypeId();

        boundaryLabelToNameMap = projectService.getBoundaryLabelToNameMapByProjectId(projectId, hfReferral.getTenantId());
        Map<String, String> finalBoundaryLabelToNameMap = boundaryLabelToNameMap;
        ObjectNode boundaryHierarchy = (ObjectNode) commonUtils.getBoundaryHierarchy(tenantId, projectTypeId, finalBoundaryLabelToNameMap);

        Map<String, String> userInfoMap = userService.getUserInfo(tenantId, hfReferral.getClientAuditDetails().getCreatedBy());

        Integer cycleIndex = commonUtils.fetchCycleIndex(tenantId, projectTypeId, hfReferral.getClientAuditDetails());
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        additionalDetails.put(CYCLE_NUMBER, cycleIndex);

        HfReferralIndexV1 hfReferralIndexV1 = HfReferralIndexV1.builder()
                .hfReferral(hfReferral)
                .userName(userInfoMap.get(USERNAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
//                .facilityName(facilityName)
                .boundaryHierarchy(boundaryHierarchy)
                .taskDates(commonUtils.getDateFromEpoch(hfReferral.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(hfReferral.getAuditDetails().getLastModifiedTime()))
                .additionalDetails(additionalDetails)
                .build();

        return hfReferralIndexV1;
    }
}

