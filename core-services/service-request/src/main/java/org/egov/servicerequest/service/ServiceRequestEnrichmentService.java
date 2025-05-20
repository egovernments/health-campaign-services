package org.egov.servicerequest.service;

import digit.models.coremodels.AuditDetails;
import org.egov.common.contract.request.RequestInfo;
import org.egov.servicerequest.constants.Constants;
import org.egov.servicerequest.web.models.AttributeDefinition;
import org.egov.servicerequest.web.models.Service;
import org.egov.servicerequest.web.models.ServiceDefinition;
import org.egov.servicerequest.web.models.ServiceDefinitionRequest;
import org.egov.servicerequest.web.models.ServiceRequest;
import org.springframework.stereotype.Component;
import org.egov.servicerequest.web.models.AttributeValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ServiceRequestEnrichmentService {

    public void enrichServiceDefinitionRequest(ServiceDefinitionRequest serviceDefinitionRequest) {
        ServiceDefinition serviceDefinition = serviceDefinitionRequest.getServiceDefinition();
        RequestInfo requestInfo = serviceDefinitionRequest.getRequestInfo();

        // Enrich ID for service definition
        serviceDefinition.setId(UUID.randomUUID().toString());

        // Prepare audit details
        AuditDetails auditDetails = new AuditDetails();
        auditDetails.setCreatedBy(requestInfo.getUserInfo().getUuid());
        auditDetails.setLastModifiedBy(requestInfo.getUserInfo().getUuid());
        auditDetails.setCreatedTime(System.currentTimeMillis());
        auditDetails.setLastModifiedTime(System.currentTimeMillis());

        // Enrich audit details for attributes
        serviceDefinition.getAttributes().forEach(attribute -> {
            attribute.setId(UUID.randomUUID().toString());
            attribute.setAuditDetails(auditDetails);
            attribute.setReferenceId(serviceDefinition.getId());
        });

        // Enrich audit details for service definition
        serviceDefinition.setAuditDetails(auditDetails);

        // Initialize values with empty strings in case of non-list type attribute definition values
        serviceDefinition.getAttributes().forEach(attributeDefinition -> {
            if(!(attributeDefinition.getDataType().equals(AttributeDefinition.DataTypeEnum.SINGLEVALUELIST) || attributeDefinition.getDataType().equals(AttributeDefinition.DataTypeEnum.MULTIVALUELIST))){
                List<String> emptyStringList = new ArrayList<>();
                emptyStringList.add("");
                attributeDefinition.setValues(emptyStringList);
            }
        });

    }

    public Map<String, Object> enrichServiceRequest(ServiceRequest serviceRequest) {
        Service service = serviceRequest.getService();
        RequestInfo requestInfo = serviceRequest.getRequestInfo();

        // Enrich id for service
        service.setId(UUID.randomUUID().toString());

        // Prepare audit details
        AuditDetails auditDetails = new AuditDetails();
        auditDetails.setCreatedBy(requestInfo.getUserInfo().getUuid());
        auditDetails.setLastModifiedBy(requestInfo.getUserInfo().getUuid());
        auditDetails.setCreatedTime(System.currentTimeMillis());
        auditDetails.setLastModifiedTime(System.currentTimeMillis());

        // Enrich audit details in attribute values
        service.getAttributes().forEach(attributeValue -> {
            attributeValue.setId(UUID.randomUUID().toString());
            attributeValue.setAuditDetails(auditDetails);
            attributeValue.setReferenceId(service.getId());
        });

        // Enrich audit details for service
        service.setAuditDetails(auditDetails);

        // Convert incoming attribute value into JSON object
        Map<String, Object> attributeCodeVsValueMap = convertAttributeValuesIntoJson(serviceRequest);

        return attributeCodeVsValueMap;

    }

    private Map<String, Object> convertAttributeValuesIntoJson(ServiceRequest serviceRequest) {
        Map<String, Object> attributeCodeVsValueMap = new HashMap<>();
        serviceRequest.getService().getAttributes().forEach(attributeValue -> {
            attributeCodeVsValueMap.put(attributeValue.getAttributeCode(), attributeValue.getValue());
            Map<String, Object> jsonObj = new HashMap<>();
            jsonObj.put(Constants.VALUE, attributeValue.getValue());
            attributeValue.setValue(jsonObj);
        });
        return attributeCodeVsValueMap;
    }

    public void setAttributeDefinitionValuesBackToNativeState(ServiceDefinition serviceDefinition) {
        // Initialize values with null in case of non-list type attribute definition values
        serviceDefinition.getAttributes().forEach(attributeDefinition -> {
            if(!(attributeDefinition.getDataType().equals(AttributeDefinition.DataTypeEnum.SINGLEVALUELIST) || attributeDefinition.getDataType().equals(AttributeDefinition.DataTypeEnum.MULTIVALUELIST))){
                attributeDefinition.setValues(null);
            }
        });
    }

    public void setAttributeValuesBackToNativeState(ServiceRequest serviceRequest, Map<String, Object> attributeCodeVsValueMap) {
        serviceRequest.getService().getAttributes().forEach(attributeValue -> {
            attributeValue.setValue(attributeCodeVsValueMap.get(attributeValue.getAttributeCode()));
        });
    }

    private void updateAttributeDefinition(AttributeDefinition attributeDefinition, AttributeDefinition existingAttributeDefinition, RequestInfo requestInfo){

        attributeDefinition.setId(existingAttributeDefinition.getId());

        attributeDefinition.setAuditDetails(existingAttributeDefinition.getAuditDetails());

        attributeDefinition.setReferenceId(existingAttributeDefinition.getReferenceId());

        attributeDefinition.getAuditDetails().setLastModifiedTime(System.currentTimeMillis());

        attributeDefinition.getAuditDetails().setLastModifiedBy(requestInfo.getUserInfo().getUuid());

    }
    private void upsertAttributeDefinition(AttributeDefinition attributeDefinition, ServiceDefinitionRequest serviceDefinitionRequest){
        RequestInfo requestInfo = serviceDefinitionRequest.getRequestInfo();

        attributeDefinition.setId(UUID.randomUUID().toString());

        attributeDefinition.setAuditDetails(
          AuditDetails.builder()
            .createdBy(requestInfo.getUserInfo().getUuid())
            .createdTime(System.currentTimeMillis())
            .lastModifiedBy(requestInfo.getUserInfo().getUuid())
            .lastModifiedTime(System.currentTimeMillis())
            .build()
        );

        attributeDefinition.setReferenceId(serviceDefinitionRequest.getServiceDefinition().getId());
    }
    public void enrichServiceDefinitionUpdateRequest(ServiceDefinitionRequest serviceDefinitionRequest, ServiceDefinition definitionFromDb) {
        List<AttributeDefinition> attributeDefinitions = serviceDefinitionRequest.getServiceDefinition().getAttributes();

        //For quick lookup of Attribute Definition with Code
        Map<String,AttributeDefinition> existingAttributeCode = definitionFromDb
          .getAttributes()
          .stream()
          .collect(Collectors.toMap(AttributeDefinition::getCode, a->a));

        RequestInfo requestInfo = serviceDefinitionRequest.getRequestInfo();

        ServiceDefinition serviceDefinition = serviceDefinitionRequest.getServiceDefinition();

        serviceDefinition.setId(definitionFromDb.getId());
        serviceDefinition.setAuditDetails(definitionFromDb.getAuditDetails());
        serviceDefinition.getAuditDetails().setLastModifiedBy(requestInfo.getUserInfo().getUuid());
        serviceDefinition.getAuditDetails().setLastModifiedTime(System.currentTimeMillis());

        attributeDefinitions.forEach(attributeDefinition -> {
            if(existingAttributeCode.containsKey(attributeDefinition.getCode())){
                updateAttributeDefinition(attributeDefinition, existingAttributeCode.get(attributeDefinition.getCode()), requestInfo);
            }
            else{
                upsertAttributeDefinition(attributeDefinition, serviceDefinitionRequest);
            }

            if(!(attributeDefinition.getDataType().equals(AttributeDefinition.DataTypeEnum.SINGLEVALUELIST) || attributeDefinition.getDataType().equals(AttributeDefinition.DataTypeEnum.MULTIVALUELIST))){
                List<String> emptyStringList = new ArrayList<>();
                emptyStringList.add("");
                attributeDefinition.setValues(emptyStringList);
            }
        });

    }

    /**
        * Updates an attribute value using data from an existing attribute value.
        *
        * @param attributeValue The attribute value to update
        * @param existingAttributeValue The existing attribute value from the database
        * @param requestInfo The request information containing user details
     */
    private void updateAttributeValue(AttributeValue attributeValue, AttributeValue existingAttributeValue, RequestInfo requestInfo){

        attributeValue.setId(existingAttributeValue.getId());

        attributeValue.setAuditDetails(existingAttributeValue.getAuditDetails());

        attributeValue.setReferenceId(existingAttributeValue.getReferenceId());

        attributeValue.getAuditDetails().setLastModifiedTime(System.currentTimeMillis());

        attributeValue.getAuditDetails().setLastModifiedBy(requestInfo.getUserInfo().getUuid());

    }

    /**
        * Enriches a service update request by updating IDs, audit details, and attribute values
        * based on the existing service data from the database.
        *
        * @param serviceRequest The service update request to enrich
        * @param serviceFromDb The existing service data from the database
        * @return A map of attribute codes to their original values
     */
    public void enrichServiceUpdateRequest(ServiceRequest serviceRequest,org.egov.servicerequest.web.models.Service serviceFromDb) {
        Service service = serviceRequest.getService();
        RequestInfo requestInfo = serviceRequest.getRequestInfo();
        List<AttributeValue> attributeValues = service.getAttributes();

        Map<String,AttributeValue> existingAttributeCode = serviceFromDb
                .getAttributes()
                .stream()
                .collect(Collectors.toMap(AttributeValue::getAttributeCode, a->a));

        service.setId(serviceFromDb.getId());
        service.setAuditDetails(serviceFromDb.getAuditDetails());
        service.getAuditDetails().setLastModifiedBy(requestInfo.getUserInfo().getUuid());
        service.getAuditDetails().setLastModifiedTime(System.currentTimeMillis());

        attributeValues.forEach(attributeValue -> {
            if(existingAttributeCode.containsKey(attributeValue.getAttributeCode())){
                updateAttributeValue(attributeValue, existingAttributeCode.get(attributeValue.getAttributeCode()), requestInfo);
            }
        });

        Map<String, Object> attributeCodeVsValueMap = convertAttributeValuesIntoJson(serviceRequest);

    }
}
