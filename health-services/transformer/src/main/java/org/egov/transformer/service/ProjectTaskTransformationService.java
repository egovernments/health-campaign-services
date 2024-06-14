package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.Task;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ProjectTaskIndexV1;
import org.egov.common.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public abstract class ProjectTaskTransformationService implements TransformationService<Task> {
    protected final ProjectTaskIndexV1Transformer transformer;

    protected final Producer producer;

    protected final TransformerProperties properties;

    @Autowired
    protected ProjectTaskTransformationService(ProjectTaskIndexV1Transformer transformer,
                                               Producer producer, TransformerProperties properties) {
        this.transformer = transformer;
        this.producer = producer;
        this.properties = properties;
    }

    @Override
    public void transform(List<Task> payloadList) {
        log.info("transforming for ids {}", payloadList.stream()
                .map(Task::getId).collect(Collectors.toList()));
        List<ProjectTaskIndexV1> transformedPayloadList = payloadList.stream()
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
        return Operation.TASK;
    }

    @Component
    static class ProjectTaskIndexV1Transformer implements
            Transformer<Task, ProjectTaskIndexV1> {
        private final ProjectService projectService;
        private final TransformerProperties properties;

        @Autowired
        ProjectTaskIndexV1Transformer(ProjectService projectService,  TransformerProperties properties) {
            this.projectService = projectService;
            this.properties = properties;
        }

        @Override
        public List<ProjectTaskIndexV1> transform(Task task) {
            Map<String, String> boundaryLabelToNameMap = null;
            if (task.getAddress().getLocality() != null && task.getAddress().getLocality().getCode() != null) {
                boundaryLabelToNameMap = projectService
                        .getBoundaryCodeToNameMap(task.getAddress().getLocality().getCode(), task.getTenantId());
            } else {
                boundaryLabelToNameMap = projectService
                        .getBoundaryCodeToNameMapByProjectId(task.getProjectId(), task.getTenantId());
            }
            log.info("boundary labels {}", boundaryLabelToNameMap.toString());
            Map<String, String> finalBoundaryLabelToNameMap = boundaryLabelToNameMap;
            String beneficiaryType = projectService.getBeneficiaryType(task.getProjectId(), task.getTenantId());

            List<ProjectTaskIndexV1> taskResourceIndex = null;
            // Check if the task's resources list is not null and not empty
            if(!CollectionUtils.isEmpty(task.getResources())) {
                taskResourceIndex = task.getResources().stream().map(r ->
                        ProjectTaskIndexV1.builder()
                                .id(r.getId())
                                .taskId(task.getId())
                                .taskType("DELIVERY")
                                .projectId(task.getProjectId())
                                .startDate(task.getActualStartDate())
                                .endDate(task.getActualEndDate())
                                .productVariant(r.getProductVariantId())
                                .isDelivered(r.getIsDelivered())
                                .quantity(r.getQuantity())
                                .status(task.getStatus())
                                .deliveredTo(beneficiaryType)
                                .deliveryComments(r.getDeliveryComment())
                                .province(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getProvince()) : null)
                                .district(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getDistrict()) : null)
                                .administrativeProvince(finalBoundaryLabelToNameMap != null ?
                                        finalBoundaryLabelToNameMap.get(properties.getAdministrativeProvince()) : null)
                                .locality(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getLocality()) : null)
                                .village(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getVillage()) : null)
                                .latitude(task.getAddress().getLatitude())
                                .longitude(task.getAddress().getLongitude())
                                .createdTime(task.getAuditDetails().getCreatedTime())
                                .createdBy(task.getAuditDetails().getCreatedBy())
                                .lastModifiedTime(task.getAuditDetails().getLastModifiedTime())
                                .lastModifiedBy(task.getAuditDetails().getLastModifiedBy())
                                .isDeleted(task.getIsDeleted())
                                .build()
                ).collect(Collectors.toList());
            } else {
                taskResourceIndex = new ArrayList<>();
                taskResourceIndex.add(
                        ProjectTaskIndexV1.builder()
                                .id(UUID.randomUUID().toString())
                                .taskId(task.getId())
                                .taskType("DELIVERY")
                                .projectId(task.getProjectId())
                                .startDate(task.getActualStartDate())
                                .endDate(task.getActualEndDate())
                                .productVariant(null)
                                .isDelivered(false)
                                .status(task.getStatus())
                                .quantity(null)
                                .deliveredTo(beneficiaryType)
                                .deliveryComments(null)
                                .province(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getProvince()) : null)
                                .district(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getDistrict()) : null)
                                .administrativeProvince(finalBoundaryLabelToNameMap != null ?
                                        finalBoundaryLabelToNameMap.get(properties.getAdministrativeProvince()) : null)
                                .locality(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getLocality()) : null)
                                .village(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getVillage()) : null)
                                .latitude(task.getAddress().getLatitude())
                                .longitude(task.getAddress().getLongitude())
                                .createdTime(task.getAuditDetails().getCreatedTime())
                                .createdBy(task.getAuditDetails().getCreatedBy())
                                .lastModifiedTime(task.getAuditDetails().getLastModifiedTime())
                                .lastModifiedBy(task.getAuditDetails().getLastModifiedBy())
                                .isDeleted(task.getIsDeleted())
                                .build()
                );
            }

            return taskResourceIndex;
        }
    }
}
