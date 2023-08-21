package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.Household;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.Task;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ProjectTaskIndexV1;
import org.egov.transformer.models.upstream.DeliveryConfiguration;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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
        private final HouseholdService householdService;

        @Autowired
        ProjectTaskIndexV1Transformer(ProjectService projectService, TransformerProperties properties,
                                      HouseholdService householdService) {
            this.projectService = projectService;
            this.properties = properties;
            this.householdService = householdService;
        }

        @Override
        public List<ProjectTaskIndexV1> transform(Task task) {
            Map<String, String> boundaryLabelToNameMap = null;
            String tenantId = task.getTenantId();
            if (task.getAddress().getLocality() != null && task.getAddress().getLocality().getCode() != null) {
                boundaryLabelToNameMap = projectService
                        .getBoundaryLabelToNameMap(task.getAddress().getLocality().getCode(), tenantId);
            } else {
                boundaryLabelToNameMap = projectService
                        .getBoundaryLabelToNameMapByProjectId(task.getProjectId(), tenantId);
            }
            log.info("boundary labels {}", boundaryLabelToNameMap.toString());
            Map<String, String> finalBoundaryLabelToNameMap = boundaryLabelToNameMap;

            // fetch project beenficiary and household
            String projectBeneficiaryClientReferenceId = task.getProjectBeneficiaryClientReferenceId();
            log.info("get member count for project beneficiary client reference id {}",
                    projectBeneficiaryClientReferenceId);

            ProjectBeneficiary projectBeneficiary = null;
            Household household = null;
            Integer numberOfMembers = 0;

            List<ProjectBeneficiary> projectBeneficiaries = projectService
                    .searchBeneficiary(projectBeneficiaryClientReferenceId, tenantId);

            if (!CollectionUtils.isEmpty(projectBeneficiaries)) {
                projectBeneficiary = projectBeneficiaries.get(0);
                List<Household> households = householdService.searchHousehold(projectBeneficiary
                        .getBeneficiaryClientReferenceId(), tenantId);
                if (!CollectionUtils.isEmpty(households)) {
                    household = households.get(0);
                    numberOfMembers = household.getMemberCount();
                }
            }

            final Integer memberCount = numberOfMembers;
            final ProjectBeneficiary finalProjectBeneficiary = projectBeneficiary;
            final Household finalHousehold = household;

            log.info("member count is {}", memberCount);

            DeliveryConfiguration configuration = projectService.getProgramMandateValues(tenantId);
            String programMandateComment = projectService.getProgramMandateComment(tenantId);
            final boolean isMandateComment = isMandateComment(memberCount, configuration);

            return task.getResources().stream().map(r ->
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
                            .deliveredTo("HOUSEHOLD")
                            .deliveryComments(r.getDeliveryComment() != null ? r.getDeliveryComment() : isMandateComment ? programMandateComment : null)
                            .province(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getProvince()) : null)
                            .district(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getDistrict()) : null)
                            .administrativeProvince(finalBoundaryLabelToNameMap != null ?
                                    finalBoundaryLabelToNameMap.get(properties.getAdministrativeProvince()) : null)
                            .locality(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getLocality()) : null)
                            .village(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getVillage()) : null)
                            .latitude(task.getAddress().getLatitude())
                            .longitude(task.getAddress().getLongitude())
                            .locationAccuracy(task.getAddress().getLocationAccuracy())
                            .createdTime(task.getAuditDetails().getCreatedTime())
                            .createdBy(task.getAuditDetails().getCreatedBy())
                            .lastModifiedTime(task.getAuditDetails().getLastModifiedTime())
                            .lastModifiedBy(task.getAuditDetails().getLastModifiedBy())
                            .projectBeneficiaryClientReferenceId(projectBeneficiaryClientReferenceId)
                            .isDeleted(task.getIsDeleted())
                            .memberCount(memberCount)
                            .projectBeneficiary(finalProjectBeneficiary)
                            .household(finalHousehold)
                            .build()
            ).collect(Collectors.toList());
        }

        private static boolean isMandateComment(Integer memberCount, DeliveryConfiguration configuration) {
            boolean isMandateComment = false;

            if (memberCount > 0 && configuration != null) {
                Integer programMandate = configuration.getProgramMandateValue();
                Double dividingFactor = configuration.getDividingFactor();
                Integer deliveryCount = (int) Math.round((Double)(memberCount /dividingFactor));
                if (deliveryCount > programMandate) {
                    isMandateComment = true;
                }
            }

            final boolean isMandateCommentFinal = isMandateComment;
            return isMandateCommentFinal;
        }
    }
}
