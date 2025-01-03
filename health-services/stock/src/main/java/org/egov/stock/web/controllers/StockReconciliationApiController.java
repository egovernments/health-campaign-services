package org.egov.stock.web.controllers;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.common.models.stock.StockReconciliationBulkResponse;
import org.egov.common.models.stock.StockReconciliationRequest;
import org.egov.common.models.stock.StockReconciliationResponse;
import org.egov.common.models.stock.StockReconciliationSearchRequest;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.stock.config.StockReconciliationConfiguration;
import org.egov.stock.service.StockReconciliationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("")
public class StockReconciliationApiController {
    private final ObjectMapper objectMapper;

    private final HttpServletRequest httpServletRequest;

    private final StockReconciliationService stockReconciliationService;

    private final Producer producer;

    private final StockReconciliationConfiguration configuration;

    public StockReconciliationApiController(ObjectMapper objectMapper, HttpServletRequest httpServletRequest,
                                            StockReconciliationService stockReconciliationService, Producer producer,
                                            StockReconciliationConfiguration configuration) {
        this.objectMapper = objectMapper;
        this.httpServletRequest = httpServletRequest;
        this.stockReconciliationService = stockReconciliationService;
        this.producer = producer;
        this.configuration = configuration;
    }

    @RequestMapping(value = "/reconciliation/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<StockReconciliationResponse> stockReconciliationV1CreatePost(@ApiParam(value = "Capture details of stock transaction.", required = true) @RequestBody StockReconciliationRequest request) {
        StockReconciliation stock = stockReconciliationService.create(request);
        StockReconciliationResponse response = StockReconciliationResponse.builder()
                .stockReconciliation(stock)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.accepted().body(response);
    }

    @RequestMapping(value = "/reconciliation/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> stockReconciliationV1CreatePost(@ApiParam(value = "Capture details of stock transaction.", required = true) @Valid @RequestBody StockReconciliationBulkRequest request) {

        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(configuration.getBulkCreateStockReconciliationTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/reconciliation/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<StockReconciliationBulkResponse> stockReconciliationV1SearchPost(
            @Valid @ModelAttribute URLParams urlParams,
            @ApiParam(value = "Capture details of Stock Reconciliation.", required = true) @Valid @RequestBody StockReconciliationSearchRequest stockReconciliationSearchRequest
    ) throws Exception {

        List<StockReconciliation> stock = stockReconciliationService.search(
                stockReconciliationSearchRequest,
                urlParams.getLimit(),
                urlParams.getOffset(),
                urlParams.getTenantId(),
                urlParams.getLastChangedSince(),
                urlParams.getIncludeDeleted()
        );
        StockReconciliationBulkResponse response = StockReconciliationBulkResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(stockReconciliationSearchRequest.getRequestInfo(), true)).stockReconciliation(stock).build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/reconciliation/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<StockReconciliationResponse> stockReconciliationV1UpdatePost(@ApiParam(value = "Capture details of stock transaction", required = true) @Valid @RequestBody StockReconciliationRequest request) {
        StockReconciliation stock = stockReconciliationService.update(request);
        StockReconciliationResponse response = StockReconciliationResponse.builder()
                .stockReconciliation(stock)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.accepted().body(response);
    }

    @RequestMapping(value = "/reconciliation/v1/bulk/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> stockReconciliationV1UpdatePost(@ApiParam(value = "Capture details of stock transaction", required = true) @Valid @RequestBody StockReconciliationBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(configuration.getBulkUpdateStockReconciliationTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/reconciliation/v1/_delete", method = RequestMethod.POST)
    public ResponseEntity<StockReconciliationResponse> stockReconciliationV1DeletePost(@ApiParam(value = "Capture details of stock transaction", required = true) @Valid @RequestBody StockReconciliationRequest request) {
        StockReconciliation stock = stockReconciliationService.delete(request);
        StockReconciliationResponse response = StockReconciliationResponse.builder()
                .stockReconciliation(stock)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.accepted().body(response);
    }

    @RequestMapping(value = "/reconciliation/v1/bulk/_delete", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> stockReconciliationV1DeletePost(@ApiParam(value = "Capture details of stock transaction", required = true) @Valid @RequestBody StockReconciliationBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(configuration.getBulkDeleteStockReconciliationTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }
}
