package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ProjectIndexV1;
import org.egov.transformer.models.upstream.Project;
import org.egov.transformer.models.upstream.Target;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public abstract class ProjectTransformationService implements TransformationService<Project> {
    protected final ProjectIndexV1Transformer transformer;

    protected final Producer producer;

    protected final TransformerProperties properties;

    @Autowired
    protected ProjectTransformationService(ProjectIndexV1Transformer transformer,
                                           Producer producer, TransformerProperties properties) {
        this.transformer = transformer;
        this.producer = producer;
        this.properties = properties;
    }

    @Override
    public void transform(List<Project> payloadList) {
        log.info("transforming for ids {}", payloadList.stream()
                .map(Project::getId).collect(Collectors.toList()));
        List<ProjectIndexV1> transformedPayloadList = payloadList.stream()
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
        return Operation.PROJECT;
    }

    @Component
    static class ProjectIndexV1Transformer implements
            Transformer<Project, ProjectIndexV1> {
        private final ProjectService projectService;

        @Autowired
        ProjectIndexV1Transformer(ProjectService projectService) {
            this.projectService = projectService;
        }

        @Override
        public List<ProjectIndexV1> transform(Project project) {
            Map<String, String> boundaryLabelToNameMap = projectService
                    .getBoundaryLabelToNameMap(project, project.getAddress().getBoundary());
            log.info("boundary labels {}", boundaryLabelToNameMap.toString());
            List<Target> targets = project.getTargets();
            if (targets == null || targets.isEmpty()) {
                return Collections.emptyList();
            }
            return targets.stream().map(r -> {
                        Long startDate = project.getStartDate();
                        Long endDate = project.getEndDate();
                        Integer targetNo = r.getTargetNo();
                        Integer campaignDurationInDays = null;
                        Integer targetPerDay = null;
                        Long milliSecForOneDay = (long) (24 * 60 * 60 * 1000);
                        if (startDate != null && endDate != null) {
                            campaignDurationInDays = (int) ((endDate - startDate) / milliSecForOneDay);
                            if (targetNo != null && campaignDurationInDays > 0) {
                                targetPerDay = targetNo / campaignDurationInDays;
                            }
                        }

                        List<String> productVariants = projectService.getProducts(project.getTenantId(),
                                project.getProjectTypeId());
                        String productVariant = null;
                        if (productVariants != null && !productVariants.isEmpty()) {
                            productVariant = String.join(",", productVariants);
                        }

                        return ProjectIndexV1.builder()
                                .id(project.getId())
                                .overallTarget(targetNo)
                                .targetPerDay(targetPerDay)
                                .campaignDurationInDays(campaignDurationInDays)
                                .startDate(project.getStartDate())
                                .endDate(project.getEndDate())
                                .productVariant(productVariant)
                                .targetType(r.getBeneficiaryType())
                                .province(boundaryLabelToNameMap.get("Province"))
                                .district(boundaryLabelToNameMap.get("District"))
                                .administrativeProvince(boundaryLabelToNameMap.get("AdministrativeProvince"))
                                .locality(boundaryLabelToNameMap.get("Locality"))
                                .village(boundaryLabelToNameMap.get("Village"))
                                .createdTime(project.getAuditDetails().getCreatedTime())
                                .createdBy(project.getAuditDetails().getCreatedBy())
                                .lastModifiedTime(project.getAuditDetails().getLastModifiedTime())
                                .lastModifiedBy(project.getAuditDetails().getLastModifiedBy())
                                .build();
                    }
            ).collect(Collectors.toList());
        }
    }
}
