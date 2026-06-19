package org.egov.stock.web.controllers;


import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.models.stock.StockBulkResponse;
import org.egov.common.models.stock.StockRequest;
import org.egov.common.models.stock.StockResponse;
import org.egov.common.models.stock.StockSearchRequest;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.stock.config.StockConfiguration;
import org.egov.stock.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;



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
    public ResponseEntity<StockBulkResponse> stockV1SearchPost(
            @Valid @ModelAttribute URLParams urlParams,
            @ApiParam(value = "Capture details of Stock Transfer.", required = true) @Valid @RequestBody StockSearchRequest stockSearchRequest
    ) throws Exception {

        SearchResponse<Stock> searchResponse = stockService.search(
                stockSearchRequest,
                urlParams.getLimit(),
                urlParams.getOffset(),
                urlParams.getTenantId(),
                urlParams.getLastChangedSince(),
                urlParams.getIncludeDeleted()
        );
        StockBulkResponse response = StockBulkResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(stockSearchRequest.getRequestInfo(), true)).stock(searchResponse.getResponse()).totalCount(searchResponse.getTotalCount()).build();

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
