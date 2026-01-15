package org.egov.servicerequest.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.servicerequest.config.Configuration;
import org.egov.servicerequest.repository.ServiceDefinitionRequestRepository;
import org.egov.servicerequest.repository.ServiceRequestRepository;
import org.egov.servicerequest.web.models.AttributeDefinition;
import org.egov.servicerequest.web.models.AttributeValue;
import org.egov.servicerequest.web.models.Service;
import org.egov.servicerequest.web.models.ServiceCriteria;
import org.egov.servicerequest.web.models.ServiceDefinition;
import org.egov.servicerequest.web.models.ServiceDefinitionCriteria;
import org.egov.servicerequest.web.models.ServiceDefinitionSearchRequest;
import org.egov.servicerequest.web.models.ServiceRequest;
import org.egov.servicerequest.web.models.ServiceSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.egov.servicerequest.error.ErrorCode.*;

@Slf4j
@Component
public class ServiceRequestValidator {

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

    @Autowired
    private ServiceDefinitionRequestRepository serviceDefinitionRequestRepository;

    @Autowired
    private Configuration config;

    public void validateServiceRequest(ServiceRequest serviceRequest) throws InvalidTenantIdException {
        List<ServiceDefinition> serviceDefinitions = validateServiceDefID(serviceRequest.getService().getTenantId(), serviceRequest.getService().getServiceDefId());
        validateServiceRequestAlreadyExists(serviceRequest);
        validateAttributeValuesAgainstServiceDefinition(serviceDefinitions.get(0), serviceRequest.getService().getAttributes());
        validateAccountId(serviceRequest.getService());
    }

    private void validateAccountId(Service service) {
        // TO DO
    }


