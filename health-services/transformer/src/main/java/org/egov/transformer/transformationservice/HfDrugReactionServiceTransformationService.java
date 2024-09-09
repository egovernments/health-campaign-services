package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.Project;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.HfDrugReactionServiceTaskIndexV1;
import org.egov.transformer.models.upstream.AttributeValue;
import org.egov.transformer.models.upstream.Service;
import org.egov.transformer.models.upstream.ServiceDefinition;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.service.ServiceDefinitionService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.*;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class HfDrugReactionServiceTransformationService {
    private final ServiceDefinitionService serviceDefinitionService;
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final ProjectService projectService;
    private final UserService userService;
    private final CommonUtils commonUtils;
    private final ObjectMapper objectMapper;


    public HfDrugReactionServiceTransformationService(ServiceDefinitionService serviceDefinitionService, TransformerProperties transformerProperties, Producer producer, ProjectService projectService, UserService userService, CommonUtils commonUtils, ObjectMapper objectMapper) {

        this.serviceDefinitionService = serviceDefinitionService;
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.projectService = projectService;
        this.userService = userService;
        this.commonUtils = commonUtils;
        this.objectMapper = objectMapper;
    }

    public void transform(List<Service> serviceList) {
        List<HfDrugReactionServiceTaskIndexV1> hfDrugReactionServiceTaskIndexV1List = new ArrayList<>();
        String topic = transformerProperties.getTransformerProducerHfReferralDrugReactionServiceIndexTopic();
        serviceList.forEach(service -> transform(service, hfDrugReactionServiceTaskIndexV1List));
        if (!hfDrugReactionServiceTaskIndexV1List.isEmpty()) {
            producer.push(topic, hfDrugReactionServiceTaskIndexV1List);
        }
    }

    private void transform(Service service, List<HfDrugReactionServiceTaskIndexV1> hfDrugReactionServiceTaskIndexV1List) {
        ServiceDefinition serviceDefinition = serviceDefinitionService.getServiceDefinition(service.getServiceDefId(), service.getTenantId());
        String checkListName = serviceDefinition.getCode();
        String tenantId = service.getTenantId();
        String[] parts = serviceDefinition.getCode().split("\\.");
        String projectName = parts[0];
        String supervisorLevel = parts[2];
        String projectId = service.getAccountId() != null ? service.getAccountId() :
                projectService.getProjectByName(projectName, service.getTenantId()).getId();
        Map<String, String> boundaryLabelToNameMap;
        Project project = projectService.getProject(projectId, tenantId);
        String projectTypeId = project.getProjectTypeId();

        if (service.getAdditionalDetails() != null) {
            boundaryLabelToNameMap = projectService
                    .getBoundaryLabelToNameMap((String) service.getAdditionalDetails(), service.getTenantId());
        } else {
            boundaryLabelToNameMap = projectService.getBoundaryLabelToNameMapByProjectId(projectId, service.getTenantId());
        }
        log.info("boundary labels {}", boundaryLabelToNameMap.toString());
        ObjectNode boundaryHierarchy = (ObjectNode) commonUtils.getBoundaryHierarchy(tenantId, projectTypeId, boundaryLabelToNameMap);
        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(service.getAuditDetails().getCreatedTime());

        String cycleIndex = commonUtils.fetchCycleIndex(tenantId, projectTypeId, service.getAuditDetails());
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        additionalDetails.put(CYCLE_INDEX, cycleIndex);

        String checkListToFilter = transformerProperties.getHfReferralDrugReactionCheckListName().trim();
        List<AttributeValue> attributeValueList = service.getAttributes();
        Map<String, String> attributeCodeToQuestion = new HashMap<>();
        Map<String, String> userInfoMap = userService.getUserInfo(service.getTenantId(), service.getAuditDetails().getCreatedBy());
        getAttributeCodeMappings(attributeCodeToQuestion);
        if (checkListName.trim().equals(checkListToFilter)) {
            String checklistName = serviceDefinition.getCode().split("\\.")[1];
            HfDrugReactionServiceTaskIndexV1 hfDrugReactionServiceTaskIndexV1 = HfDrugReactionServiceTaskIndexV1.builder()
                    .id(service.getId())
                    .supervisorLevel(supervisorLevel)
                    .checklistName(checklistName)
                    .tenantId(tenantId)
                    .projectId(projectId)
                    .userName(userInfoMap.get(USERNAME))
                    .role(userInfoMap.get(ROLE))
                    .userAddress(userInfoMap.get(CITY))
                    .createdTime(service.getAuditDetails().getCreatedTime())
                    .syncedTime(service.getAuditDetails().getCreatedTime())
                    .taskDates(commonUtils.getDateFromEpoch(service.getAuditDetails().getLastModifiedTime()))
                    .createdBy(service.getAuditDetails().getCreatedBy())
                    .syncedTimeStamp(syncedTimeStamp)
                    .boundaryHierarchy(boundaryHierarchy)
                    .additionalDetails(additionalDetails)
                    .build();

            searchAndSetAttribute(attributeValueList, attributeCodeToQuestion, hfDrugReactionServiceTaskIndexV1);
            hfDrugReactionServiceTaskIndexV1List.add(hfDrugReactionServiceTaskIndexV1);
        }
    }

    private void searchAndSetAttribute(List<AttributeValue> attributeValueList, Map<String, String> codeToQuestionMapping, HfDrugReactionServiceTaskIndexV1 hfDrugReactionServiceTaskIndexV1) {
        attributeValueList.forEach(attributeValue -> {
            String attributeCode = attributeValue.getAttributeCode();
            if (codeToQuestionMapping.containsKey(attributeCode)) {
                String question = codeToQuestionMapping.get(attributeCode);
                setAttributeValue(hfDrugReactionServiceTaskIndexV1, attributeValue.getValue(), question);
            }
        });
    }

    private static void setAttributeValue(HfDrugReactionServiceTaskIndexV1 hfDrugReactionServiceTaskIndexV1, Object value, String question) {
        switch (question) {
            case HF_DRUG_EVALUATED_FOR_SE: {
                hfDrugReactionServiceTaskIndexV1.setEvaluatedForSE(value);
                break;
            }
            case HF_DRUG_FILLED_PHARMA: {
                hfDrugReactionServiceTaskIndexV1.setFilledPharma(value);
                break;
            }
            case HF_DRUG_ADVERSE_REACTIONS: {
                hfDrugReactionServiceTaskIndexV1.setAdverseReactions(value);
                break;
            }
            case HF_DRUG_SERIOUS_ADVERSE_EFFECTS: {
                hfDrugReactionServiceTaskIndexV1.setSeriousAdverseEffects(value);
                break;
            }
            case HF_DRUG_SERIOUS_ADVERSE_EFFECTS_OUTCOME: {
                hfDrugReactionServiceTaskIndexV1.setSeriousAdverseEffectsOutcome(value);
                break;
            }
        }

    }

    private static void getAttributeCodeMappings(Map<String, String> attributeCodeToQuestionGroup) {
        attributeCodeToQuestionGroup.put("HF_REASON_DRUG_SE_Q1", HF_DRUG_EVALUATED_FOR_SE);
        attributeCodeToQuestionGroup.put("HF_REASON_DRUG_SE_Q2", HF_DRUG_FILLED_PHARMA);
        attributeCodeToQuestionGroup.put("HF_REASON_DRUG_SE_Q3", HF_DRUG_ADVERSE_REACTIONS);
        attributeCodeToQuestionGroup.put("HF_REASON_DRUG_SE_Q4", HF_DRUG_SERIOUS_ADVERSE_EFFECTS);
        attributeCodeToQuestionGroup.put("HF_REASON_DRUG_SE_Q4.YES.DQ1", HF_DRUG_SERIOUS_ADVERSE_EFFECTS_OUTCOME);
    }
}
