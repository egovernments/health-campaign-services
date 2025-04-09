package org.egov.individual.web.controllers;


import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.individual.*;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.service.IndividualService;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
@Validated
public class IndividualApiController {

    private final IndividualService individualService;

    private final ObjectMapper objectMapper;

    private final HttpServletRequest servletRequest;

    private final Producer producer;

    private final IndividualProperties individualProperties;

    @Autowired
    public IndividualApiController(IndividualService individualService,
                                   ObjectMapper objectMapper,
                                   HttpServletRequest servletRequest, Producer producer,
                                   IndividualProperties individualProperties) {
        this.individualService = individualService;
        this.objectMapper = objectMapper;
        this.servletRequest = servletRequest;
        this.producer = producer;
        this.individualProperties = individualProperties;
    }

    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<IndividualResponse> individualV1CreatePost(@ApiParam(value = "Capture details of Individual.", required = true) @Valid @RequestBody IndividualRequest request, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {

        List<Individual> individuals = individualService.create(request);
        IndividualResponse response = IndividualResponse.builder()
                .individual(individuals.get(0))
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

    }

    @RequestMapping(value = "/v1/_mapIndividualToUser", method = RequestMethod.POST)
    public ResponseEntity<IndividualResponse> mapIndividualToUser(@RequestBody IndividualRequest individualRequest) {

        List<Individual> individuals = individualService.mapIndividualToUser(individualRequest.getIndividual().getTenantId(),individualRequest.getIndividual().getId(), individualRequest.getIndividual().getUserId(), individualRequest.getRequestInfo());
        if(CollectionUtils.isEmpty(individuals)){
            throw new CustomException("ID_NOT_FOUND","Individual Cannot be found with this id");
        }
        individuals.get(0).setUserUuid(individualRequest.getIndividual().getUserUuid()); // check which one to update
        individuals.get(0).setUserId(individualRequest.getIndividual().getUserId()); // check which one to update
//        individuals.get(0).setClientReferenceId(individuals.get(0).getIdentifiers().get(0).getClientReferenceId());
        individualRequest.setIndividual(individuals.get(0));
        return individualV1UpdatePost(individualRequest, Boolean.TRUE);
    }

    @RequestMapping(value = "/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> individualV1BulkCreatePost(@ApiParam(value = "Capture details of Individual.", required = true) @Valid @RequestBody IndividualBulkRequest request, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        request.getRequestInfo().setApiId(servletRequest.getRequestURI());
        individualService.putInCache(request.getIndividuals());
        producer.push(individualProperties.getBulkSaveIndividualTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<IndividualBulkResponse> individualV1SearchPost(
            @Valid @ModelAttribute URLParams urlParams,
            @ApiParam(value = "Individual details.", required = true) @Valid @RequestBody IndividualSearchRequest request
    ) {
        SearchResponse<Individual> searchResponse = individualService.search(
                request.getIndividual(),
                urlParams.getLimit(),
                urlParams.getOffset(),
                urlParams.getTenantId(),
                urlParams.getLastChangedSince(),
                urlParams.getIncludeDeleted(),
                request.getRequestInfo()
        );
        IndividualBulkResponse response = IndividualBulkResponse.builder()
                .individual(searchResponse.getResponse())
                .totalCount(searchResponse.getTotalCount())
                .responseInfo(ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), true))
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<IndividualResponse> individualV1UpdatePost(@ApiParam(value = "Details for the Individual.", required = true) @Valid @RequestBody IndividualRequest request, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        List<Individual> individuals = individualService.update(request);
        IndividualResponse response = IndividualResponse.builder()
                .individual(individuals.get(0))
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/v1/bulk/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> individualV1BulkUpdatePost(@ApiParam(value = "Details for the Individual.", required = true) @Valid @RequestBody IndividualBulkRequest request, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        request.getRequestInfo().setApiId(servletRequest.getRequestURI());
        producer.push(individualProperties.getBulkUpdateIndividualTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/v1/_delete", method = RequestMethod.POST)
    public ResponseEntity<IndividualResponse> individualV1DeletePost(@ApiParam(value = "Details for the Individual.", required = true) @Valid @RequestBody IndividualRequest request, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        List<Individual> individuals = individualService.delete(request);
        IndividualResponse response = IndividualResponse.builder()
                .individual(individuals.get(0))
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/v1/bulk/_delete", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> individualV1BulkDeletePost(@ApiParam(value = "Details for the Individual.", required = true) @Valid @RequestBody IndividualBulkRequest request, @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource) {
        request.getRequestInfo().setApiId(servletRequest.getRequestURI());
        producer.push(individualProperties.getBulkDeleteIndividualTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }
}
