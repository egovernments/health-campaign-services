package org.egov.servicerequest.validators;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.servicerequest.repository.ServiceDefinitionRequestRepository;
import org.egov.servicerequest.repository.ServiceRequestRepository;
import org.egov.servicerequest.web.models.*;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.egov.servicerequest.error.ErrorCode.*;

@Component
public class ServiceDefinitionRequestValidator {

    @Autowired
    private ServiceDefinitionRequestRepository serviceDefinitionRequestRepository;

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;


    public void validateServiceDefinitionRequest(ServiceDefinitionRequest serviceDefinitionRequest) throws InvalidTenantIdException {
        ServiceDefinition serviceDefinition = serviceDefinitionRequest.getServiceDefinition();

        // Validate if a service definition with the same combination of tenantId and code already exists
        validateServiceDefinitionExistence(serviceDefinition);

        // Validate if all attribute definitions provided as part of service definitions have unique code
        validateAttributeDefinitionUniqueness(serviceDefinition);

        // Validate values provided in attribute definitions as per data type
        validateAttributeValuesAsPerDataType(serviceDefinition);

        // Validate regex values provided in attribute definitions
        validateRegex(serviceDefinition);

    }

    private void validateRegex(ServiceDefinition serviceDefinition) {
        serviceDefinition.getAttributes().forEach(attributeDefinition -> {
            if(!ObjectUtils.isEmpty(attributeDefinition.getRegex())){
                try {
                    Pattern.compile(attributeDefinition.getRegex(), Pattern.CASE_INSENSITIVE);
                }catch (Exception e){
                    throw new CustomException(INVALID_REGEX_ERR_CODE, INVALID_REGEX_ERR_MSG + attributeDefinition.getCode());
                }
            }
        });
    }

    private void validateAttributeValuesAsPerDataType(ServiceDefinition serviceDefinition) {
        // Values should be present only in case of single value and multi value lists
        serviceDefinition.getAttributes().forEach(attributeDefinition -> {
            AttributeDefinition.DataTypeEnum dataType = attributeDefinition.getDataType();

            if(!(dataType.equals(AttributeDefinition.DataTypeEnum.SINGLEVALUELIST) || dataType.equals(AttributeDefinition.DataTypeEnum.MULTIVALUELIST))){
                if(!CollectionUtils.isEmpty(attributeDefinition.getValues()))
                    throw new CustomException(INVALID_ATTRIBUTE_DEFINITION_ERR_CODE, INVALID_ATTRIBUTE_DEFINITION_ERR_MSG);
            }
        });
    }

    private void validateAttributeDefinitionUniqueness(ServiceDefinition serviceDefinition) {
        Set<String> attributeCodes = new HashSet<>();

        serviceDefinition.getAttributes().forEach(attributeDefinition -> {
            if(attributeCodes.contains(attributeDefinition.getCode())){
                throw new CustomException(ATTRIBUTE_CODE_UNIQUENESS_ERR_CODE, ATTRIBUTE_CODE_UNIQUENESS_ERR_MSG);
            }else{
                attributeCodes.add(attributeDefinition.getCode());
            }
        });
    }

    private void validateServiceDefinitionExistence(ServiceDefinition serviceDefinition) throws InvalidTenantIdException {
        List<ServiceDefinition> serviceDefinitionList = serviceDefinitionRequestRepository.getServiceDefinitions(ServiceDefinitionSearchRequest.builder().includeDeleted(true).serviceDefinitionCriteria(ServiceDefinitionCriteria.builder().tenantId(serviceDefinition.getTenantId()).code(Collections.singletonList(serviceDefinition.getCode())).build()).build());
        if(!CollectionUtils.isEmpty(serviceDefinitionList)){
            throw new CustomException(SERVICE_DEFINITION_ALREADY_EXISTS_ERR_CODE, SERVICE_DEFINITION_ALREADY_EXISTS_ERR_MSG);
        }
    }

    private List<ServiceDefinition> validateExistence(ServiceDefinition serviceDefinition) throws InvalidTenantIdException {
        List<ServiceDefinition> serviceDefinitionList = serviceDefinitionRequestRepository.
          getServiceDefinitions(ServiceDefinitionSearchRequest.builder()
            .includeDeleted(true)
            .serviceDefinitionCriteria(ServiceDefinitionCriteria.builder().tenantId(serviceDefinition.getTenantId()).code(Collections.singletonList(serviceDefinition.getCode())).build()).build());

        //Check if valid service definition exists
        if (CollectionUtils.isEmpty(serviceDefinitionList)) {
            throw new CustomException(SERVICE_DEFINITION_NOT_EXIST_ERR_CODE, SERVICE_DEFINITION_NOT_EXIST_ERR_MSG);
        }

        return serviceDefinitionList;
    }

    private void validateService(List<ServiceDefinition> serviceDefinition) throws InvalidTenantIdException {
        List<Service> service = serviceRequestRepository.getService(
          ServiceSearchRequest.builder().serviceCriteria(
            ServiceCriteria.builder().serviceDefIds(Collections.singletonList(serviceDefinition.get(0).getId())).build()
          ).build()
        );
        // If the service mapping doesn't exist throw an error
        if(CollectionUtils.isEmpty(service)){
            throw new CustomException(VALID_SERVICE_DOES_NOT_EXIST_ERR_CODE, VALID_SERVICE_DOES_NOT_EXIST_ERR_MSG);
        }
    }

    public ServiceDefinition validateUpdateRequest(ServiceDefinitionRequest serviceDefinitionRequest) throws InvalidTenantIdException {
        ServiceDefinition serviceDefinition = serviceDefinitionRequest.getServiceDefinition();

        //Validate if a  Service Definition exists
        List<ServiceDefinition> serviceDefinitionList = validateExistence(serviceDefinition);

        // Validate if a Service exists corresponding to this Service Definition
        // validateService(serviceDefinitionList);

        // Validate if all attribute definitions provided as part of service definitions have unique code
        validateAttributeDefinitionUniqueness(serviceDefinition);

        // Validate values provided in attribute definitions as per data type
        validateAttributeValuesAsPerDataType(serviceDefinition);

        // Validate regex values provided in attribute definitions
        validateRegex(serviceDefinition);

        return serviceDefinitionList.get(0);
    }

}
