package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import org.egov.common.models.project.AdditionalFields;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.Field;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.ProjectBeneficiaryIndexV1;
import org.egov.transformer.models.downstream.ProjectInfo;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.BoundaryService;
import org.egov.transformer.service.IndividualService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class ProjectBeneficiaryTransformationService {
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final CommonUtils commonUtils;
    private final BoundaryService boundaryService;
    private final IndividualService individualService;
    private static final Map<String, Class<?>> fieldsTypeMap = new HashMap<>();
    static {
        fieldsTypeMap.put(AGE_IN_MONTHS, Integer.class);
        fieldsTypeMap.put(IS_GUEST_MEMBER, Boolean.class);
        fieldsTypeMap.put(IS_HEAD_OF_HOUSEHOLD, Boolean.class);
        fieldsTypeMap.put(AGE, Integer.class);
    }
    private static final Map<String, Class<?>> individualInfoFieldsTypeMap = new HashMap<>();
    static {
        individualInfoFieldsTypeMap.put(GENDER, String.class);
        individualInfoFieldsTypeMap.put(DATE_OF_BIRTH, Long.class);
        individualInfoFieldsTypeMap.put(AGE_IN_MONTHS, Integer.class);
    }


    private static final HashSet<String> mandatoryFields = new HashSet<>();
    static {
        mandatoryFields.add(AGE_IN_MONTHS);
        mandatoryFields.add(GENDER);
    }

    public ProjectBeneficiaryTransformationService(TransformerProperties transformerProperties, Producer producer, ObjectMapper objectMapper, UserService userService, CommonUtils commonUtils, BoundaryService boundaryService, IndividualService individualService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.commonUtils = commonUtils;
        this.boundaryService = boundaryService;
        this.individualService = individualService;
    }

    public void transform(List<ProjectBeneficiary> beneficiaryList) {
        log.info("transforming for BENEFICIARY id's {}", beneficiaryList.stream()
                .map(ProjectBeneficiary::getId).collect(Collectors.toList()));
        String topic = transformerProperties.getTransformerProducerProjectBeneficiaryIndexV1Topic();
        List<ProjectBeneficiaryIndexV1> beneficiaryIndexV1List = beneficiaryList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        log.info("transformation success for BENEFICIARY id's {}", beneficiaryIndexV1List.stream()
                .map(ProjectBeneficiaryIndexV1::getProjectBeneficiary)
                .map(ProjectBeneficiary::getId)
                .collect(Collectors.toList()));
        producer.push(topic, beneficiaryIndexV1List);
    }

    private ProjectBeneficiaryIndexV1 transform(ProjectBeneficiary beneficiary) {
        String projectId = beneficiary.getProjectId();

        BoundaryHierarchyResult boundaryHierarchyResult = new BoundaryHierarchyResult();

        ObjectNode additionalDetails = objectMapper.createObjectNode();

        AdditionalFields additionalFields = beneficiary.getAdditionalFields();

        if (additionalFields != null && beneficiary.getAdditionalFields().getFields() != null
                && !CollectionUtils.isEmpty(beneficiary.getAdditionalFields().getFields())) {
            additionalFieldsToDetails(beneficiary.getAdditionalFields().getFields(), additionalDetails);
        }
        JsonNode boundaryCode = additionalDetails.get(LOCALITY);
        if (isMissing(boundaryCode)) {
            boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(projectId, beneficiary.getTenantId());
        }
        else {
            boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(boundaryCode.asText(), beneficiary.getTenantId());
        }

        Map<String, String> boundaryHierarchy = boundaryHierarchyResult != null ? boundaryHierarchyResult.getBoundaryHierarchy() : null;
        Map<String, String> boundaryHierarchyCode = boundaryHierarchyResult != null ? boundaryHierarchyResult.getBoundaryHierarchyCode() : null;

        Map<String, String> userInfoMap = userService.getUserInfo(beneficiary.getTenantId(), beneficiary.getClientAuditDetails().getLastModifiedBy());
        ProjectInfo projectInfo = new ProjectInfo();

        checkMandatoryFieldExists(additionalDetails, beneficiary.getBeneficiaryClientReferenceId(), beneficiary.getTenantId());
        ProjectBeneficiaryIndexV1 projectBeneficiaryIndexV1 = ProjectBeneficiaryIndexV1.builder()
                .projectBeneficiary(beneficiary)
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .userName(userInfoMap.get(USERNAME))
                .role(userInfoMap.get(ROLE))
                .nameOfUser(userInfoMap.get(NAME))
                .userAddress(userInfoMap.get(CITY))
                .taskDates(commonUtils.getDateFromEpoch(beneficiary.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(beneficiary.getAuditDetails().getLastModifiedTime()))
                .syncedTimeStamp(commonUtils.getTimeStampFromEpoch(beneficiary.getAuditDetails().getLastModifiedTime()))
                .additionalDetails(additionalDetails)
                .build();
        commonUtils.addProjectDetailsFromProjectId(projectBeneficiaryIndexV1, projectId, beneficiary.getTenantId());
        return projectBeneficiaryIndexV1;
    }

    private boolean isMissing(JsonNode value) {
        return value == null ||
                value.isNull() ||
                (value.isTextual() && value.asText().trim().isEmpty());
    }

    private void checkMandatoryFieldExists(ObjectNode additionalDetails, String beneficiaryClientReferenceId, String tenantId) {
        List<String> missingKeys = new ArrayList<>();
        for (String key : mandatoryFields) {
            JsonNode value = additionalDetails.get(key);
            //Check if the value exists or if it is null or empty
            if (isMissing(value)) {
                missingKeys.add(key);
            }
        }
        if (!missingKeys.isEmpty()) {
            addRequiredFieldsInAdditionalDetails(additionalDetails, beneficiaryClientReferenceId, tenantId, missingKeys);
        }
    }

    private void additionalFieldsToDetails(List<Field> fields, ObjectNode additionalDetails) {
        fields.forEach(
                field -> {
                    String key = field.getKey();
                    String value = field.getValue();
                    if (fieldsTypeMap.containsKey(key)) {
                        putValueBasedOnType(additionalDetails, key, value, fieldsTypeMap.get(key));
                    } else {
                        additionalDetails.put(key, value);
                    }
                }
        );
    }

    private void putValueBasedOnType(ObjectNode node, String key, String value, Class<?> type) {
        if (value == null || value.trim().isEmpty()) {
            node.putNull(key);
            return;
        }

        try {
            if (type == Integer.class) {
                node.put(key, Integer.parseInt(value));
            } else if (type == Long.class) {
                node.put(key, Long.parseLong(value));
            } else if (type == Double.class) {
                node.put(key, Double.parseDouble(value));
            } else if (type == Boolean.class) {
                node.put(key, Boolean.parseBoolean(value));
            } else {
                node.put(key, value);
            }
        } catch (Exception e) {
            // fallback to string
            node.put(key, value);
        }
    }

    private void putValueBasedOnTypeObjectBased(ObjectNode additionalDetails, String key, Object value, Class<?> type) {
        if (value == null) {
            additionalDetails.putNull(key);
            return;
        }
        if (type == Integer.class) {
            additionalDetails.put(key, Integer.parseInt(value.toString()));
        } else if (type == Long.class) {
            additionalDetails.put(key, Long.parseLong(value.toString()));
        } else if (type == Double.class) {
            additionalDetails.put(key, Double.parseDouble(value.toString()));
        } else if (type == Boolean.class) {
            additionalDetails.put(key, Boolean.parseBoolean(value.toString()));
        } else if (type == String.class) {
            additionalDetails.put(key, value.toString());
        } else {
            additionalDetails.putPOJO(key, value);
        }
    }

    private void addRequiredFieldsInAdditionalDetails(ObjectNode additionalDetails, String beneficiaryClientReferenceId, String tenantId, List<String> missingKeys) {
        Map<String, Object> individualInfo = individualService.getIndividualInfo(beneficiaryClientReferenceId, tenantId);
        for(String key : missingKeys) {
            log.info("Adding missing value of required key : {}", key);
            if (key.equals(AGE_IN_MONTHS)) {
                putValueBasedOnTypeObjectBased(additionalDetails, key, individualInfo.get(AGE), individualInfoFieldsTypeMap.get(key));
            }
            else {
                putValueBasedOnTypeObjectBased(additionalDetails, key, individualInfo.get(key), individualInfoFieldsTypeMap.get(key));
            }
        }
    }
}
