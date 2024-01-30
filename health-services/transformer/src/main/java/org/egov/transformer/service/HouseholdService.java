package org.egov.transformer.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkResponse;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.common.models.household.HouseholdSearchRequest;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectStaff;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.models.downstream.HouseholdIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.ROLE;
import static org.egov.transformer.Constants.USERNAME;

@Component
@Slf4j
public class HouseholdService {
    private final TransformerProperties transformerProperties;
    private final ServiceRequestClient serviceRequestClient;
    private final Producer producer;
    private final UserService userService;
    private final ProjectService projectService;
    private final CommonUtils commonUtils;

    public HouseholdService(TransformerProperties transformerProperties, ServiceRequestClient serviceRequestClient, Producer producer, UserService userService, ProjectService projectService, CommonUtils commonUtils) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
        this.producer = producer;
        this.userService = userService;
        this.projectService = projectService;
        this.commonUtils = commonUtils;
    }

    public List<Household> searchHousehold(String clientRefId, String tenantId) {
        HouseholdSearchRequest request = HouseholdSearchRequest.builder()
                .requestInfo(RequestInfo.builder().
                        userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .household(HouseholdSearch.builder().
                        clientReferenceId(Collections.singletonList(clientRefId)).build())
                .build();
        HouseholdBulkResponse response;
        try {
            StringBuilder uri = new StringBuilder();
            uri.append(transformerProperties.getHouseholdHost())
                    .append(transformerProperties.getHouseholdSearchUrl())
                    .append("?limit=").append(transformerProperties.getSearchApiLimit())
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);
            response = serviceRequestClient.fetchResult(uri,
                    request,
                    HouseholdBulkResponse.class);
        } catch (Exception e) {
            log.error("Error while fetching household for clientRefId: {}. ExceptionDetails: {}", clientRefId, ExceptionUtils.getStackTrace(e));
            return Collections.emptyList();
        }
        return response.getHouseholds();
    }

    public void transform(List<Household> payloadList) {
        String topic = transformerProperties.getTransformerProducerBulkHouseholdIndexV1Topic();
        log.info("transforming for ids {}", payloadList.stream()
                .map(Household::getId).collect(Collectors.toList()));
        List<HouseholdIndexV1> transformedPayloadList = payloadList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        if (!transformedPayloadList.isEmpty()) {
            producer.push(topic, transformedPayloadList);
            log.info("transformation successful");
        }
    }

    public HouseholdIndexV1 transform(Household household) {
        Map<String, String> boundaryLabelToNameMap = null;
        String projectTypeId = null;
        String userId = household.getAuditDetails().getCreatedBy();
        ProjectStaff projectStaff = projectService.searchProjectStaff(userId, household.getTenantId());
        if (projectStaff != null) {
            Project project = projectService.getProject(projectStaff.getProjectId(), household.getTenantId());
            projectTypeId = project != null ? project.getProjectTypeId() : null;
        }
        if (household.getAddress().getLocality() != null && household.getAddress().getLocality().getCode() != null) {
            boundaryLabelToNameMap = projectService
                    .getBoundaryLabelToNameMap(household.getAddress().getLocality().getCode(), household.getTenantId());
        } else {
            boundaryLabelToNameMap = null;
        }
        ObjectNode boundaryHierarchy = (ObjectNode) commonUtils.getBoundaryHierarchy(household.getTenantId(), projectTypeId, boundaryLabelToNameMap);
        Map<String, String> userInfoMap = userService.getUserInfo(household.getTenantId(), household.getAuditDetails().getCreatedBy());

        return HouseholdIndexV1.builder()
                .household(household)
                .userName(userInfoMap.get(USERNAME))
                .role(userInfoMap.get(ROLE))
                .geoPoint(commonUtils.getGeoPoint(household.getAddress()))
                .boundaryHierarchy(boundaryHierarchy)
                .build();
    }
}
