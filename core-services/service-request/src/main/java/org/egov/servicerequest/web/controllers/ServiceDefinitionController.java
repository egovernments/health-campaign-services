package org.egov.servicerequest.web.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.servicerequest.service.ServiceDefinitionRequestService;
import org.egov.servicerequest.util.ResponseInfoFactory;
import org.egov.servicerequest.web.models.ServiceDefinition;
import org.egov.servicerequest.web.models.ServiceDefinitionRequest;
import org.egov.servicerequest.web.models.ServiceDefinitionResponse;
import org.egov.servicerequest.web.models.ServiceDefinitionSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collections;
import java.util.List;

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
        ServiceDefinition serviceDefinition = serviceDefinitionRequestService.createServiceDefinition(serviceDefinitionRequest);
        ResponseInfo responseInfo = responseInfoFactory.createResponseInfoFromRequestInfo(serviceDefinitionRequest.getRequestInfo(), true);
        ServiceDefinitionResponse response = ServiceDefinitionResponse.builder().serviceDefinition(Collections.singletonList(serviceDefinition)).responseInfo(responseInfo).build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value="/definition/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<ServiceDefinitionResponse> search(@Valid @RequestBody ServiceDefinitionSearchRequest serviceDefinitionSearchRequest) {
        List<ServiceDefinition> serviceDefinitionList = serviceDefinitionRequestService.searchServiceDefinition(serviceDefinitionSearchRequest);
        ServiceDefinitionResponse response  = ServiceDefinitionResponse.builder().serviceDefinition(serviceDefinitionList).build();
        return new ResponseEntity<>(response,HttpStatus.OK);
    }

    @RequestMapping(value="/definition/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ServiceDefinitionResponse> update(@RequestBody @Valid ServiceDefinitionRequest serviceDefinitionRequest){
        ServiceDefinition serviceDefinition = serviceDefinitionRequestService.updateServiceDefinition(serviceDefinitionRequest);
        ResponseInfo responseInfo = responseInfoFactory.createResponseInfoFromRequestInfo(serviceDefinitionRequest.getRequestInfo(), true);
        ServiceDefinitionResponse response = ServiceDefinitionResponse.builder().serviceDefinition(Collections.singletonList(serviceDefinition)).responseInfo(responseInfo).build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}