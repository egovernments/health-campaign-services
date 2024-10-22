package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.ChecklistAnswerIndexV1;
import org.egov.transformer.models.downstream.ServiceIndexV1;
import org.egov.transformer.models.upstream.AttributeValue;
import org.egov.transformer.producer.Producer;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ChecklistAnswerTransformationService {
    private final Producer producer;
    private final TransformerProperties transformerProperties;
    private final ObjectMapper objectMapper;

    public ChecklistAnswerTransformationService(Producer producer, TransformerProperties transformerProperties, ObjectMapper objectMapper) {
        this.producer = producer;
        this.transformerProperties = transformerProperties;
        this.objectMapper = objectMapper;
    }

    public void transform(ServiceIndexV1 serviceIndexV1) {
        log.info("transforming checklist answers for SERVICE TASK id {}", serviceIndexV1.getId());
        String topic = transformerProperties.getTransformerProducerChecklistAnswerIndexV1Topic();
        ChecklistAnswerIndexV1 checklistAnswerIndexV1 = transformChecklistAnswers(serviceIndexV1);
        log.info("transformation of checklist answers success for SERVICE TASK id {}", checklistAnswerIndexV1.getId());
        producer.push(topic, checklistAnswerIndexV1);
    }

    private ChecklistAnswerIndexV1 transformChecklistAnswers(ServiceIndexV1 serviceIndexV1){

        ObjectNode checklistAnswers = transformAttributes(serviceIndexV1.getAttributes());

        ChecklistAnswerIndexV1 checklistAnswerIndexV1 = ChecklistAnswerIndexV1.builder()
                .id(serviceIndexV1.getId())
                .createdTime(serviceIndexV1.getCreatedTime())
                .createdBy(serviceIndexV1.getCreatedBy())
                .supervisorLevel(serviceIndexV1.getSupervisorLevel())
                .checklistName(serviceIndexV1.getChecklistName())
                .projectId(serviceIndexV1.getProjectId())
                .serviceDefinitionId(serviceIndexV1.getServiceDefinitionId())
                .userName(serviceIndexV1.getUserName())
                .nameOfUser(serviceIndexV1.getNameOfUser())
                .role(serviceIndexV1.getRole())
                .userId(serviceIndexV1.getUserId())
                .boundaryHierarchy(serviceIndexV1.getBoundaryHierarchy())
                .boundaryHierarchyCode(serviceIndexV1.getBoundaryHierarchyCode())
                .tenantId(serviceIndexV1.getTenantId())
                .userId(serviceIndexV1.getUserId())
                .clientReferenceId(serviceIndexV1.getClientReferenceId())
                .syncedTimeStamp(serviceIndexV1.getSyncedTimeStamp())
                .syncedTime(serviceIndexV1.getSyncedTime())
                .taskDates(serviceIndexV1.getTaskDates())
                .additionalDetails(serviceIndexV1.getAdditionalDetails())
                .checklistAnswers(checklistAnswers)
                .build();

        return checklistAnswerIndexV1;
    }

    public ObjectNode transformAttributes(List<AttributeValue> attributeValues) {
        ObjectNode checklistAnswers = objectMapper.createObjectNode();

        for (AttributeValue attributeValue : attributeValues) {
            String attributeCode = attributeValue.getAttributeCode();
            Object value = attributeValue.getValue();

            // Adding the value object as-is
            checklistAnswers.putPOJO(attributeCode, value);
        }

        return checklistAnswers;
    }
}
