package org.egov.transformer.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.models.household.Household;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskResource;
import org.egov.transformer.Constants;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ProjectTaskIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
public abstract class ProjectTaskTransformationService implements TransformationService<Task> {
    protected final ProjectTaskIndexV1Transformer transformer;

    protected final Producer producer;

    protected final TransformerProperties properties;
    protected final CommonUtils commonUtils;

    @Autowired
    protected ProjectTaskTransformationService(ProjectTaskIndexV1Transformer transformer,
                                               Producer producer, TransformerProperties properties, CommonUtils commonUtils) {
        this.transformer = transformer;
        this.producer = producer;
        this.properties = properties;
        this.commonUtils = commonUtils;
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
        private final CommonUtils commonUtils;
        private UserService userService;
        @Autowired
        ProjectTaskIndexV1Transformer(ProjectService projectService, TransformerProperties properties,
                                      HouseholdService householdService, CommonUtils commonUtils, UserService userService) {
            this.projectService = projectService;
            this.properties = properties;
            this.householdService = householdService;
            this.commonUtils = commonUtils;
            this.userService = userService;
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
            JsonNode mdmsBoundaryData = projectService.fetchBoundaryData(tenantId, "");
            List<JsonNode> boundaryLevelVsLabel = StreamSupport
                    .stream(mdmsBoundaryData.get(Constants.BOUNDARY_HIERARCHY).spliterator(), false).collect(Collectors.toList());
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
            int deliveryCount = (int) Math.round((Double) (memberCount / properties.getProgramMandateDividingFactor()));
            final boolean isMandateComment = deliveryCount > properties.getProgramMandateLimit();

            log.info("member count is {}", memberCount);

            List<User> users = userService.getUsers(task.getTenantId(), task.getAuditDetails().getCreatedBy());
            String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(task.getAuditDetails().getCreatedTime());

            return task.getResources().stream().map(r ->
                    transformTaskToProjectTaskIndex(r, task, finalBoundaryLabelToNameMap, boundaryLevelVsLabel, tenantId, users, isMandateComment,
                            projectBeneficiaryClientReferenceId, memberCount, finalProjectBeneficiary, finalHousehold, syncedTimeStamp)
            ).collect(Collectors.toList());
        }

        private ProjectTaskIndexV1 transformTaskToProjectTaskIndex(TaskResource taskResource, Task task, Map<String, String> finalBoundaryLabelToNameMap,
                                                                   List<JsonNode> boundaryLevelVsLabel, String tenantId, List<User> users, boolean isMandateComment,
                                                                   String projectBeneficiaryClientReferenceId, Integer memberCount,
                                                                   ProjectBeneficiary finalProjectBeneficiary, Household finalHousehold, String syncedTimeStamp) {
            ProjectTaskIndexV1 projectTaskIndexV1 = ProjectTaskIndexV1.builder()
                    .id(taskResource.getId())
                    .taskId(task.getId())
                    .clientReferenceId(taskResource.getClientReferenceId())
                    .tenantId(tenantId)
                    .taskType("DELIVERY")
                    .projectId(task.getProjectId())
                    .startDate(task.getActualStartDate())
                    .userName(userService.getUserName(users, task.getAuditDetails().getCreatedBy()))
                    .role(userService.getStaffRole(task.getTenantId(), users))
                    .endDate(task.getActualEndDate())
                    .productVariant(taskResource.getProductVariantId())
                    .isDelivered(taskResource.getIsDelivered())
                    .quantity(taskResource.getQuantity())
                    .deliveredTo("HOUSEHOLD")
                    .deliveryComments(taskResource.getDeliveryComment() != null ? taskResource.getDeliveryComment() : isMandateComment ? properties.getProgramMandateComment() : null)
                    .latitude(task.getAddress().getLatitude())
                    .longitude(task.getAddress().getLongitude())
                    .locationAccuracy(task.getAddress().getLocationAccuracy())
                    .createdTime(task.getClientAuditDetails().getCreatedTime())
                    .createdBy(task.getAuditDetails().getCreatedBy())
                    .lastModifiedTime(task.getClientAuditDetails().getLastModifiedTime())
                    .lastModifiedBy(task.getAuditDetails().getLastModifiedBy())
                    .projectBeneficiaryClientReferenceId(projectBeneficiaryClientReferenceId)
                    .isDeleted(task.getIsDeleted())
                    .memberCount(memberCount)
                    .projectBeneficiary(finalProjectBeneficiary)
                    .household(finalHousehold)
                    .clientAuditDetails(task.getClientAuditDetails())
                    .syncedTimeStamp(syncedTimeStamp)
                    .syncedTime(task.getAuditDetails().getCreatedTime())
                    .additionalFields(task.getAdditionalFields())
                    .build();
            //todo verify this
            boundaryLevelVsLabel.forEach(node -> {
                if (node.get(Constants.LEVEL).asInt() > 1) {
                    projectTaskIndexV1.getBoundaryHierarchy().put(node.get(Constants.INDEX_LABEL).asText(), finalBoundaryLabelToNameMap.get(node.get(Constants.INDEX_LABEL).asText()) == null ? null : finalBoundaryLabelToNameMap.get(node.get(Constants.INDEX_LABEL).asText()));
                }
            });
            return projectTaskIndexV1;
        }
    }
}