    private void validateAttributeValuesAgainstServiceDefinition(ServiceDefinition serviceDefinition, List<AttributeValue> attributeValues) {

        // Prepare map of attribute code vs attribute type and
        // validate uniqueness of attribute value codes being passed against service definition
        Map<String, AttributeDefinition.DataTypeEnum> attributeCodeVsDataType = new HashMap<>();
        Set<String> setOfRequiredAttributes = new HashSet<>();
        Map<String, Set<String>> attributeCodeVsValues = new HashMap<>();
        serviceDefinition.getAttributes().forEach(attributeDefinition -> {
            attributeCodeVsDataType.put(attributeDefinition.getCode(), attributeDefinition.getDataType());

            if(attributeDefinition.getDataType().equals(AttributeDefinition.DataTypeEnum.SINGLEVALUELIST) || attributeDefinition.getDataType().equals(AttributeDefinition.DataTypeEnum.MULTIVALUELIST)){
                attributeCodeVsValues.put(attributeDefinition.getCode(), new HashSet<>(attributeDefinition.getValues()));
            }

            if(attributeDefinition.getRequired())
                setOfRequiredAttributes.add(attributeDefinition.getCode());
        });

        // Check if service has all the attribute values required as part of service definition
        Set<String> setOfAttributeValues = new HashSet<>();
        attributeValues.forEach(attributeValue -> {
            if(!attributeCodeVsDataType.keySet().contains(attributeValue.getAttributeCode())){
                throw new CustomException(SERVICE_REQUEST_UNRECOGNIZED_ATTRIBUTE_CODE, SERVICE_REQUEST_UNRECOGNIZED_ATTRIBUTE_MSG);
            }

            if(!setOfAttributeValues.contains(attributeValue.getAttributeCode()))
                setOfAttributeValues.add(attributeValue.getAttributeCode());
            else
                throw new CustomException(SERVICE_REQUEST_ATTRIBUTE_VALUES_UNIQUENESS_ERR_CODE, SERVICE_REQUEST_ATTRIBUTE_VALUES_UNIQUENESS_ERR_MSG);
        });

        // Check if all required attributes have been provided as part of service
        setOfRequiredAttributes.forEach(requiredAttribute -> {
            if(!setOfAttributeValues.contains(requiredAttribute))
                throw new CustomException(SERVICE_REQUEST_REQUIRED_ATTRIBUTE_NOT_PROVIDED_ERR_CODE, SERVICE_REQUEST_REQUIRED_ATTRIBUTE_NOT_PROVIDED_ERR_MSG);
        });

        // Validate if value being passed is consistent in terms of data type provided as part of service definition
        attributeValues.forEach(attributeValue -> {
            if (attributeValue.getValue() == null && !setOfRequiredAttributes.contains(attributeValue.getAttributeCode())) {
                return;
            }
            if(attributeCodeVsDataType.get(attributeValue.getAttributeCode()).equals(AttributeDefinition.DataTypeEnum.NUMBER)){
                if(!(attributeValue.getValue() instanceof Number)){
                    throw new CustomException(SERVICE_REQUEST_ATTRIBUTE_INVALID_VALUE_CODE, SERVICE_REQUEST_ATTRIBUTE_INVALID_NUMBER_VALUE_MSG);
                }
            }else if(attributeCodeVsDataType.get(attributeValue.getAttributeCode()).equals(AttributeDefinition.DataTypeEnum.STRING)){
                if(!(attributeValue.getValue() instanceof String)){
                    throw new CustomException(SERVICE_REQUEST_ATTRIBUTE_INVALID_VALUE_CODE, SERVICE_REQUEST_ATTRIBUTE_INVALID_STRING_VALUE_MSG);
                }
                validateSize(attributeValue.getValue());
            }else if(attributeCodeVsDataType.get(attributeValue.getAttributeCode()).equals(AttributeDefinition.DataTypeEnum.TEXT)){
                if(!(attributeValue.getValue() instanceof String)){
                    throw new CustomException(SERVICE_REQUEST_ATTRIBUTE_INVALID_VALUE_CODE, SERVICE_REQUEST_ATTRIBUTE_INVALID_TEXT_VALUE_MSG);
                }
                validateSize(attributeValue.getValue());
            }else if(attributeCodeVsDataType.get(attributeValue.getAttributeCode()).equals(AttributeDefinition.DataTypeEnum.DATETIME)){
                if(!(attributeValue.getValue() instanceof Long)){
                    throw new CustomException(SERVICE_REQUEST_ATTRIBUTE_INVALID_VALUE_CODE, SERVICE_REQUEST_ATTRIBUTE_INVALID_DATETIME_VALUE_MSG);
                }
            }else if(attributeCodeVsDataType.get(attributeValue.getAttributeCode()).equals(AttributeDefinition.DataTypeEnum.SINGLEVALUELIST)){
                if(!(attributeValue.getValue() instanceof String)){
                    throw new CustomException(SERVICE_REQUEST_ATTRIBUTE_INVALID_VALUE_CODE, SERVICE_REQUEST_ATTRIBUTE_INVALID_SINGLE_VALUE_LIST_VALUE_MSG);
                }
            }
//            else if(attributeCodeVsDataType.get(attributeValue.getAttributeCode()).equals(AttributeDefinition.DataTypeEnum.MULTIVALUELIST)){
//                if(!(attributeValue.getValue() instanceof List)){
//                    throw new CustomException(SERVICE_REQUEST_ATTRIBUTE_INVALID_VALUE_CODE, SERVICE_REQUEST_ATTRIBUTE_INVALID_MULTI_VALUE_LIST_VALUE_MSG);
//                }
//            }
            else if(attributeCodeVsDataType.get(attributeValue.getAttributeCode()).equals(AttributeDefinition.DataTypeEnum.BOOLEAN)){
                if(!(attributeValue.getValue() instanceof Boolean)){
                    throw new CustomException(SERVICE_REQUEST_ATTRIBUTE_INVALID_VALUE_CODE, SERVICE_REQUEST_ATTRIBUTE_INVALID_BOOLEAN_VALUE_MSG);
                }
            }
        });

        // Validate if value provided against attribute definition of single value list and multi value list is the same as the list of values provided during creation
        attributeValues.forEach(attributeValue -> {
            if (attributeValue.getValue() == null && !setOfRequiredAttributes.contains(attributeValue.getAttributeCode())) {
                return;
            }
            if(attributeCodeVsValues.containsKey(attributeValue.getAttributeCode())){
                if(attributeCodeVsDataType.get(attributeValue.getAttributeCode()).equals(AttributeDefinition.DataTypeEnum.SINGLEVALUELIST)){
                    if(!attributeCodeVsValues.get(attributeValue.getAttributeCode()).contains(attributeValue.getValue())){
                        throw new CustomException(SERVICE_REQUEST_ATTRIBUTE_INVALID_VALUE_CODE, SERVICE_REQUEST_ATTRIBUTE_INVALID_VALUE_SINGLEVALUELIST_MSG);
                    }
                }
//                else if(attributeCodeVsDataType.get(attributeValue.getAttributeCode()).equals(AttributeDefinition.DataTypeEnum.MULTIVALUELIST)){
//                    List<String> providedAttributeValues = (List<String>) attributeValue.getValue();
//                    providedAttributeValues.forEach(providedAttributeValue -> {
//                        if(!attributeCodeVsValues.get(attributeValue.getAttributeCode()).contains(providedAttributeValue)){
//                            throw new CustomException(SERVICE_REQUEST_ATTRIBUTE_INVALID_VALUE_CODE, SERVICE_REQUEST_ATTRIBUTE_INVALID_VALUE_MULTIVALUELIST_MSG);
//                        }
//                    });
//                }
            }
        });

    }

    private void validateSize(Object value) {
        String incomingValue = (String) value;

        if(incomingValue.length() > config.getMaxStringInputSize()){
            throw new CustomException(INVALID_SIZE_OF_INPUT_CODE, INVALID_SIZE_OF_TEXT_MSG);
        }
    }

