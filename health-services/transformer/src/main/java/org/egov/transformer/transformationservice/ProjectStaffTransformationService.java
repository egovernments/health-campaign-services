package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectStaff;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.ProjectStaffIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.BoundaryService;
import org.egov.transformer.service.ProjectFactoryService;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
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
        log.info("transforming for STAFF id's {}", projectStaffList.stream()
                .map(ProjectStaff::getId).collect(Collectors.toList()));
        String topic = transformerProperties.getTransformerProducerBulkProjectStaffIndexV1Topic();
        List<ProjectStaffIndexV1> projectStaffIndexV1List = projectStaffList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        log.info("transformation success for STAFF id's {}", projectStaffIndexV1List.stream()
                .map(ProjectStaffIndexV1::getId)
                .collect(Collectors.toList()));
        producer.push(topic, projectStaffIndexV1List);
    }

    private ProjectStaffIndexV1 transform(ProjectStaff projectStaff) {
        String tenantId = projectStaff.getTenantId();
        String projectId = projectStaff.getProjectId();
        Project project = projectService.getProject(projectId, tenantId);
        String projectTypeId = project.getProjectTypeId();
        String localityCode;
        if (project.getAddress() != null) {
            localityCode = project.getAddress().getBoundary() != null ?
                    project.getAddress().getBoundary() :
                    project.getAddress().getLocality() != null ?
                            project.getAddress().getLocality().getCode() :
                            null;
        } else {
            localityCode = null;
        }
        String campaignId = null;
        if (StringUtils.isNotBlank(project.getReferenceID())) {
            campaignId = projectFactoryService.getCampaignIdFromCampaignNumber(
                    project.getTenantId(), true, project.getReferenceID()
            );
        }
        BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(projectId, tenantId);
        Map<String, String> userInfoMap = userService.getUserInfo(projectStaff.getTenantId(), projectStaff.getUserId());
        JsonNode additionalDetails = projectService.fetchProjectAdditionalDetails(tenantId, projectId);
        ProjectStaffIndexV1 projectStaffIndexV1 = ProjectStaffIndexV1.builder()
                .id(projectStaff.getId())
                .userId(projectStaff.getUserId())
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .taskDates(commonUtils.getProjectDatesList(project.getStartDate(), project.getEndDate()))
                .createdTime(projectStaff.getAuditDetails().getCreatedTime())
                .createdBy(projectStaff.getAuditDetails().getCreatedBy())
                .additionalDetails(additionalDetails)
                .boundaryHierarchy(boundaryHierarchyResult.getBoundaryHierarchy())
                .boundaryHierarchyCode(boundaryHierarchyResult.getBoundaryHierarchyCode())
                .localityCode(localityCode)
                .isDeleted(projectStaff.getIsDeleted())
                .build();
        projectStaffIndexV1.setProjectInfo(projectId, project.getProjectType(), projectTypeId, project.getName());
        projectStaffIndexV1.setCampaignNumber(project.getReferenceID());
        projectStaffIndexV1.setCampaignId(campaignId);
        return projectStaffIndexV1;
    }
}
