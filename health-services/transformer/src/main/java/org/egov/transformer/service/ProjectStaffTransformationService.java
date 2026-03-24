package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.ProjectStaff;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ProjectStaffIndexV1;
import org.egov.common.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        @Autowired
        ProjectStaffIndexV1Transformer(ProjectService projectService, TransformerProperties properties) {
            this.projectService = projectService;
            this.properties = properties;
        }

        @Override
        public List<ProjectStaffIndexV1> transform(ProjectStaff projectStaff) {
            Map<String, String> boundaryLabelToNameMap = projectService
                    .getBoundaryCodeToNameMapByProjectId(projectStaff.getProjectId(), projectStaff.getTenantId());
            log.info("boundary labels {}", boundaryLabelToNameMap.toString());
            return Collections.singletonList(ProjectStaffIndexV1.builder()
                    .id(projectStaff.getId())
                    .projectId(projectStaff.getProjectId())
                    .userId(projectStaff.getUserId())
                    .province(boundaryLabelToNameMap.get(properties.getProvince()))
                    .district(boundaryLabelToNameMap.get(properties.getDistrict()))
                    .administrativeProvince(boundaryLabelToNameMap.get(properties.getAdministrativeProvince()))
                    .locality(boundaryLabelToNameMap.get(properties.getLocality()))
                    .village(boundaryLabelToNameMap.get(properties.getVillage()))
                    .createdTime(projectStaff.getAuditDetails().getCreatedTime())
                    .createdBy(projectStaff.getAuditDetails().getCreatedBy())
                    .lastModifiedBy(projectStaff.getAuditDetails().getLastModifiedBy())
                    .lastModifiedTime(projectStaff.getAuditDetails().getLastModifiedTime())
                    .build());
        }
    }
}