    private List<ServiceDefinition> validateServiceDefID(String tenantId, String serviceDefId) throws InvalidTenantIdException {
        List<ServiceDefinition> serviceDefinitions = serviceDefinitionRequestRepository.getServiceDefinitions(ServiceDefinitionSearchRequest.builder().serviceDefinitionCriteria(ServiceDefinitionCriteria.builder().tenantId(tenantId).ids(Arrays.asList(serviceDefId)).build()).build());

        if(serviceDefinitions.isEmpty())
            throw new CustomException(SERVICE_REQUEST_INVALID_SERVICE_DEF_ID_CODE, SERVICE_REQUEST_INVALID_SERVICE_DEF_ID_MSG);

        return serviceDefinitions;
    }

    private void validateServiceRequestAlreadyExists(ServiceRequest serviceRequest) throws InvalidTenantIdException {
        Service service = serviceRequest.getService();
        List<Service> services = serviceRequestRepository.getService(ServiceSearchRequest.builder()
                .serviceCriteria(ServiceCriteria.builder()
                        .clientId(service.getClientId())
                        .tenantId(service.getTenantId())
                        .build())
                .build());
        if (!CollectionUtils.isEmpty(services)) {
            throw new CustomException(SERVICE_ALREADY_EXISTS_ERR_CODE, SERVICE_ALREADY_EXISTS_FOR_CLIENT_ID_ERR_MSG);
        }
    }

    private List<Service> validateExistingServiceRequest(ServiceRequest serviceRequest) throws InvalidTenantIdException {
        Service service = serviceRequest.getService();
        List<Service> services = serviceRequestRepository.getService(ServiceSearchRequest.builder()
                        .serviceCriteria(ServiceCriteria.builder()
                                .ids(Collections.singletonList(service.getId()))
                                .tenantId(service.getTenantId())
                                .build())
                .build());
        if (CollectionUtils.isEmpty(services)) {
            throw new CustomException(SERVICE_DOES_NOT_EXIST_ERR_CODE, SERVICE_DOES_NOT_EXIST_ERR_MSG);
        }
        return services;
    }

    private List<AttributeValue> validateIdForAttributeValues(Service service, Service existingFromDB) {
        Map<String, AttributeValue> existingAttributeValueMap;
        Map<String, AttributeValue> attributeValueMap;

        try {
            existingAttributeValueMap = existingFromDB.getAttributes().stream()
                    .collect(Collectors.toMap(
                            AttributeValue::getId,
                            attributeValue -> attributeValue
                    ));
            attributeValueMap = service.getAttributes().stream()
                    .filter(attributeValue -> !ObjectUtils.isEmpty(attributeValue.getId()))
                    .collect(Collectors.toMap(
                            AttributeValue::getId,
                            attributeValue -> attributeValue
                    ));
        } catch (Exception ex) {
            throw new CustomException(SERVICE_ATTRIBUTE_VALUE_INVALID_ERR_CODE, SERVICE_ATTRIBUTE_VALUE_DUPLICATE_ERR_MSG);
        }
        service.getAttributes().forEach(attributeValue -> {
            if(!ObjectUtils.isEmpty(attributeValue.getId()) && !existingAttributeValueMap.containsKey(attributeValue.getId())) {
                throw new CustomException(SERVICE_ATTRIBUTE_VALUE_INVALID_ERR_CODE, SERVICE_ATTRIBUTE_VALUE_DOES_NOT_EXIST_ERR_MSG);
            }
        });
        List<AttributeValue> attributeValuesToUpdate = existingFromDB.getAttributes().stream().map(attributeValue -> {
            if(attributeValueMap.containsKey(attributeValue.getId())) {
                return attributeValueMap.get(attributeValue.getId());
            }
            return attributeValue;
        }).collect(Collectors.toList());
        service.getAttributes().stream().filter(attributeValue -> ObjectUtils.isEmpty(attributeValue.getId()))
                .forEach(attributeValue -> {
                    if (existingAttributeValueMap.values().stream()
                            .anyMatch(existingAttributeValue -> existingAttributeValue
                                    .getAttributeCode().equals(attributeValue.getAttributeCode()))) {
                        throw new CustomException(SERVICE_ATTRIBUTE_VALUE_INVALID_ERR_CODE, SERVICE_ATTRIBUTE_VALUE_ALREADY_EXISTS_ERR_MSG);
                    }
                    attributeValuesToUpdate.add(attributeValue);
                });
        return attributeValuesToUpdate;
    }

    public Service validateServiceUpdateRequest(ServiceRequest serviceRequest) throws InvalidTenantIdException {
        List<Service> existingService = validateExistingServiceRequest(serviceRequest);
        List<ServiceDefinition> serviceDefinitions = validateServiceDefID(serviceRequest.getService().getTenantId(), serviceRequest.getService().getServiceDefId());
        List<AttributeValue> attributeValues = validateIdForAttributeValues(serviceRequest.getService(), existingService.get(0));
        validateAttributeValuesAgainstServiceDefinition(serviceDefinitions.get(0), attributeValues);
        serviceRequest.getService().setAttributes(attributeValues);
        validateAccountId(serviceRequest.getService());
        return existingService.get(0);
    }
}
