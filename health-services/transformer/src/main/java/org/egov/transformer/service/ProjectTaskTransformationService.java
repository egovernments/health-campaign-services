package org.egov.transformer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.models.household.Household;
import org.egov.transformer.Constants;
import org.egov.common.models.project.*;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ProjectTaskIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.egov.transformer.Constants.*;

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

        private final IndividualService individualService;
        private final CommonUtils commonUtils;

        private final ProductService productService;
        private final UserService userService;

        private final ObjectMapper objectMapper;

        @Autowired
        ProjectTaskIndexV1Transformer(ProjectService projectService, TransformerProperties properties,
                                      HouseholdService householdService, IndividualService individualService, CommonUtils commonUtils, UserService userService, ObjectMapper objectMapper, ProductService productService) {
            this.projectService = projectService;
            this.properties = properties;
            this.householdService = householdService;
            this.individualService = individualService;
            this.commonUtils = commonUtils;
            this.productService = productService;
            this.userService = userService;
            this.objectMapper = objectMapper;
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
            Project project = projectService.getProject(task.getProjectId(),tenantId);
            String projectTypeId = project.getProjectTypeId();
            JsonNode mdmsBoundaryData = projectService.fetchBoundaryData(tenantId, null,projectTypeId);
            List<JsonNode> boundaryLevelVsLabel = StreamSupport
                    .stream(mdmsBoundaryData.get(Constants.BOUNDARY_HIERARCHY).spliterator(), false).collect(Collectors.toList());
            log.info("boundary labels {}", boundaryLabelToNameMap.toString());
            Map<String, String> finalBoundaryLabelToNameMap = boundaryLabelToNameMap;

            // fetch project beneficiary
            String projectBeneficiaryClientReferenceId = task.getProjectBeneficiaryClientReferenceId();

            ProjectBeneficiary projectBeneficiary = null;

            List<ProjectBeneficiary> projectBeneficiaries = projectService
                    .searchBeneficiary(projectBeneficiaryClientReferenceId, tenantId);
            Map<String, Object> individualDetails = null;
            if (projectBeneficiaries.size() > 0) {
                individualDetails = individualService.findIndividualByClientReferenceId(projectBeneficiaries.get(0).getClientReferenceId(), tenantId);
            }

            final Map<String, Object> finalIndividualDetails = individualDetails;


            if (!CollectionUtils.isEmpty(projectBeneficiaries)) {
                projectBeneficiary = projectBeneficiaries.get(0);
            }
            final ProjectBeneficiary finalProjectBeneficiary = projectBeneficiary;

            List<User> users = userService.getUsers(task.getTenantId(), task.getAuditDetails().getCreatedBy());
            String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(task.getAuditDetails().getCreatedTime());
            String projectBeneficiaryType = projectService.getProjectBeneficiaryType(task.getTenantId(), projectTypeId);

            return task.getResources().stream().map(r ->
                    transformTaskToProjectTaskIndex(r, task, finalBoundaryLabelToNameMap, boundaryLevelVsLabel, tenantId, users,
                            projectBeneficiaryClientReferenceId, finalProjectBeneficiary, syncedTimeStamp, projectBeneficiaryType)
            ).collect(Collectors.toList());
        }

        private ProjectTaskIndexV1 transformTaskToProjectTaskIndex(TaskResource taskResource, Task task, Map<String, String> finalBoundaryLabelToNameMap,
                                                                   List<JsonNode> boundaryLevelVsLabel, String tenantId, List<User> users,
                                                                   String projectBeneficiaryClientReferenceId,
                                                                   ProjectBeneficiary finalProjectBeneficiary, String syncedTimeStamp, String projectBeneficiaryType) {
            ProjectTaskIndexV1 projectTaskIndexV1 = ProjectTaskIndexV1.builder()
                    .id(taskResource.getId())
                    .taskId(task.getId())
                    .clientReferenceId(taskResource.getClientReferenceId())
                    .tenantId(tenantId)
                    .taskType("DELIVERY")
                    .projectId(task.getProjectId())
                    .userName(userService.getUserName(users, task.getAuditDetails().getCreatedBy()))
                    .role(userService.getStaffRole(task.getTenantId(), users))
                    .productVariant(taskResource.getProductVariantId())
                    .quantity(taskResource.getQuantity())
                    .deliveredTo(projectBeneficiaryType)
                    .deliveryComments(taskResource.getDeliveryComment())
                    .latitude(task.getAddress().getLatitude())
                    .longitude(task.getAddress().getLongitude())
                    .locationAccuracy(task.getAddress().getLocationAccuracy())
                    .createdTime(task.getClientAuditDetails().getCreatedTime())
                    .createdBy(task.getAuditDetails().getCreatedBy())
                    .lastModifiedTime(task.getClientAuditDetails().getLastModifiedTime())
                    .lastModifiedBy(task.getAuditDetails().getLastModifiedBy())
                    .projectBeneficiaryClientReferenceId(projectBeneficiaryClientReferenceId)
                    .memberCount(memberCount)
                    .syncedTimeStamp(syncedTimeStamp)
                    .syncedTime(task.getAuditDetails().getCreatedTime())
                    .additionalFields(task.getAdditionalFields())
                    .dateOfBirth(individualDetails.containsKey(DATE_OF_BIRTH) ? (Long) individualDetails.get(DATE_OF_BIRTH) : null)
                    .age(individualDetails.containsKey(AGE) ? (Integer) individualDetails.get(AGE) : null)
                    .gender(individualDetails.containsKey(GENDER) ? (String) individualDetails.get(GENDER) : null)
                    .individualId(individualDetails.containsKey(INDIVIDUAL_ID) ? (String) individualDetails.get(INDIVIDUAL_ID) : null)
                    .geoPoint(commonUtils.getGeoPoint(task.getAddress()))
                    .build();
            List<String> variantList= new ArrayList<>(Collections.singleton(taskResource.getProductVariantId()));
            String productName = String.join(COMMA, productService.getProductVariantNames(variantList, tenantId));
            projectTaskIndexV1.setProductName(productName);
            if (projectTaskIndexV1.getBoundaryHierarchy() == null) {
                ObjectNode boundaryHierarchy = objectMapper.createObjectNode();
                projectTaskIndexV1.setBoundaryHierarchy(boundaryHierarchy);
            }
            boundaryLevelVsLabel.stream()
                    .filter(node -> node.get(LEVEL).asInt() > 1)
                    .forEach(node -> {
                        String label = node.get(INDEX_LABEL).asText();
                        String name = Optional.ofNullable(finalBoundaryLabelToNameMap.get(node.get(LABEL).asText()))
                                .orElse(null);
                        projectTaskIndexV1.getBoundaryHierarchy().put(label, name);
                    });

            if (HOUSEHOLD.equalsIgnoreCase(projectBeneficiaryType)) {
                //TODO for transforming QR code additionalDetails
                projectTaskIndexV1.setAdditionalFields(task.getAdditionalFields());
                List<Household> households = householdService.searchHousehold(finalProjectBeneficiary
                        .getBeneficiaryClientReferenceId(), tenantId);
                Household household = null;
                Integer numberOfMembers = 0;
                if (!CollectionUtils.isEmpty(households)) {
                    household = households.get(0);
                    numberOfMembers = household.getMemberCount();
                }

                final Integer memberCount = numberOfMembers;
                log.info("member count is {}", memberCount);

                final Household finalHousehold = household;
                int deliveryCount = (int) Math.round((Double) (memberCount / properties.getProgramMandateDividingFactor()));
                final boolean isMandateComment = deliveryCount > properties.getProgramMandateLimit();
                projectTaskIndexV1.setDeliveryComments(taskResource.getDeliveryComment() != null ? taskResource.getDeliveryComment() : isMandateComment ? properties.getProgramMandateComment() : null);


                projectTaskIndexV1.setProjectBeneficiary(finalProjectBeneficiary);
                projectTaskIndexV1.setHousehold(finalHousehold);
                projectTaskIndexV1.setMemberCount(memberCount);

            }

            projectTaskIndexV1.setAdministrationStatus(task.getStatus());
            projectTaskIndexV1.setAdditionalDetails(addAdditionalDetails(task, taskResource));
            return projectTaskIndexV1;
        }



        private ObjectNode addAdditionalDetails(Task task, TaskResource taskResource) {
            ObjectNode additionalDetails = objectMapper.createObjectNode();

            AdditionalFields additionalFields = task.getAdditionalFields();
            if (additionalFields != null) {
                additionalFields.getFields().forEach(field -> {
                    String key = field.getKey();
                    if (DOSE_NUMBER.equalsIgnoreCase(key) || CYCLE_NUMBER.equalsIgnoreCase(key)) {
                        additionalDetails.put(key, getValue(key, additionalFields, Integer.class, null));
                    }
                    else {
                        additionalDetails.put(key, field.getValue());
                    }
                });
            }
            additionalDetails.put(QUANTITY_WASTED, getValue(QUANTITY_WASTED, taskResource.getAdditionalFields(), Integer.class, 0));

            return additionalDetails;
        }


        private <T> T getValue(String key, AdditionalFields additionalFields, Class<T> valueType, T defaultValue) {
            if (additionalFields != null && additionalFields.getFields() != null) {
                return additionalFields.getFields().stream()
                        .filter(field -> key.equalsIgnoreCase(field.getKey()))
                        .map(Field::getValue)
                        .filter(Objects::nonNull)
                        .map(valueType::cast)
                        .findFirst()
                        .orElse(defaultValue);
            }
            return defaultValue;
        }
    }
}
