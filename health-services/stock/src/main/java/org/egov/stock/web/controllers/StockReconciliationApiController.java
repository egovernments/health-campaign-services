package org.egov.stock.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.stock.*;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.stock.config.StockReconciliationConfiguration;
import org.egov.stock.service.StockReconciliationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;

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
    public ResponseEntity<StockReconciliationBulkResponse> stockReconciliationV1SearchPost(@ApiParam(value = "Capture details of Stock Reconciliation.", required = true) @Valid @RequestBody StockReconciliationSearchRequest request, @NotNull
    @Min(0)
    @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit, @NotNull
                                                                                       @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset, @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId, @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince, @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) throws Exception {

        List<StockReconciliation> stock = stockReconciliationService.search(request, limit, offset, tenantId, lastChangedSince, includeDeleted);
        StockReconciliationBulkResponse response = StockReconciliationBulkResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true)).stockReconciliation(stock).build();

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
