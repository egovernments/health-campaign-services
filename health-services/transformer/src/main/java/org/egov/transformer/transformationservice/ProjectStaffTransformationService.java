package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.ProjectStaff;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.ProjectDetails;
import org.egov.transformer.models.downstream.ProjectInfo;
import org.egov.transformer.models.downstream.ProjectStaffIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.BoundaryService;
import org.egov.transformer.service.ProjectFactoryService;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;


import static org.egov.transformer.Constants.*;
import static org.egov.transformer.Constants.CITY;

@Slf4j
@Component
public class ProjectStaffTransformationService {
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final CommonUtils commonUtils;
    private final ProjectService projectService;
    private final UserService userService;
    private final BoundaryService boundaryService;
    private final ProjectFactoryService projectFactoryService;

    public ProjectStaffTransformationService(TransformerProperties transformerProperties, Producer producer, CommonUtils commonUtils, ProjectService projectService, UserService userService, BoundaryService boundaryService, ProjectFactoryService projectFactoryService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.commonUtils = commonUtils;
        this.projectService = projectService;
        this.userService = userService;
        this.boundaryService = boundaryService;
        this.projectFactoryService = projectFactoryService;
    }

    public void transform(List<ProjectStaff> projectStaffList) {
        if (CollectionUtils.isEmpty(projectStaffList)) {
            return;
        }
        log.info("transforming for STAFF id's {}", projectStaffList.stream()
                .map(ProjectStaff::getId).collect(Collectors.toList()));

        String tenantId = projectStaffList.get(0).getTenantId();

        Set<String> userIds = projectStaffList.stream().map(ProjectStaff::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        userService.preWarmUserInfo(userIds, tenantId);

        Set<String> projectIds = projectStaffList.stream().map(ProjectStaff::getProjectId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        // Served from the short lived Redis cache where possible; only uncached projects are searched, and
        // that same search warms the ProjectInfo cache the record loop below uses.
        Map<String, ProjectDetails> projectDetailsById = projectService.getProjectDetails(projectIds, tenantId);

        String topic = transformerProperties.getTransformerProducerBulkProjectStaffIndexV1Topic();
        List<ProjectStaffIndexV1> projectStaffIndexV1List = projectStaffList.stream()
                .map(projectStaff -> transform(projectStaff, projectDetailsById))
                .collect(Collectors.toList());
        log.info("transformation success for STAFF id's {}", projectStaffIndexV1List.stream()
                .map(ProjectStaffIndexV1::getId)
                .collect(Collectors.toList()));
        producer.push(topic, projectStaffIndexV1List);
    }

    private ProjectStaffIndexV1 transform(ProjectStaff projectStaff, Map<String, ProjectDetails> projectDetailsById) {
        String tenantId = projectStaff.getTenantId();
        String projectId = projectStaff.getProjectId();
        ProjectInfo projectInfo = projectService.getProjectInfoByProjectId(projectId, tenantId);
        // EMPTY rather than null, so a project the search did not return cannot NPE the whole record.
        ProjectDetails projectDetails = projectDetailsById.getOrDefault(projectId, ProjectDetails.EMPTY);
        String localityCode = projectDetails.getLocalityCode();

        // Resolving straight from the locality code skips the project lookup the by-project-id path does.
        BoundaryHierarchyResult boundaryHierarchyResult = localityCode != null
                ? boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, tenantId, projectInfo.getHierarchyType())
                : boundaryService.getBoundaryHierarchyWithProjectId(projectId, tenantId);

        Map<String, String> userInfoMap = userService.getUserInfo(projectStaff.getTenantId(), projectStaff.getUserId());
        JsonNode additionalDetails = projectDetails.getAdditionalDetails();
        ProjectStaffIndexV1 projectStaffIndexV1 = ProjectStaffIndexV1.builder()
                .id(projectStaff.getId())
                .userId(projectStaff.getUserId())
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .taskDates(projectDetails.getTaskDates())
                .createdTime(projectStaff.getAuditDetails().getCreatedTime())
                .createdBy(projectStaff.getAuditDetails().getCreatedBy())
                .additionalDetails(additionalDetails)
                .boundaryHierarchy(boundaryHierarchyResult.getBoundaryHierarchy())
                .boundaryHierarchyCode(boundaryHierarchyResult.getBoundaryHierarchyCode())
                .localityCode(localityCode)
                .isDeleted(projectStaff.getIsDeleted())
                .additionalFields(projectStaff.getAdditionalFields())
                .build();
        projectStaffIndexV1.setProjectInfo(projectInfo);
        return projectStaffIndexV1;
    }
}
