package org.egov.stock.web.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.models.stock.StockBulkResponse;
import org.egov.common.models.stock.StockRequest;
import org.egov.common.models.stock.StockResponse;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.stock.config.StockConfiguration;
import org.egov.stock.service.StockService;
import org.egov.stock.web.models.StockSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
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

@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-08T11:49:06.320+05:30")

@Controller
@RequestMapping("")
public class StockApiController {

    private final ObjectMapper objectMapper;

    private final HttpServletRequest httpServletRequest;

    private final StockService stockService;

    private final Producer producer;

    private final StockConfiguration stockConfiguration;

    @Autowired
    public StockApiController(ObjectMapper objectMapper, HttpServletRequest request, StockService stockService, Producer producer, StockConfiguration stockConfiguration) {
        this.objectMapper = objectMapper;
        this.httpServletRequest = request;
        this.stockService = stockService;
        this.producer = producer;
        this.stockConfiguration = stockConfiguration;
    }

    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<StockResponse> stockV1CreatePost(@ApiParam(value = "Capture details of stock transaction.", required = true) @Valid @RequestBody StockRequest request) {
        Stock stock = stockService.create(request);
        StockResponse response = StockResponse.builder()
                .stock(stock)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.accepted().body(response);
    }

    @RequestMapping(value = "/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> stockV1CreatePost(@ApiParam(value = "Capture details of stock transaction.", required = true) @Valid @RequestBody StockBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(stockConfiguration.getBulkCreateStockTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<StockBulkResponse> stockV1SearchPost(@ApiParam(value = "Capture details of Stock Transfer.", required = true) @Valid @RequestBody StockSearchRequest request, @NotNull
    @Min(0) @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit, @NotNull
                                                           @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset, @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId, @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince, @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) throws Exception {

        List<Stock> stock = stockService.search(request, limit, offset, tenantId, lastChangedSince, includeDeleted);
        StockBulkResponse response = StockBulkResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true)).stock(stock).build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<StockResponse> stockV1UpdatePost(@ApiParam(value = "Capture details of stock transaction", required = true) @Valid @RequestBody StockRequest request) {
        Stock stock = stockService.update(request);
        StockResponse response = StockResponse.builder()
                .stock(stock)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.accepted().body(response);
    }

    @RequestMapping(value = "/v1/bulk/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> stockV1UpdatePost(@ApiParam(value = "Capture details of stock transaction.", required = true) @Valid @RequestBody StockBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(stockConfiguration.getBulkUpdateStockTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/v1/_delete", method = RequestMethod.POST)
    public ResponseEntity<StockResponse> stockV1DeletePost(@ApiParam(value = "Capture details of stock transaction", required = true) @Valid @RequestBody StockRequest request) {
        Stock stock = stockService.delete(request);
        StockResponse response = StockResponse.builder()
                .stock(stock)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.accepted().body(response);
    }

    @RequestMapping(value = "/v1/bulk/_delete", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> stockV1DeletePost(@ApiParam(value = "Capture details of stock transaction.", required = true) @Valid @RequestBody StockBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(stockConfiguration.getBulkDeleteStockTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

}
