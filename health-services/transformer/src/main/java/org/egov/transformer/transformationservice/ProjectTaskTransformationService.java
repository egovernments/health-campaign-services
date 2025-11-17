package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.household.Household;
import org.egov.common.models.project.*;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.ProjectTaskIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.*;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class ProjectTaskTransformationService {
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final ObjectMapper objectMapper;
    private final CommonUtils commonUtils;
    private final ProjectService projectService;
    private final ProductService productService;
    private final IndividualService individualService;
    private final HouseholdService householdService;
    private final UserService userService;
    private final BoundaryService boundaryService;
    private static final Set<String> ADDITIONAL_DETAILS_DOUBLE_FIELDS = new HashSet<>(Arrays.asList(QUANTITY_WASTED));
    private static final Set<String> ADDITIONAL_DETAILS_INTEGER_FIELDS = new HashSet<>(Arrays.asList(NO_OF_ROOMS_SPRAYED_KEY, RE_DOSE_QUANTITY_KEY));
    private static final Set<String> BENEFICIARY_INFO_STRING_KEYS = new HashSet<>(Arrays.asList(
            INDIVIDUAL_CLIENT_REFERENCE_ID, GENDER, HOUSEHOLD_CLIENT_REFERENCE_ID, UNIQUE_BENEFICIARY_ID
    ));
    private static final Set<String> BENEFICIARY_INFO_INTEGER_KEYS = new HashSet<>(Arrays.asList(
            AGE, MEMBER_COUNT
    ));

    public ProjectTaskTransformationService(TransformerProperties transformerProperties, Producer producer, ObjectMapper objectMapper, CommonUtils commonUtils, ProjectService projectService, ProductService productService, IndividualService individualService, HouseholdService householdService, UserService userService, BoundaryService boundaryService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.commonUtils = commonUtils;
        this.projectService = projectService;
        this.productService = productService;
        this.individualService = individualService;
        this.householdService = householdService;
        this.userService = userService;
        this.boundaryService = boundaryService;
    }

    public void transform(List<Task> taskList) {
        log.info("transforming for TASK id's {}", taskList.stream()
                .map(Task::getId).collect(Collectors.toList()));
        String topic = transformerProperties.getTransformerProducerBulkProjectTaskIndexV1Topic();
        List<ProjectTaskIndexV1> projectTaskIndexV1List = taskList.stream()
                .map(this::transform)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        log.info("transformation success for TASK id's {}", projectTaskIndexV1List.stream()
                .map(ProjectTaskIndexV1::getId)
                .collect(Collectors.toList()));
        producer.push(topic, projectTaskIndexV1List);
    }

    public List<ProjectTaskIndexV1> transform(Task task) {

        Map<String, String> boundaryHierarchy;
        Map<String, String> boundaryHierarchyCode;
        String tenantId = task.getTenantId();
        String localityCode;
        if (task.getAddress() != null && task.getAddress().getLocality() != null && task.getAddress().getLocality().getCode() != null) {
            localityCode = task.getAddress().getLocality().getCode();
            BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, task.getTenantId());
            boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
            boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
        } else {
            localityCode = null;
            BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(task.getProjectId(), tenantId);
            boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
            boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
        }
        String projectTypeIdAndType = projectService.getProjectTypeInfoByProjectId(task.getProjectId(), tenantId);

        String projectTypeId;
        String projectType;
        if (!StringUtils.isEmpty(projectTypeIdAndType)) {
            String[] parts = projectTypeIdAndType.split(COLON);
            projectTypeId = parts[0];
            projectType = (parts.length > 1) ? parts[1] : "";
        } else {
            projectTypeId = "";
            projectType = "";
        }

        AdditionalFields taskAdditionalFields = task.getAdditionalFields();

        boolean hasIndividualOrHousehold = false;

        if (taskAdditionalFields != null && taskAdditionalFields.getFields() != null
                && !taskAdditionalFields.getFields().isEmpty()) {
            hasIndividualOrHousehold = taskAdditionalFields.getFields().stream()
                    .anyMatch(field -> INDIVIDUAL_CLIENT_REFERENCE_ID.equals(field.getKey()) || HOUSEHOLD_CLIENT_REFERENCE_ID.equals(field.getKey()));
        }
        Map<String, Object> beneficiaryInfo = new HashMap<>();

        String projectBeneficiaryType = projectService.getProjectBeneficiaryType(task.getTenantId(), projectTypeId);
        if (hasIndividualOrHousehold) {
            log.info("Fetching BeneficiaryInfo from task addFields");
            List<Field> fields = taskAdditionalFields.getFields();
            if (fields != null) {
                fields.forEach(field -> {
                    String key = field.getKey();
                    String value = field.getValue();
                    if (BENEFICIARY_INFO_STRING_KEYS.contains(key)) {
                        beneficiaryInfo.put(key, value);
                    } else if (BENEFICIARY_INFO_INTEGER_KEYS.contains(key)) {
                        try {
                            beneficiaryInfo.put(key, Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            log.warn("Invalid integer for key '{}': '{}', defaulting to null", key, value);
                            beneficiaryInfo.put(key, null);
                        }
                    }
                });
            }
        } else {
            String projectBeneficiaryClientReferenceId = task.getProjectBeneficiaryClientReferenceId();
            beneficiaryInfo.putAll(getProjectBeneficiaryDetails(projectBeneficiaryClientReferenceId, projectBeneficiaryType, tenantId));
        }

        Task constructedTask = constructTaskResourceIfNull(task);
        Map<String, String> userInfoMap = userService.getUserInfo(task.getTenantId(), task.getClientAuditDetails().getCreatedBy());
        return constructedTask.getResources().stream().map(r ->
                transformTaskToProjectTaskIndex(r, task, boundaryHierarchy, boundaryHierarchyCode, tenantId, beneficiaryInfo, projectBeneficiaryType, projectTypeId, projectType, userInfoMap, localityCode)
        ).collect(Collectors.toList());
    }

    private ProjectTaskIndexV1 transformTaskToProjectTaskIndex(TaskResource taskResource, Task task, Map<String, String> boundaryHierarchy, Map<String, String> boundaryHierarchyCode, String tenantId,
                                                               Map<String, Object> beneficiaryInfo, String projectBeneficiaryType, String projectTypeId, String projectType,
                                                               Map<String, String> userInfoMap, String localityCode) {
        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(task.getAuditDetails().getCreatedTime());
        List<String> variantList = Optional.ofNullable(taskResource.getProductVariantId())
                .map(Collections::singletonList)
                .orElse(new ArrayList<>());
        String productName = null;
        if (!variantList.isEmpty()) {
            productName = String.join(COMMA, productService.getProductVariantNames(variantList, tenantId));
        }
        ProjectTaskIndexV1 projectTaskIndexV1 = ProjectTaskIndexV1.builder()
                .id(taskResource.getId())
                .taskId(task.getId())
                .taskClientReferenceId(task.getClientReferenceId())
                .clientReferenceId(taskResource.getClientReferenceId())
                .tenantId(tenantId)
                .taskType("DELIVERY")
                .status(task.getStatus())
                .localityCode(localityCode)
                .projectId(task.getProjectId())
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .productVariant(taskResource.getProductVariantId())
                .productName(productName)
                .isDelivered(taskResource.getIsDelivered())
                .quantity(taskResource.getQuantity())
                .deliveredTo(projectBeneficiaryType)
                .deliveryComments(taskResource.getDeliveryComment())
                .latitude(task.getAddress().getLatitude())
                .longitude(task.getAddress().getLongitude())
                .locationAccuracy(task.getAddress().getLocationAccuracy())
                .createdTime(task.getClientAuditDetails().getCreatedTime())
                .taskDates(commonUtils.getDateFromEpoch(task.getClientAuditDetails().getLastModifiedTime()))
                .createdBy(task.getClientAuditDetails().getCreatedBy())
                .lastModifiedTime(task.getClientAuditDetails().getLastModifiedTime())
                .lastModifiedBy(task.getClientAuditDetails().getLastModifiedBy())
                .projectBeneficiaryClientReferenceId(task.getProjectBeneficiaryClientReferenceId())
                .syncedTimeStamp(syncedTimeStamp)
                .syncedDate(commonUtils.getDateFromEpoch(task.getAuditDetails().getLastModifiedTime()))
                .syncedTime(task.getAuditDetails().getLastModifiedTime())
                .geoPoint(commonUtils.getGeoPoint(task.getAddress()))
                .administrationStatus(task.getStatus())
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .projectType(projectType)
                .projectTypeId(projectTypeId)
                .householdId(beneficiaryInfo.containsKey(HOUSEHOLD_CLIENT_REFERENCE_ID) ? (String) beneficiaryInfo.get(HOUSEHOLD_CLIENT_REFERENCE_ID) : null)
                .memberCount(beneficiaryInfo.containsKey(MEMBER_COUNT) ? (Integer) beneficiaryInfo.get(MEMBER_COUNT) : null)
                .dateOfBirth(beneficiaryInfo.containsKey(DATE_OF_BIRTH) ? (Long) beneficiaryInfo.get(DATE_OF_BIRTH) : null)
                .age(beneficiaryInfo.containsKey(AGE) ? (Integer) beneficiaryInfo.get(AGE) : null)
                .gender(beneficiaryInfo.containsKey(GENDER) ? (String) beneficiaryInfo.get(GENDER) : null)
                .individualId(beneficiaryInfo.containsKey(INDIVIDUAL_CLIENT_REFERENCE_ID) ? (String) beneficiaryInfo.get(INDIVIDUAL_CLIENT_REFERENCE_ID) : null)
                .build();

        //adding to additional details  from additionalFields in task and task resource
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        if (task.getAdditionalFields() != null) {
            addAdditionalDetails(task.getAdditionalFields(), additionalDetails);
            addCycleIndex(additionalDetails, task.getClientAuditDetails(), tenantId, projectTypeId);
        }
        // TODO below code is commented because the additionalFields is removed from taskResource but his has to be added back
//        if (taskResource.getAdditionalFields() != null) {
//            addAdditionalDetails(taskResource.getAdditionalFields(), additionalDetails);
//            addCycleIndex(additionalDetails, taskResource.getAuditDetails(), tenantId, projectTypeId);
//        }
        if (beneficiaryInfo.containsKey(HEIGHT) && beneficiaryInfo.containsKey(DISABILITY_TYPE)) {
            additionalDetails.put(HEIGHT, (Integer) beneficiaryInfo.get(HEIGHT));
            additionalDetails.put(DISABILITY_TYPE, (String) beneficiaryInfo.get(DISABILITY_TYPE));
        }

        if (beneficiaryInfo.containsKey("additionalFields")) {
            try {
                householdService.additionalFieldsToDetails(additionalDetails, beneficiaryInfo.get("additionalFields"));
            } catch (IllegalArgumentException e) {
                log.error("Error in projectTask transformation while addition of additionalFields to additionDetails {}", e.getMessage());
            }
        }

        int pregnantWomenCount = additionalDetails.has(PREGNANTWOMEN) ? additionalDetails.get(PREGNANTWOMEN).asInt(0) : 0;
        int childrenCount = additionalDetails.has(CHILDREN) ? additionalDetails.get(CHILDREN).asInt(0) : 0;
        if (pregnantWomenCount > 0 || childrenCount > 0) {
            additionalDetails.put(ISVULNERABLE, true);
        }
        if (task.getStatus() != null && task.getStatus().equalsIgnoreCase(CLOSED_HOUSEHOLD) && !additionalDetails.has(REASON_OF_REFUSAL)) {
            additionalDetails.put(REASON_OF_REFUSAL, task.getStatus());
        }

        if (additionalDetails != null && additionalDetails.has("selectedVaccines")) {
            String selectedVaccines = additionalDetails.get("selectedVaccines").asText();

             // Split by "." and remove prefix "HCM_VACCINE_"
            List<String> vaccinesList = Arrays.stream(selectedVaccines.split("\\."))
                    .map(v -> v.replace("HCM_VACCINE_", ""))  // remove prefix
                    .collect(Collectors.toList());             // collect as list

            // Convert List<String> → ArrayNode
            ArrayNode vaccinesArray = objectMapper.valueToTree(vaccinesList);


            // Add new field to ObjectNode
            additionalDetails.put("vaccinesList", vaccinesArray);
        }
        projectTaskIndexV1.setAdditionalDetails(additionalDetails);

        return projectTaskIndexV1;
    }

    private void addAdditionalDetails(AdditionalFields additionalFields, ObjectNode additionalDetails) {
        additionalFields.getFields().forEach(field -> {
            String key = field.getKey();
            String value = field.getValue();
            if (ADDITIONAL_DETAILS_DOUBLE_FIELDS.contains(key)) {
                try {
                    additionalDetails.put(key, Double.valueOf(value));
                } catch (NumberFormatException e) {
                    log.warn("Invalid double format for key '{}': value '{}'. Storing as null.", key, value);
                    additionalDetails.set(key, null);
                }

            } else if (ADDITIONAL_DETAILS_INTEGER_FIELDS.contains(key)) {
                try {
                    additionalDetails.put(key, Integer.valueOf(value));
                } catch (NumberFormatException e) {
                    log.warn("Invalid integer format for key '{}': value '{}'. Storing as null.", key, value);
                    additionalDetails.set(key, null);
                }
            } else {
                additionalDetails.put(key, value);
            }
        });
    }

    //This cycleIndex logic has to be changed if we send all required additionalDetails from app
    private void addCycleIndex(ObjectNode additionalDetails, AuditDetails auditDetails, String tenantId, String projectTypeId) {
        if (!additionalDetails.has(CYCLE_INDEX)) {
            String cycleIndex = commonUtils.fetchCycleIndexFromTime(tenantId, projectTypeId, auditDetails.getCreatedTime());
            additionalDetails.put(CYCLE_INDEX, cycleIndex);
        }
    }

    private Task constructTaskResourceIfNull(Task task) {
        if (task.getResources() == null || task.getResources().isEmpty()) {
            TaskResource taskResource = new TaskResource();
            taskResource.setId((task.getStatus() == null ? "NULL" : task.getStatus()) + HYPHEN + task.getId());
            taskResource.setClientReferenceId((task.getStatus() == null ? "NULL" : task.getStatus()) + HYPHEN + task.getClientReferenceId());
            taskResource.setIsDelivered(false);
            taskResource.setDeliveryComment(null);

            AdditionalFields taskAdditionalFields = task.getAdditionalFields();

            if (taskAdditionalFields != null && taskAdditionalFields.getFields() != null
                    && !taskAdditionalFields.getFields().isEmpty()) {
                List<Field> fields = taskAdditionalFields.getFields();

                String productVariantId = getFieldStringValue(fields, PRODUCT_VARIANT_ID);
                String taskStatus = getFieldStringValue(fields, TASK_STATUS);

                taskResource.setProductVariantId(productVariantId);

                if (BENEFICIARY_REFERRED.equalsIgnoreCase(taskStatus) && productVariantId != null) {
                    taskResource.setQuantity(RE_ADMINISTERED_DOSES);
                    taskResource.setDeliveryComment(ADMINISTRATION_NOT_SUCCESSFUL);
                }
            } else {
                taskResource.setQuantity(null);
                taskResource.setProductVariantId(null);
            }

            task.setResources(Collections.singletonList(taskResource));
        }
        return task;
    }

    private String getFieldStringValue(List<Field> fields, String key) {
        Optional<Field> field = fields.stream()
                .filter(field1 -> key.equalsIgnoreCase(field1.getKey()))
                .findFirst();
        return field.map(Field::getValue).orElse(null);
    }

    private Map<String, Object> getProjectBeneficiaryDetails(String projectBeneficiaryClRefId, String projectBeneficiaryType, String tenantId) {
        Map<String, Object> projectBenfInfoMap = new HashMap<>();

        List<ProjectBeneficiary> projectBeneficiaries = projectService
                .searchBeneficiary(projectBeneficiaryClRefId, tenantId);
        if (CollectionUtils.isEmpty(projectBeneficiaries)) {
            return projectBenfInfoMap;
        }
        ProjectBeneficiary projectBeneficiary = projectBeneficiaries.get(0);
        String beneficiaryClientRefId = projectBeneficiary.getBeneficiaryClientReferenceId();

        if (HOUSEHOLD.equalsIgnoreCase(projectBeneficiaryType)) {
            log.info("fetching household details for HOUSEHOLD projectBeneficiaryType, clientReferenceId: {}", beneficiaryClientRefId);
            List<Household> households = householdService.searchHousehold(beneficiaryClientRefId, tenantId);

//Commenting below code as it is not needed

//            int deliveryCount = (int) Math.round((Double) (memberCount / transformerProperties.getProgramMandateDividingFactor()));
//            final boolean isMandateComment = deliveryCount > transformerProperties.getProgramMandateLimit();
//            projectTaskIndexV1.setDeliveryComments(taskResource.getDeliveryComment() != null ? taskResource.getDeliveryComment() : isMandateComment ? transformerProperties.getProgramMandateComment() : null);

            if (!CollectionUtils.isEmpty(households)) {
                Integer memberCount = households.get(0).getMemberCount();
                projectBenfInfoMap.put(MEMBER_COUNT, memberCount);
                projectBenfInfoMap.put(HOUSEHOLD_CLIENT_REFERENCE_ID, households.get(0).getClientReferenceId());
                if (ObjectUtils.isNotEmpty(households.get(0).getAdditionalFields()) && !CollectionUtils.isEmpty(households.get(0).getAdditionalFields().getFields())) {
                    projectBenfInfoMap.put("additionalFields", households.get(0).getAdditionalFields().getFields());
                }
            } else {
                log.info("COULD NOT FIND HOUSEHOLD for clientReferenceId: {}", beneficiaryClientRefId);
            }
        } else if (INDIVIDUAL.equalsIgnoreCase(projectBeneficiaryType)) {
            log.info("fetching individual details for INDIVIDUAL projectBeneficiaryType");
            projectBenfInfoMap = individualService.getIndividualInfo(beneficiaryClientRefId, tenantId);
        }
        return projectBenfInfoMap;
    }
}
