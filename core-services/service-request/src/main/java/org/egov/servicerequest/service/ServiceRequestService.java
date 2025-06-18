package org.egov.servicerequest.service;

import org.egov.servicerequest.config.Configuration;
import org.egov.servicerequest.kafka.Producer;
import org.egov.servicerequest.repository.ServiceRequestRepository;
import org.egov.servicerequest.validators.ServiceRequestValidator;
import org.egov.servicerequest.web.models.ServiceRequest;
import org.egov.servicerequest.web.models.ServiceSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ServiceRequestService {

    @Autowired
    private ServiceRequestValidator serviceRequestValidator;

    @Autowired
    private ServiceRequestEnrichmentService enrichmentService;

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

    @Autowired
    private Producer producer;

    @Autowired
    private Configuration config;

    public org.egov.servicerequest.web.models.Service createService(ServiceRequest serviceRequest) {

        org.egov.servicerequest.web.models.Service service = serviceRequest.getService();

        // Validate incoming service definition request
        serviceRequestValidator.validateServiceRequest(serviceRequest);

        // Enrich incoming service definition request
        Map<String, Object> attributeCodeVsValueMap = enrichmentService.enrichServiceRequest(serviceRequest);

        // Producer statement to emit service definition to kafka for persisting
        producer.push(config.getServiceCreateTopic(), serviceRequest);

        // Restore attribute values to the type in which it was sent in service request
        enrichmentService.setAttributeValuesBackToNativeState(serviceRequest, attributeCodeVsValueMap);

        return service;
    }

    public List<org.egov.servicerequest.web.models.Service> searchService(ServiceSearchRequest serviceSearchRequest){

        List<org.egov.servicerequest.web.models.Service> listOfServices = serviceRequestRepository.getService(serviceSearchRequest);

        if(CollectionUtils.isEmpty(listOfServices))
            return new ArrayList<>();

        return listOfServices;
    }

    public org.egov.servicerequest.web.models.Service updateService(ServiceRequest serviceRequest) {

        org.egov.servicerequest.web.models.Service service = serviceRequest.getService();

        // Validate incoming service definition request
        org.egov.servicerequest.web.models.Service existingService = serviceRequestValidator.validateServiceUpdateRequest(serviceRequest);

        // Enrich incoming service definition request
        Map<String, Object> attributeCodeVsValueMap = enrichmentService.enrichServiceRequestUpdate(serviceRequest, existingService);

        // Producer statement to emit service definition to kafka for persisting
        producer.push(config.getServiceUpdateTopic(), serviceRequest);

        // Restore attribute values to the type in which it was sent in service request
        enrichmentService.setAttributeValuesBackToNativeState(serviceRequest, attributeCodeVsValueMap);

        return service;
    }

}
