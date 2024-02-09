package org.egov.transformer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectStaff;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ProjectStaffIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.ROLE;
import static org.egov.transformer.Constants.USERNAME;

@Slf4j
public abstract class ProjectStaffTransformationService implements TransformationService<ProjectStaff> {
    protected final ProjectStaffIndexV1Transformer transformer;

    protected final Producer producer;

    protected final TransformerProperties properties;


    @Autowired
    protected ProjectStaffTransformationService(ProjectStaffIndexV1Transformer transformer,
                                                Producer producer, TransformerProperties properties) {
        this.transformer = transformer;
        this.producer = producer;
        this.properties = properties;
    }

    @Override
    public void transform(List<ProjectStaff> payloadList) {
        log.info("transforming for ids {}", payloadList.stream()
                .map(ProjectStaff::getId).collect(Collectors.toList()));
        List<ProjectStaffIndexV1> transformedPayloadList = payloadList.stream()
                .map(transformer::transform)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        log.info("transformation successful");
        producer.push(getTopic(),
                transformedPayloadList);
    }

    public abstract String getTopic();

    @Override
    public Operation getOperation() {
        return Operation.PROJECT_STAFF;
    }

    @Component
    static class ProjectStaffIndexV1Transformer implements
            Transformer<ProjectStaff, ProjectStaffIndexV1> {
        private final ProjectService projectService;

        private final UserService userService;

        private final CommonUtils commonUtils;

        @Autowired
        ProjectStaffIndexV1Transformer(ProjectService projectService, UserService userService, CommonUtils commonUtils) {
            this.projectService = projectService;
            this.userService = userService;
            this.commonUtils = commonUtils;
        }

        @Override
        public List<ProjectStaffIndexV1> transform(ProjectStaff projectStaff) {
            String tenantId = projectStaff.getTenantId();
            String projectId = projectStaff.getProjectId();
            Project project = projectService.getProject(projectId, tenantId);
            String projectTypeId = project.getProjectTypeId();
            Map<String, String> boundaryLabelToNameMap = projectService
                    .getBoundaryLabelToNameMapByProjectId(projectStaff.getProjectId(), projectStaff.getTenantId());
            log.info("boundary labels {}", boundaryLabelToNameMap.toString());
            Map<String, String> userInfoMap = userService.getUserInfo(projectStaff.getTenantId(), projectStaff.getUserId());
            List<ProjectStaffIndexV1> projectStaffIndexV1List = new ArrayList<>();
            ProjectStaffIndexV1 projectStaffIndexV1 = ProjectStaffIndexV1.builder()
                    .id(projectStaff.getId())
                    .projectId(projectId)
                    .userId(projectStaff.getUserId())
                    .userName(userInfoMap.get(USERNAME))
                    .role(userInfoMap.get(ROLE))
                    .taskDates(commonUtils.getProjectDatesList(project.getStartDate(), project.getEndDate()))
                    .createdTime(projectStaff.getAuditDetails().getCreatedTime())
                    .createdBy(projectStaff.getAuditDetails().getCreatedBy())
                    .lastModifiedBy(projectStaff.getAuditDetails().getLastModifiedBy())
                    .lastModifiedTime(projectStaff.getAuditDetails().getLastModifiedTime())
                    .build();

            ObjectNode boundaryHierarchy = (ObjectNode) commonUtils.getBoundaryHierarchy(tenantId, projectTypeId, boundaryLabelToNameMap);
            projectStaffIndexV1.setBoundaryHierarchy(boundaryHierarchy);
            projectStaffIndexV1List.add(projectStaffIndexV1);
            return projectStaffIndexV1List;
        }
    }
}
