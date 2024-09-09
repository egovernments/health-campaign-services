package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.Project;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.HfFeverServiceIndexV1;
import org.egov.transformer.models.upstream.AttributeValue;
import org.egov.transformer.models.upstream.Service;
import org.egov.transformer.models.upstream.ServiceDefinition;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.service.ServiceDefinitionService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class HfFeverServiceTransformationService {
    private final ServiceDefinitionService serviceDefinitionService;
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final ProjectService projectService;
    private final UserService userService;
    private final CommonUtils commonUtils;
    private final ObjectMapper objectMapper;


    public HfFeverServiceTransformationService(ServiceDefinitionService serviceDefinitionService, TransformerProperties transformerProperties, Producer producer, ProjectService projectService, UserService userService, CommonUtils commonUtils, ObjectMapper objectMapper) {

        this.serviceDefinitionService = serviceDefinitionService;
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.projectService = projectService;
        this.userService = userService;
        this.commonUtils = commonUtils;
        this.objectMapper = objectMapper;
    }

    public void transform(List<Service> serviceList) {
        List<HfFeverServiceIndexV1> hfFeverServiceIndexV1List = new ArrayList<>();
        String topic = transformerProperties.getTransformerProducerHfReferralFeverServiceIndexTopic();
        serviceList.forEach(service -> transform(service, hfFeverServiceIndexV1List));
        if (!hfFeverServiceIndexV1List.isEmpty()) {
            producer.push(topic, hfFeverServiceIndexV1List);
        }
    }

    private void transform(Service service, List<HfFeverServiceIndexV1> hfFeverServiceIndexV1List) {
        ServiceDefinition serviceDefinition = serviceDefinitionService.getServiceDefinition(service.getServiceDefId(), service.getTenantId());
        String checkListName = serviceDefinition.getCode();
        String tenantId = service.getTenantId();
        String[] parts = serviceDefinition.getCode().split("\\.");
        String projectName = parts[0];
        String supervisorLevel = parts[2];
        String projectId;
        if (service.getAccountId() != null) {
            projectId = service.getAccountId();
        } else {
            projectId = projectService.getProjectByName(projectName, service.getTenantId()).getId();
        }
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

        String checkListToFilter = transformerProperties.getHfReferralFeverCheckListName().trim();
        List<AttributeValue> attributeValueList = service.getAttributes();
        Map<String, String> userInfoMap = userService.getUserInfo(service.getTenantId(), service.getAuditDetails().getCreatedBy());
        Map<String, String>  codeToQuestionMapping = new HashMap<>();
        getAttributeCodeMappings(codeToQuestionMapping);
        if (checkListName.trim().equals(checkListToFilter)) {
            String checklistName = serviceDefinition.getCode().split("\\.")[1];
            HfFeverServiceIndexV1 hfFeverServiceIndexV1 = HfFeverServiceIndexV1.builder()
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

            searchAndSetAttribute(attributeValueList, codeToQuestionMapping, hfFeverServiceIndexV1);
            hfFeverServiceIndexV1List.add(hfFeverServiceIndexV1);
        }
    }

    private void searchAndSetAttribute(List<AttributeValue> attributeValueList, Map<String, String> codeToQuestionMapping, HfFeverServiceIndexV1 hfFeverServiceIndexV1) {
        attributeValueList.forEach(attributeValue -> {
            String attributeCode = attributeValue.getAttributeCode();
            if (codeToQuestionMapping.containsKey(attributeCode)) {
                String question = codeToQuestionMapping.get(attributeCode);
                setAttributeValue(hfFeverServiceIndexV1, attributeValue.getValue(), question);
            }
        });
    }

    private static void setAttributeValue(HfFeverServiceIndexV1 hfFeverServiceIndexV1, Object value, String question) {
        switch (question) {
            case HF_TESTED_FOR_MALARIA: {
                hfFeverServiceIndexV1.setTestedForMalaria(value);
                break;
            }
            case HF_MALARIA_RESULT: {
                hfFeverServiceIndexV1.setMalariaResult(value);
                break;
            }
            case HF_ADMITTED_WITH_ILLNESS: {
                hfFeverServiceIndexV1.setAdmittedWithSeriousIllness(value);
                break;
            }
            case HF_TREATED_WITH_ANTI_MALARIALS: {
                hfFeverServiceIndexV1.setTreatedWithAntiMalarials(value);
                break;
            }
            case HF_NAME_OF_ANTI_MALARIALS: {
                hfFeverServiceIndexV1.setNameOfAntiMalarials(value);
                break;
            }
            case HF_NEGATIVE_RTD_GIVEN_SPAQ: {
                hfFeverServiceIndexV1.setNegativeRTDGivenSPAQ(value);
                break;
            }
            case HF_NEGATIVE_RTD_GIVEN_SPAQ_OUTCOME: {
                hfFeverServiceIndexV1.setNegativeRTDGivenSPAQOutcome(value);
                break;
            }
        }

    }

    private static void getAttributeCodeMappings(Map<String, String> attributeCodeToQuestionGroup) {
        attributeCodeToQuestionGroup.put("HF_REASON_FEVER_Q1", HF_TESTED_FOR_MALARIA);
        attributeCodeToQuestionGroup.put("HF_REASON_FEVER_Q2", HF_MALARIA_RESULT);
        attributeCodeToQuestionGroup.put("HF_REASON_FEVER_Q2.POSITIVE.FQ1", HF_ADMITTED_WITH_ILLNESS);
        attributeCodeToQuestionGroup.put("HF_REASON_FEVER_Q2.POSITIVE.FQ2", HF_TREATED_WITH_ANTI_MALARIALS);
        attributeCodeToQuestionGroup.put("HF_REASON_FEVER_Q2.POSITIVE.FQ2.YES.Q1", HF_NAME_OF_ANTI_MALARIALS);
        attributeCodeToQuestionGroup.put("HF_REASON_SICK_Q3", HF_NEGATIVE_RTD_GIVEN_SPAQ);
        attributeCodeToQuestionGroup.put("HF_REASON_SICK_Q3.YES.FQ3", HF_NEGATIVE_RTD_GIVEN_SPAQ_OUTCOME);
    }

}
