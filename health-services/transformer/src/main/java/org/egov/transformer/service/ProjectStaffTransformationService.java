package org.egov.transformer.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.models.project.ProjectStaff;
import org.egov.transformer.Constants;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ProjectStaffIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
        private final TransformerProperties properties;

        private UserService userService;
        @Autowired
        ProjectStaffIndexV1Transformer(ProjectService projectService, TransformerProperties properties,UserService userService) {
            this.projectService = projectService;
            this.properties = properties;
            this.userService = userService;
        }
        @Override
        public List<ProjectStaffIndexV1> transform(ProjectStaff projectStaff) {
            String tenantId = projectStaff.getTenantId();
            JsonNode mdmsBoundaryData = projectService.fetchBoundaryData(tenantId,"");
            List<JsonNode> boundaryLevelVsLabel = StreamSupport
                    .stream(mdmsBoundaryData.get(Constants.BOUNDARY_HIERARCHY).spliterator(), false).collect(Collectors.toList());
            Map<String, String> boundaryLabelToNameMap = projectService
                    .getBoundaryLabelToNameMapByProjectId(projectStaff.getProjectId(), projectStaff.getTenantId());
            log.info("boundary labels {}", boundaryLabelToNameMap.toString());
            List<User> users = userService.getUsers(projectStaff.getTenantId(),projectStaff.getUserId());
            List<ProjectStaffIndexV1> projectStaffIndexV1List = new ArrayList<>();
            ProjectStaffIndexV1 projectStaffIndexV1 = ProjectStaffIndexV1.builder()
                    .id(projectStaff.getId())
                    .projectId(projectStaff.getProjectId())
                    .userId(projectStaff.getUserId())
                    .userName(userService.getUserName(users,projectStaff.getUserId()))
                    .role(userService.getStaffRole(projectStaff.getTenantId(),users))
                    .createdTime(projectStaff.getAuditDetails().getCreatedTime())
                    .createdBy(projectStaff.getAuditDetails().getCreatedBy())
                    .lastModifiedBy(projectStaff.getAuditDetails().getLastModifiedBy())
                    .lastModifiedTime(projectStaff.getAuditDetails().getLastModifiedTime())
                    .build();
            //todo verify this
            boundaryLevelVsLabel.forEach(node->{
                if(node.get(Constants.LEVEL).asInt()>1){
                    projectStaffIndexV1.getBoundaryHierarchy().put(node.get(Constants.INDEX_LABEL).asText(),boundaryLabelToNameMap.get(node.get(Constants.INDEX_LABEL).asText()));
                }
            });
            projectStaffIndexV1List.add(projectStaffIndexV1);
            return projectStaffIndexV1List;
        }
    }
}
