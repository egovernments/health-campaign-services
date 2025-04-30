package org.egov.servicerequest.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.servicerequest.service.ServiceRequestService;
import org.egov.servicerequest.util.ResponseInfoFactory;
import org.egov.servicerequest.web.models.Service;
import org.egov.servicerequest.web.models.ServiceRequest;
import org.egov.servicerequest.web.models.ServiceResponse;
import org.egov.servicerequest.web.models.ServiceSearchRequest;
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
public class ServiceController {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResponseInfoFactory responseInfoFactory;

    @Autowired
    private ServiceRequestService serviceRequestService;

    @RequestMapping(value="/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ServiceResponse> create(@RequestBody @Valid ServiceRequest serviceRequest) {
        Service service = null;
        try {
            service = serviceRequestService.createService(serviceRequest);
        } catch (InvalidTenantIdException e) {
            throw new CustomException(INVALID_TENANT_ID_ERR_CODE, e.getMessage());
        }
        ResponseInfo responseInfo = responseInfoFactory.createResponseInfoFromRequestInfo(serviceRequest.getRequestInfo(), true);
        ServiceResponse response = ServiceResponse.builder().service(Collections.singletonList(service)).responseInfo(responseInfo).build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value="/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<ServiceResponse> search(@Valid @RequestBody ServiceSearchRequest serviceSearchRequest) {
        List<Service> serviceList = null;
        try {
            serviceList = serviceRequestService.searchService(serviceSearchRequest);
        } catch (InvalidTenantIdException e) {
            throw new CustomException(INVALID_TENANT_ID_ERR_CODE, e.getMessage());
        }
        ServiceResponse response  = ServiceResponse.builder().service(serviceList).build();
        return new ResponseEntity<>(response,HttpStatus.OK);
    }

    @RequestMapping(value="/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ServiceResponse> update(@RequestBody @Valid ServiceRequest serviceRequest){
        Service service = serviceRequestService.updateService(serviceRequest);
        ResponseInfo responseInfo = responseInfoFactory.createResponseInfoFromRequestInfo(serviceRequest.getRequestInfo(), true);
        ServiceResponse response = ServiceResponse.builder().service(Collections.singletonList(service)).responseInfo(responseInfo).build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}
