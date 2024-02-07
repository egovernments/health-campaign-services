package org.egov.transformer.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.models.household.Household;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.Task;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ProjectTaskIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
        private final IndividualService individualService;
        private UserService userService;
        @Autowired
        ProjectTaskIndexV1Transformer(ProjectService projectService, TransformerProperties properties,
                                      HouseholdService householdService, CommonUtils commonUtils, UserService userService, IndividualService individualService) {
            this.projectService = projectService;
            this.properties = properties;
            this.householdService = householdService;
            this.commonUtils = commonUtils;
            this.userService = userService;
            this.individualService = individualService;
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
            String clientReferenceId = projectBeneficiaries.get(0).getClientReferenceId();
            Date individualDOB = individualService.findIndividualByClientReferenceId(clientReferenceId, tenantId);

            individualDOB = (individualDOB != null) ? individualDOB :null;
            int individualAgeInMonths = (individualDOB != null)
                    ? commonUtils.calculateAgeInMonthsFromDOB(individualDOB)
                    : 0;
            long individualDOBInEpoch= individualDOB!=null?individualDOB.getTime():0;
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
            int deliveryCount = (int) Math.round((Double)(memberCount/ properties.getProgramMandateDividingFactor()));
            final boolean isMandateComment = deliveryCount > properties.getProgramMandateLimit();

            log.info("member count is {}", memberCount);

            List<User> users = userService.getUsers(task.getTenantId(), task.getAuditDetails().getCreatedBy());
            String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(task.getAuditDetails().getCreatedTime());
            List<String> taskDates = new ArrayList<>();
            taskDates.add(syncedTimeStamp.substring(0, syncedTimeStamp.indexOf('T')));
            return task.getResources().stream().map(r ->
                    ProjectTaskIndexV1.builder()
                            .id(r.getId())
                            .age(individualAgeInMonths)
                            .dateOfBirth(individualDOBInEpoch)
                            .taskId(task.getId())
                            .clientReferenceId(r.getClientReferenceId())
                            .tenantId(tenantId)
                            .taskType("DELIVERY")
                            .projectId(task.getProjectId())
                            .startDate(task.getActualStartDate())
                            .taskDates(taskDates)
                            .userName(userService.getUserName(users,task.getAuditDetails().getCreatedBy()))
                            .role(userService.getStaffRole(task.getTenantId(),users))
                            .endDate(task.getActualEndDate())
                            .productVariant(r.getProductVariantId())
                            .isDelivered(r.getIsDelivered())
                            .quantity(r.getQuantity())
                            .deliveredTo("HOUSEHOLD")
                            .deliveryComments(r.getDeliveryComment() != null ? r.getDeliveryComment() : isMandateComment ? properties.getProgramMandateComment() : null)
                            .province(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getProvince()) : null)
                            .district(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getDistrict()) : null)
                            .administrativeProvince(finalBoundaryLabelToNameMap != null ?
                                    finalBoundaryLabelToNameMap.get(properties.getAdministrativeProvince()) : null)
                            .locality(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getLocality()) : null)
//                            .healthFacility(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getHealthFacility()) : null)
                            .village(finalBoundaryLabelToNameMap != null ? finalBoundaryLabelToNameMap.get(properties.getVillage()) : null)
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
                            .build()
            ).collect(Collectors.toList());
        }
    }
}
