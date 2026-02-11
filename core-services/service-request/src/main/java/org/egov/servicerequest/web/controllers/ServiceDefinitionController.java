package org.egov.servicerequest.web.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.servicerequest.service.ServiceDefinitionRequestService;
import org.egov.servicerequest.util.ResponseInfoFactory;
import org.egov.servicerequest.web.models.ServiceDefinition;
import org.egov.servicerequest.web.models.ServiceDefinitionRequest;
import org.egov.servicerequest.web.models.ServiceDefinitionResponse;
import org.egov.servicerequest.web.models.ServiceDefinitionSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.Collections;
import java.util.List;

import static org.egov.servicerequest.error.ErrorCode.INVALID_TENANT_ID_ERR_CODE;

@Slf4j
@RestController
@RequestMapping("/service")
public class ServiceDefinitionController {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResponseInfoFactory responseInfoFactory;

    @Autowired
    private ServiceDefinitionRequestService serviceDefinitionRequestService;

    @RequestMapping(value="/definition/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ServiceDefinitionResponse> create(@RequestBody @Valid ServiceDefinitionRequest serviceDefinitionRequest) {
        ServiceDefinition serviceDefinition = null;
        try {
            serviceDefinition = serviceDefinitionRequestService.createServiceDefinition(serviceDefinitionRequest);
        } catch (InvalidTenantIdException e) {
            // building and throwing CustomException for InvalidTenantIdException
            throw new CustomException(INVALID_TENANT_ID_ERR_CODE, e.getMessage());
        }
        ResponseInfo responseInfo = responseInfoFactory.createResponseInfoFromRequestInfo(serviceDefinitionRequest.getRequestInfo(), true);
        ServiceDefinitionResponse response = ServiceDefinitionResponse.builder().serviceDefinition(Collections.singletonList(serviceDefinition)).responseInfo(responseInfo).build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value="/definition/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<ServiceDefinitionResponse> search(@Valid @RequestBody ServiceDefinitionSearchRequest serviceDefinitionSearchRequest) {
        List<ServiceDefinition> serviceDefinitionList;
        try {
            serviceDefinitionList = serviceDefinitionRequestService.searchServiceDefinition(serviceDefinitionSearchRequest);
        } catch (InvalidTenantIdException e) {
            // building and throwing CustomException for InvalidTenantIdException
            throw new CustomException(INVALID_TENANT_ID_ERR_CODE, e.getMessage());
        }
        ServiceDefinitionResponse response  = ServiceDefinitionResponse.builder().serviceDefinition(serviceDefinitionList).build();
        return new ResponseEntity<>(response,HttpStatus.OK);
    }

    @RequestMapping(value="/definition/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ServiceDefinitionResponse> update(@RequestBody @Valid ServiceDefinitionRequest serviceDefinitionRequest){
        ServiceDefinition serviceDefinition;
        try {
            serviceDefinition = serviceDefinitionRequestService.updateServiceDefinition(serviceDefinitionRequest);
        } catch (InvalidTenantIdException e) {
            // building and throwing CustomException for InvalidTenantIdException
            throw new CustomException(INVALID_TENANT_ID_ERR_CODE, e.getMessage());
        }
        ResponseInfo responseInfo = responseInfoFactory.createResponseInfoFromRequestInfo(serviceDefinitionRequest.getRequestInfo(), true);
        ServiceDefinitionResponse response = ServiceDefinitionResponse.builder().serviceDefinition(Collections.singletonList(serviceDefinition)).responseInfo(responseInfo).build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}