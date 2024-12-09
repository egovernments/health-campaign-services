package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.Household;
import org.egov.common.models.project.*;
import org.egov.transformer.config.TransformerProperties;
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
    private static final Set<String> ADDITIONAL_DETAILS_DOUBLE_FIELDS = new HashSet<>(Arrays.asList(QUANTITY_WASTED, QUANTITY_UTILISED));


    public ProjectTaskTransformationService(TransformerProperties transformerProperties, Producer producer, ObjectMapper objectMapper, CommonUtils commonUtils, ProjectService projectService, ProductService productService, IndividualService individualService, HouseholdService householdService, UserService userService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.commonUtils = commonUtils;
        this.projectService = projectService;
        this.productService = productService;
        this.individualService = individualService;
        this.householdService = householdService;
        this.userService = userService;
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
        String tenantId = task.getTenantId();
        String localityCode;
        if (task.getAddress() != null && task.getAddress().getLocality() != null && task.getAddress().getLocality().getCode() != null) {
            localityCode = task.getAddress().getLocality().getCode();
            boundaryHierarchy = projectService.getBoundaryHierarchyWithLocalityCode(localityCode, task.getTenantId());
        } else {
            localityCode = null;
            boundaryHierarchy = projectService.getBoundaryHierarchyWithProjectId(task.getProjectId(), tenantId);
        }
        Project project = projectService.getProject(task.getProjectId(), tenantId);
        String projectTypeId = project.getProjectTypeId();
        String projectType = project.getProjectType();

        String projectBeneficiaryClientReferenceId = task.getProjectBeneficiaryClientReferenceId();
        String projectBeneficiaryType = projectService.getProjectBeneficiaryType(task.getTenantId(), projectTypeId);
        AdditionalFields additionalFields = task.getAdditionalFields();
        List<Field> fields = new ArrayList<>();
        if (additionalFields != null && additionalFields.getFields() != null
                && !CollectionUtils.isEmpty(additionalFields.getFields())) {
            fields = additionalFields.getFields();
        }
        Integer age = null;
        String gender = null;
        Long dob = null;
        Integer height = null;
        String disabilityType = null;
        String individualId = null;
        for (Field f : fields){
            if (AGE.equals(f.getKey())) {
                age = Integer.valueOf(f.getValue());
            }
            else if (GENDER.equals(f.getKey())) {
                gender = f.getValue().toUpperCase();
            }
            else if (DATE_OF_BIRTH.equals(f.getKey())) {
                dob = Long.valueOf(f.getValue());
            }
            else if (HEIGHT.equals(f.getKey())) {
                height = Integer.valueOf(f.getValue());
            }
            else if (DISABILITY_TYPE.equals(f.getKey())) {
                disabilityType = f.getValue();
            }
            else if (INDIVIDUAL_ID.equals(f.getKey())) {
                individualId = f.getValue();
            }
        }
        Map<String, Object> beneficiaryInfo;
        if(age == null && gender == null) {
            beneficiaryInfo = getProjectBeneficiaryDetails(projectBeneficiaryClientReferenceId, projectBeneficiaryType, tenantId);
        }
        else{
            beneficiaryInfo = new HashMap<>();
            beneficiaryInfo.put(AGE, age);
            beneficiaryInfo.put(GENDER, gender);
            beneficiaryInfo.put(DATE_OF_BIRTH, dob);
            beneficiaryInfo.put(DISABILITY_TYPE, disabilityType);
            beneficiaryInfo.put(INDIVIDUAL_ID, individualId);
            beneficiaryInfo.put(HEIGHT, height);
        }
        Task constructedTask = constructTaskResourceIfNull(task);
        Map<String, String> userInfoMap = userService.getUserInfo(task.getTenantId(), task.getClientAuditDetails().getCreatedBy());
        return constructedTask.getResources().stream().map(r ->
                transformTaskToProjectTaskIndex(r, task, boundaryHierarchy, tenantId, beneficiaryInfo, projectBeneficiaryType, projectTypeId, projectType, userInfoMap, localityCode)
        ).collect(Collectors.toList());
    }

    private ProjectTaskIndexV1 transformTaskToProjectTaskIndex(TaskResource taskResource, Task task, Map<String, String> boundaryHierarchy, String tenantId,
                                                               Map<String, Object> beneficiaryInfo, String projectBeneficiaryType, String projectTypeId, String projectType,
                                                               Map<String, String> userInfoMap, String localityCode) {
        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(task.getAuditDetails().getCreatedTime());
        List<String> variantList = new ArrayList<>(Collections.singleton(taskResource.getProductVariantId()));
        String productName = String.join(COMMA, productService.getProductVariantNames(variantList, tenantId));

        ProjectTaskIndexV1 projectTaskIndexV1 = ProjectTaskIndexV1.builder()
                .id(taskResource.getId())
                .taskId(task.getId())
                .taskClientReferenceId(task.getClientReferenceId())
                .clientReferenceId(taskResource.getClientReferenceId())
                .tenantId(tenantId)
                .taskType("DELIVERY")
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
                .projectType(projectType)
                .projectTypeId(projectTypeId)
                .householdId(beneficiaryInfo.containsKey(HOUSEHOLD_ID) ? (String) beneficiaryInfo.get(HOUSEHOLD_ID) : null)
                .memberCount(beneficiaryInfo.containsKey(MEMBER_COUNT) ? (Integer) beneficiaryInfo.get(MEMBER_COUNT) : null)
                .dateOfBirth(beneficiaryInfo.containsKey(DATE_OF_BIRTH) ? (Long) beneficiaryInfo.get(DATE_OF_BIRTH) : null)
                .age(beneficiaryInfo.containsKey(AGE) ? (Integer) beneficiaryInfo.get(AGE) : null)
                .gender(beneficiaryInfo.containsKey(GENDER) ? (String) beneficiaryInfo.get(GENDER) : null)
                .individualId(beneficiaryInfo.containsKey(INDIVIDUAL_ID) ? (String) beneficiaryInfo.get(INDIVIDUAL_ID) : null)
                .tag(beneficiaryInfo.containsKey(BENEFICIARY_TAG) ? (String) beneficiaryInfo.get(BENEFICIARY_TAG) : null)
                .build();

        //adding to additional details  from additionalFields in task and task resource
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        if (task.getAdditionalFields() != null) {
            addAdditionalDetails(task.getAdditionalFields(), additionalDetails);
            addCycleIndex(additionalDetails, task.getAuditDetails(), tenantId, projectTypeId);
        }
        if (taskResource.getAdditionalFields() != null) {
            addAdditionalDetails(taskResource.getAdditionalFields(), additionalDetails);
            addCycleIndex(additionalDetails, taskResource.getAuditDetails(), tenantId, projectTypeId);
        }
        if (beneficiaryInfo.containsKey(HEIGHT) && beneficiaryInfo.containsKey(DISABILITY_TYPE)) {
            additionalDetails.put(HEIGHT, (Integer) beneficiaryInfo.get(HEIGHT));
            additionalDetails.put(DISABILITY_TYPE,(String) beneficiaryInfo.get(DISABILITY_TYPE));
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
                    log.warn("Invalid number format for key '{}': value '{}'. Storing as null.", key, value);
                    additionalDetails.put(key, (JsonNode) null);
                }

            } else {
                additionalDetails.put(key, value);
            }
        });
    }

    //This cycleIndex logic has to be changed if we send all required additionalDetails from app
    private void addCycleIndex(ObjectNode additionalDetails, AuditDetails auditDetails, String tenantId, String projectTypeId) {
        if (!additionalDetails.has(CYCLE_INDEX)) {
            String cycleIndex = commonUtils.fetchCycleIndex(tenantId, projectTypeId, auditDetails);
            additionalDetails.put(CYCLE_INDEX, cycleIndex);
        }
    }

    private Task constructTaskResourceIfNull(Task task) {
        if (task.getResources() == null || task.getResources().isEmpty()) {
            TaskResource taskResource = new TaskResource();
            taskResource.setId(task.getStatus() + HYPHEN + task.getId());
            taskResource.setClientReferenceId(task.getStatus() + HYPHEN + task.getClientReferenceId());
            taskResource.setIsDelivered(false);
            taskResource.setDeliveryComment(null);

            AdditionalFields taskAdditionalFields = task.getAdditionalFields();

            if (taskAdditionalFields != null && !taskAdditionalFields.getFields().isEmpty()) {
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
            log.info("fetching household details for HOUSEHOLD projectBeneficiaryType");
            List<Household> households = householdService.searchHousehold(beneficiaryClientRefId, tenantId);
            Integer memberCount = households.get(0).getMemberCount();
//Commenting below code as it is not needed

//            int deliveryCount = (int) Math.round((Double) (memberCount / transformerProperties.getProgramMandateDividingFactor()));
//            final boolean isMandateComment = deliveryCount > transformerProperties.getProgramMandateLimit();
//            projectTaskIndexV1.setDeliveryComments(taskResource.getDeliveryComment() != null ? taskResource.getDeliveryComment() : isMandateComment ? transformerProperties.getProgramMandateComment() : null);

            if (!CollectionUtils.isEmpty(households)) {
                projectBenfInfoMap.put(MEMBER_COUNT, memberCount);
                projectBenfInfoMap.put(HOUSEHOLD_ID, households.get(0).getClientReferenceId());
            }
        } else if (INDIVIDUAL.equalsIgnoreCase(projectBeneficiaryType)) {
            log.info("fetching individual details for INDIVIDUAL projectBeneficiaryType");
            projectBenfInfoMap = individualService.getIndividualInfo(beneficiaryClientRefId, tenantId);
        }
        projectBenfInfoMap.put(BENEFICIARY_TAG, projectBeneficiary.getTag());
        return projectBenfInfoMap;
    }
}
