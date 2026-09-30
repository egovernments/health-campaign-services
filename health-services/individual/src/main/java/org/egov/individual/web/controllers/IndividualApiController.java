package org.egov.individual.web.controllers;


import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.models.individual.IndividualBulkResponse;
import org.egov.common.models.individual.IndividualRequest;
import org.egov.common.models.individual.IndividualResponse;
import org.egov.common.models.individual.IndividualSearchRequest;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.service.IndividualService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
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

    @RequestMapping(value = "/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<?> individualV1BulkCreatePost(
            @ApiParam(value = "Capture details of Individual.", required = true) @Valid @RequestBody IndividualBulkRequest request,
            @ApiParam(value = "Client can specify if the resource in request body needs to be sent back in the response. This is being used to limit amount of data that needs to flow back from the server to the client in low bandwidth scenarios. Server will always send the server generated id for validated requests.", defaultValue = "true") @Valid @RequestParam(value = "echoResource", required = false, defaultValue = "true") Boolean echoResource,
            @ApiParam(value = "When true, process the whole bulk create inline and return the fully-enriched individuals (including server-generated ids and user-service ids) in the response body. When false or omitted, the request is queued on Kafka and the response contains only the ResponseInfo (existing async behaviour).", defaultValue = "false") @Valid @RequestParam(value = "synchronous", required = false, defaultValue = "false") Boolean synchronous) {
        request.getRequestInfo().setApiId(servletRequest.getRequestURI());

        if (Boolean.TRUE.equals(synchronous)) {
            // Inline processing — same code path the Kafka consumer uses.
            // Returns individuals populated with server-generated id / userId /
            // userUuid AND a rich per-record errors array so downstream
            // orchestrators can chain without polling and see WHY any record
            // failed with the same code/message emitted by the downstream
            // service (egov-user), enriched with individual-level identifiers.
            java.util.Map<org.egov.common.models.individual.Individual,
                    org.egov.common.models.ErrorDetails> errorDetailsMap = new java.util.HashMap<>();
            List<Individual> individuals =
                    individualService.create(request, true, true, errorDetailsMap);
            java.util.List<java.util.Map<String, Object>> errorPayload = new java.util.ArrayList<>();
            for (java.util.Map.Entry<org.egov.common.models.individual.Individual,
                    org.egov.common.models.ErrorDetails> entry : errorDetailsMap.entrySet()) {
                org.egov.common.models.individual.Individual ind = entry.getKey();
                org.egov.common.models.ErrorDetails details = entry.getValue();
                if (details == null || details.getErrors() == null) continue;
                for (org.egov.common.models.Error err : details.getErrors()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("clientReferenceId", ind.getClientReferenceId());
                    row.put("individualId", ind.getId());
                    row.put("username", ind.getUserDetails() != null ? ind.getUserDetails().getUsername() : null);
                    row.put("mobileNumber", ind.getMobileNumber());
                    row.put("errorCode", err.getErrorCode());
                    row.put("errorMessage", err.getErrorMessage());
                    errorPayload.add(row);
                }
            }
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("ResponseInfo",
                    ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), errorPayload.isEmpty()));
            body.put("TotalCount", (long) individuals.size());
            body.put("Individual", individuals);
            body.put("Errors", errorPayload);
            return ResponseEntity.status(HttpStatus.OK).body(body);
        }

        // Existing async path — unchanged.
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
        SearchResponse<Individual> searchResponse  = individualService.search(
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
