package org.egov.servicerequest.service;

import org.egov.servicerequest.config.Configuration;
import org.egov.servicerequest.kafka.Producer;
import org.egov.servicerequest.repository.ServiceDefinitionRequestRepository;
import org.egov.servicerequest.validators.ServiceDefinitionRequestValidator;
import org.egov.servicerequest.web.models.ServiceDefinition;
import org.egov.servicerequest.web.models.ServiceDefinitionRequest;
import org.egov.servicerequest.web.models.ServiceDefinitionSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class ServiceDefinitionRequestService {

    @Autowired
    private ServiceDefinitionRequestValidator serviceDefinitionRequestValidator;

    @Autowired
    private ServiceRequestEnrichmentService enrichmentService;

    @Autowired
    private ServiceDefinitionRequestRepository serviceDefinitionRequestRepository;

    @Autowired
    private Producer producer;

    @Autowired
    private Configuration config;

    public ServiceDefinition createServiceDefinition(ServiceDefinitionRequest serviceDefinitionRequest) {

        ServiceDefinition serviceDefinition = serviceDefinitionRequest.getServiceDefinition();

        // Validate incoming service definition request
        serviceDefinitionRequestValidator.validateServiceDefinitionRequest(serviceDefinitionRequest);

        // Enrich incoming service definition request
        enrichmentService.enrichServiceDefinitionRequest(serviceDefinitionRequest);

        // Producer statement to emit service definition to kafka for persisting
        producer.push(config.getServiceDefinitionCreateTopic(), serviceDefinitionRequest);

        // Restore attribute values to the type in which it was sent in service definition request
        enrichmentService.setAttributeDefinitionValuesBackToNativeState(serviceDefinition);

        return serviceDefinition;
    }

    public List<ServiceDefinition> searchServiceDefinition(ServiceDefinitionSearchRequest serviceDefinitionSearchRequest){

        List<ServiceDefinition> listOfServiceDefinitions = serviceDefinitionRequestRepository.getServiceDefinitions(serviceDefinitionSearchRequest);

        if(CollectionUtils.isEmpty(listOfServiceDefinitions))
            return new ArrayList<>();

        listOfServiceDefinitions.forEach(serviceDefinition -> {
            // Restore attribute values to native state
            enrichmentService.setAttributeDefinitionValuesBackToNativeState(serviceDefinition);
        });

        return listOfServiceDefinitions;
    }

    public ServiceDefinition updateServiceDefinition(ServiceDefinitionRequest serviceDefinitionRequest) {

        ServiceDefinition serviceDefinition = serviceDefinitionRequest.getServiceDefinition();

        //Validate incoming service definition request
        List<ServiceDefinition> serviceDefinitionList = serviceDefinitionRequestValidator.validateUpdateRequest(serviceDefinitionRequest);

        //Enrich incoming service definition request
        enrichmentService.enrichServiceDefinitionUpdateRequest(serviceDefinitionRequest, serviceDefinitionList);

        producer.push(config.getServiceDefinitionUpdateTopic(), serviceDefinitionRequest);

        enrichmentService.setAttributeDefinitionValuesBackToNativeState(serviceDefinition);

        return serviceDefinitionRequest.getServiceDefinition();
    }

}

