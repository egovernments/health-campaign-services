package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.Project;
import org.egov.transformer.Constants;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.ReferralServiceTaskIndexV1;
import org.egov.transformer.models.upstream.AttributeValue;
import org.egov.transformer.models.upstream.Service;
import org.egov.transformer.models.upstream.ServiceDefinition;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.BoundaryService;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.service.ServiceDefinitionService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.*;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class ReferralServiceTaskTransformationService {
    private final ServiceDefinitionService serviceDefinitionService;
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final ProjectService projectService;
    private final UserService userService;
    private final CommonUtils commonUtils;
    private final ObjectMapper objectMapper;
    private final BoundaryService boundaryService;


    public ReferralServiceTaskTransformationService(ServiceDefinitionService serviceDefinitionService, TransformerProperties transformerProperties, Producer producer, ProjectService projectService, UserService userService, CommonUtils commonUtils, ObjectMapper objectMapper, BoundaryService boundaryService) {

        this.serviceDefinitionService = serviceDefinitionService;
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.projectService = projectService;
        this.userService = userService;
        this.commonUtils = commonUtils;
        this.objectMapper = objectMapper;
        this.boundaryService = boundaryService;
    }

    public void transform(List<Service> serviceList) {
        List<ReferralServiceTaskIndexV1> referralServiceTaskIndexV1List = new ArrayList<>();
        String topic = transformerProperties.getTransformerProducerServiceIndexV2Topic();
        serviceList.forEach(service -> {
            transform(service, referralServiceTaskIndexV1List);
        });
        if (!referralServiceTaskIndexV1List.isEmpty()) {
            producer.push(topic, referralServiceTaskIndexV1List);
        }
    }

    private void transform(Service service, List<ReferralServiceTaskIndexV1> referralServiceTaskIndexV1List) {
        ServiceDefinition serviceDefinition = serviceDefinitionService.getServiceDefinition(service.getServiceDefId(), service.getTenantId());
        String checkListName = serviceDefinition.getCode();
        String tenantId = service.getTenantId();
        String[] parts = serviceDefinition.getCode().split("\\.");
        String projectName = parts[0];
        String supervisorLevel = parts[2];
        String projectId = service.getAccountId() != null ? service.getAccountId() :
                projectService.getProjectByName(projectName, service.getTenantId()).getId();
        Project project = projectService.getProject(projectId, tenantId);
        String projectTypeId = project.getProjectTypeId();
        BoundaryHierarchyResult boundaryHierarchyResult;

        if (service.getAdditionalDetails() != null) {
            boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode((String) service.getAdditionalDetails(), service.getTenantId());
        } else {
            boundaryHierarchyResult = projectService.getBoundaryCodeToNameMapByProjectIdV2(projectId, service.getTenantId());
        }
//        log.info("boundary labels {}", boundaryLabelToNameMap.toString());
        Map<String, String > boundaryHierarchyMap = boundaryHierarchyResult.getBoundaryHierarchy();
        ObjectNode boundaryHierarchy = objectMapper.convertValue(boundaryHierarchyMap, ObjectNode.class);
        Map<String, String > boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(service.getAuditDetails().getCreatedTime());

        Integer cycleIndex = commonUtils.fetchCycleIndex(tenantId, projectTypeId, service.getAuditDetails());
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        additionalDetails.put(CYCLE_INDEX, cycleIndex);

        String checkListToFilter = transformerProperties.getCheckListName().trim();
        List<AttributeValue> attributeValueList = service.getAttributes();
        Map<String, Map<String, String>> attributeCodeToQuestionAgeGroup = new HashMap<>();
        Map<String, String> userInfoMap = userService.getUserInfo(service.getTenantId(), service.getAuditDetails().getCreatedBy());
        getAttributeCodeMappings(attributeCodeToQuestionAgeGroup);
        if (checkListName.trim().equals(checkListToFilter)) {
            String finalProjectId = projectId;
            String checklistName = serviceDefinition.getCode().split("\\.")[1];
            attributeCodeToQuestionAgeGroup.forEach((key, value) -> {
                ReferralServiceTaskIndexV1 referralServiceTaskIndexV1 = ReferralServiceTaskIndexV1.builder()
                        .id(service.getId())
                        .supervisorLevel(supervisorLevel)
                        .checklistName(checklistName)
                        .ageGroup(key)
                        .tenantId(tenantId)
                        .projectId(finalProjectId)
                        .userName(userInfoMap.get(USERNAME))
                        .role(userInfoMap.get(ROLE))
                        .userAddress(userInfoMap.get(CITY))
                        .createdTime(service.getAuditDetails().getCreatedTime())
                        .syncedTime(service.getAuditDetails().getCreatedTime())
                        .taskDates(commonUtils.getDateFromEpoch(service.getAuditDetails().getLastModifiedTime()))
                        .createdBy(service.getAuditDetails().getCreatedBy())
                        .syncedTimeStamp(syncedTimeStamp)
                        .boundaryHierarchy(boundaryHierarchy)
                        .boundaryHierarchyCode(boundaryHierarchyCode)
                        .additionalDetails(additionalDetails)
                        .build();

                searchAndSetAttribute(attributeValueList, value, referralServiceTaskIndexV1);
                referralServiceTaskIndexV1List.add(referralServiceTaskIndexV1);
            });
        }
    }

    private void searchAndSetAttribute(List<AttributeValue> attributeValueList, Map<String, String> codeToQuestionMapping, ReferralServiceTaskIndexV1 referralServiceTaskIndexV1) {
        attributeValueList.forEach(attributeValue -> {
            String attributeCode = attributeValue.getAttributeCode();
            if (codeToQuestionMapping.containsKey(attributeCode)) {
                String question = codeToQuestionMapping.get(attributeCode);
                setAttributeValue(referralServiceTaskIndexV1, attributeValue.getValue(), question);
            }
        });
    }

    private static void setAttributeValue(ReferralServiceTaskIndexV1 referralServiceTaskIndexV1, Object value, String question) {
        switch (question) {
            case Constants.CHILDREN_PRESENTED_US: {
                referralServiceTaskIndexV1.setChildrenPresentedUS(value);
                break;
            }
            case Constants.MALARIA_POSITIVE_US: {
                referralServiceTaskIndexV1.setMalariaPositiveUS(value);
                break;
            }
            case Constants.MALARIA_NEGATIVE_US: {
                referralServiceTaskIndexV1.setMalariaNegativeUS(value);
                break;
            }
            case Constants.CHILDREN_PRESENTED_APE: {
                referralServiceTaskIndexV1.setChildrenPresentedAPE(value);
                break;
            }
            case Constants.MALARIA_POSITIVE_APE: {
                referralServiceTaskIndexV1.setMalariaPositiveAPE(value);
                break;
            }
            case Constants.MALARIA_NEGATIVE_APE: {
                referralServiceTaskIndexV1.setMalariaNegativeAPE(value);
                break;
            }
        }

    }

    private static void getAttributeCodeMappings(Map<String, Map<String, String>> attributeCodeToQuestionAgeGroup) {
        Map<String, String> codeVsQuestionMappingGroup1 = new HashMap<>();
        Map<String, String> codeVsQuestionMappingGroup2 = new HashMap<>();
        getGroup1Map(codeVsQuestionMappingGroup1);
        getGroup2Map(codeVsQuestionMappingGroup2);
        attributeCodeToQuestionAgeGroup.put("3-11 months", codeVsQuestionMappingGroup1);
        attributeCodeToQuestionAgeGroup.put("12-59 months", codeVsQuestionMappingGroup2);
    }

    private static void getGroup1Map(Map<String, String> codeVsQuestionMappingGroup1) {
        codeVsQuestionMappingGroup1.put("SM1", Constants.CHILDREN_PRESENTED_US);
        codeVsQuestionMappingGroup1.put("SM3", Constants.MALARIA_POSITIVE_US);
        codeVsQuestionMappingGroup1.put("SM5", Constants.MALARIA_NEGATIVE_US);
        codeVsQuestionMappingGroup1.put("SM7", Constants.CHILDREN_PRESENTED_APE);
        codeVsQuestionMappingGroup1.put("SM9", Constants.MALARIA_POSITIVE_APE);
        codeVsQuestionMappingGroup1.put("SM11", Constants.MALARIA_NEGATIVE_APE);
    }

    private static void getGroup2Map(Map<String, String> codeVsQuestionMappingGroup2) {
        codeVsQuestionMappingGroup2.put("SM2", Constants.CHILDREN_PRESENTED_US);
        codeVsQuestionMappingGroup2.put("SM4", Constants.MALARIA_POSITIVE_US);
        codeVsQuestionMappingGroup2.put("SM6", Constants.MALARIA_NEGATIVE_US);
        codeVsQuestionMappingGroup2.put("SM8", Constants.CHILDREN_PRESENTED_APE);
        codeVsQuestionMappingGroup2.put("SM10", Constants.MALARIA_POSITIVE_APE);
        codeVsQuestionMappingGroup2.put("SM12", Constants.MALARIA_NEGATIVE_APE);
    }
}
