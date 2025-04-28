package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.*;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.SideEffectsIndexV1;
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
public class SideEffectTransformationService {
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final ProjectService projectService;
    private final UserService userService;
    private final CommonUtils commonUtils;
    private final ObjectMapper objectMapper;
    private final SideEffectService sideEffectService;
    private final IndividualService individualService;
    private final BoundaryService boundaryService;
    private static final List<String> TASK_ADDITIONAL_FIELDS_TO_PICK = new ArrayList<>(Arrays.asList(CYCLE_INDEX));

    public SideEffectTransformationService(TransformerProperties transformerProperties, Producer producer, ProjectService projectService, UserService userService, CommonUtils commonUtils, ObjectMapper objectMapper, SideEffectService sideEffectService, IndividualService individualService, BoundaryService boundaryService) {

        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.projectService = projectService;
        this.userService = userService;
        this.commonUtils = commonUtils;
        this.objectMapper = objectMapper;
        this.sideEffectService = sideEffectService;
        this.individualService = individualService;
        this.boundaryService = boundaryService;
    }

    public void transform(List<SideEffect> sideEffectsList) {
        log.info("transforming for SIDE_EFFECTS id's {}", sideEffectsList.stream()
                .map(SideEffect::getId).collect(Collectors.toList()));
        String topic = transformerProperties.getTransformerProducerSideEffectIndexV1Topic();
        List<SideEffectsIndexV1> sideEffectsIndexV1List = sideEffectsList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        log.info("transformation success for SIDE EFFECTS id's {}", sideEffectsIndexV1List.stream()
                .map(SideEffectsIndexV1::getSideEffect)
                .map(SideEffect::getId)
                .collect(Collectors.toList()));
        producer.push(topic, sideEffectsIndexV1List);
    }

    private SideEffectsIndexV1 transform(SideEffect sideEffect) {

        AdditionalFields additionalFields = sideEffect.getAdditionalFields();
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        if (additionalFields != null && additionalFields.getFields() != null
                && !CollectionUtils.isEmpty(additionalFields.getFields())) {
            additionalDetails = additionalFieldsToDetails(additionalFields.getFields());
        }
        String tenantId = sideEffect.getTenantId();
        String localityCode = null;
        Map<String, Object> individualDetails = new HashMap<>();
        Map<String, String> boundaryHierarchy = new HashMap<>();
        Map<String, String> boundaryHierarchyCode = new HashMap<>();
        List<Task> taskList = sideEffectService.getTaskFromTaskClientReferenceId(sideEffect.getTaskClientReferenceId(), tenantId);

        if (!CollectionUtils.isEmpty(taskList)) {
            Task task = taskList.get(0);
            localityCode = (task.getAddress() != null &&
                    task.getAddress().getLocality() != null &&
                    task.getAddress().getLocality().getCode() != null) ?
                    task.getAddress().getLocality().getCode() :
                    null;
            BoundaryHierarchyResult boundaryHierarchyResult = localityCode != null ? boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, tenantId) :
                    boundaryService.getBoundaryHierarchyWithProjectId(task.getProjectId(), tenantId);
            boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
            boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
            List<ProjectBeneficiary> projectBeneficiaries = projectService
                    .searchBeneficiary(task.getProjectBeneficiaryClientReferenceId(), tenantId);

            if (!CollectionUtils.isEmpty(projectBeneficiaries)) {
                ProjectBeneficiary projectBeneficiary = projectBeneficiaries.get(0);
                individualDetails = individualService.getIndividualInfo(projectBeneficiary.getBeneficiaryClientReferenceId(), tenantId);
            }
            addSpecificAdditionalFields(task, additionalDetails);
        }

        String dpName = localityCode != null ? boundaryService.getLocalizedBoundaryName(localityCode + "_DP",
                null, tenantId) : null;
        Map<String, String> userInfoMap = userService.getUserInfo(sideEffect.getTenantId(), sideEffect.getClientAuditDetails().getCreatedBy());
        if (individualDetails.containsKey(HEIGHT) && individualDetails.containsKey(DISABILITY_TYPE)) {
            additionalDetails.put(HEIGHT, (Integer) individualDetails.get(HEIGHT));
            additionalDetails.put(DISABILITY_TYPE,(String) individualDetails.get(DISABILITY_TYPE));
        }
        SideEffectsIndexV1 sideEffectsIndexV1 = SideEffectsIndexV1.builder()
                .sideEffect(sideEffect)
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .localityCode(localityCode)
                .dateOfBirth(individualDetails.containsKey(DATE_OF_BIRTH) ? (Long) individualDetails.get(DATE_OF_BIRTH) : null)
                .age(individualDetails.containsKey(AGE) ? (Integer) individualDetails.get(AGE) : null)
                .gender(individualDetails.containsKey(GENDER) ? (String) individualDetails.get(GENDER) : null)
                .individualId(individualDetails.containsKey(INDIVIDUAL_ID) ? (String) individualDetails.get(INDIVIDUAL_ID) : null)
                .symptoms(String.join(COMMA, sideEffect.getSymptoms()))
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(dpName)
                .taskDates(commonUtils.getDateFromEpoch(sideEffect.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(sideEffect.getAuditDetails().getLastModifiedTime()))
                .additionalDetails(additionalDetails)
                .build();
        return sideEffectsIndexV1;
    }

    private void addSpecificAdditionalFields(Task task, ObjectNode additionalDetails) {
        if (task.getAdditionalFields() != null && task.getAdditionalFields().getFields() != null &&
                !CollectionUtils.isEmpty(task.getAdditionalFields().getFields())) {
            task.getAdditionalFields().getFields().stream()
                    .filter(f -> TASK_ADDITIONAL_FIELDS_TO_PICK.contains(f.getKey()))
                    .forEach(f -> additionalDetails.put(f.getKey(), f.getValue()));
        }
    }
    private ObjectNode additionalFieldsToDetails(List<Field> fields) {
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        fields.forEach(
                f -> additionalDetails.put(f.getKey(), f.getValue())
        );
        return additionalDetails;
    }

}
