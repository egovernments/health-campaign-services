package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.Task;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.SideEffectsIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.*;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public SideEffectTransformationService(TransformerProperties transformerProperties, Producer producer, ProjectService projectService, UserService userService, CommonUtils commonUtils, ObjectMapper objectMapper, SideEffectService sideEffectService, IndividualService individualService) {

        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.projectService = projectService;
        this.userService = userService;
        this.commonUtils = commonUtils;
        this.objectMapper = objectMapper;
        this.sideEffectService = sideEffectService;
        this.individualService = individualService;
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
        String tenantId = sideEffect.getTenantId();
        Task task = null;
        Integer cycleIndex = null;
        String localityCode = null;
        ProjectBeneficiary projectBeneficiary = null;
        Map<String, Object> individualDetails = new HashMap<>();
        Map<String, String> boundaryHierarchy = new HashMap<>();
        List<Task> taskList = sideEffectService.getTaskFromTaskClientReferenceId(sideEffect.getTaskClientReferenceId(), tenantId);
        if (!CollectionUtils.isEmpty(taskList)) {
            task = taskList.get(0);
            localityCode = task.getAddress().getLocality().getCode();
        }
        if (task != null) {
            boundaryHierarchy = commonUtils.getBoundaryHierarchyWithProjectId(task.getProjectId(), tenantId);
            List<ProjectBeneficiary> projectBeneficiaries = projectService
                    .searchBeneficiary(task.getProjectBeneficiaryClientReferenceId(), tenantId);

            if (!CollectionUtils.isEmpty(projectBeneficiaries)) {
                projectBeneficiary = projectBeneficiaries.get(0);
            }
        }
        if (projectBeneficiary != null) {
            individualDetails = individualService.findIndividualByClientReferenceId(projectBeneficiary.getBeneficiaryClientReferenceId(), tenantId);
            if (projectBeneficiary.getProjectId() != null) {
                Project project = projectService.getProject(projectBeneficiary.getProjectId(), tenantId);
//                TODO change logic of fetching cycleIndex and additionalDetails, cycle Index can be fetching from task additional fields
                cycleIndex = commonUtils.fetchCycleIndex(tenantId, project.getProjectTypeId(), sideEffect.getAuditDetails());
            }
        }

        ObjectNode additionalDetails = objectMapper.createObjectNode();
        additionalDetails.put(CYCLE_INDEX, cycleIndex);

        Map<String, String> userInfoMap = userService.getUserInfo(sideEffect.getTenantId(), sideEffect.getClientAuditDetails().getCreatedBy());

        SideEffectsIndexV1 sideEffectsIndexV1 = SideEffectsIndexV1.builder()
                .sideEffect(sideEffect)
                .dateOfBirth(individualDetails.containsKey(DATE_OF_BIRTH) ? (Long) individualDetails.get(DATE_OF_BIRTH) : null)
                .age(individualDetails.containsKey(AGE) ? (Integer) individualDetails.get(AGE) : null)
                .boundaryHierarchy(boundaryHierarchy)
                .localityCode(localityCode)
                .gender(individualDetails.containsKey(GENDER) ? (String) individualDetails.get(GENDER) : null)
                .individualId(individualDetails.containsKey(INDIVIDUAL_ID) ? (String) individualDetails.get(INDIVIDUAL_ID) : null)
                .symptoms(String.join(COMMA, sideEffect.getSymptoms()))
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .taskDates(commonUtils.getDateFromEpoch(sideEffect.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(sideEffect.getAuditDetails().getLastModifiedTime()))
                .additionalDetails(additionalDetails)
                .build();
        return sideEffectsIndexV1;
    }

}
