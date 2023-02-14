package org.egov.stock.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.producer.Producer;
import org.egov.stock.config.StockConfiguration;
import org.egov.stock.service.StockReconciliationService;
import org.egov.stock.web.models.StockReconciliationBulkRequest;
import org.egov.stock.web.models.StockReconciliationRequest;
import org.egov.stock.web.models.StockReconciliationResponse;
import org.egov.stock.web.models.StockReconciliationSearchRequest;
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

@Controller
@RequestMapping("")
public class StockReconciliationApiController {
    private final ObjectMapper objectMapper;

    private final HttpServletRequest httpServletRequest;

    private final StockReconciliationService stockReconciliationService;

    private final Producer producer;

    private final StockConfiguration stockConfiguration;

    public StockReconciliationApiController(ObjectMapper objectMapper, HttpServletRequest httpServletRequest,
                                            StockReconciliationService stockReconciliationService, Producer producer,
                                            StockConfiguration stockConfiguration) {
        this.objectMapper = objectMapper;
        this.httpServletRequest = httpServletRequest;
        this.stockReconciliationService = stockReconciliationService;
        this.producer = producer;
        this.stockConfiguration = stockConfiguration;
    }

    @RequestMapping(value = "/reconciliation/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<StockReconciliationResponse> stockReconciliationV1CreatePost(@ApiParam(value = "Capture details of stock transaction.", required = true) @Valid @RequestBody StockReconciliationRequest request) {

        return new ResponseEntity<StockReconciliationResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/reconciliation/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> stockReconciliationV1CreatePost(@ApiParam(value = "Capture details of stock transaction.", required = true) @Valid @RequestBody StockReconciliationBulkRequest request) {

        return new ResponseEntity(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/reconciliation/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<StockReconciliationResponse> stockReconciliationV1SearchPost(@ApiParam(value = "Capture details of Stock Reconciliation.", required = true) @Valid @RequestBody StockReconciliationSearchRequest stock, @NotNull
    @Min(0)
    @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit, @NotNull
                                                                                       @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset, @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId, @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince, @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) {

        return new ResponseEntity<StockReconciliationResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/reconciliation/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<StockReconciliationResponse> stockReconciliationV1UpdatePost(@ApiParam(value = "Capture details of stock transaction", required = true) @Valid @RequestBody StockReconciliationRequest request) {
        return new ResponseEntity<StockReconciliationResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/reconciliation/v1/bulk/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> stockReconciliationV1UpdatePost(@ApiParam(value = "Capture details of stock transaction", required = true) @Valid @RequestBody StockReconciliationBulkRequest request) {
        return new ResponseEntity(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/reconciliation/v1/_delete", method = RequestMethod.POST)
    public ResponseEntity<StockReconciliationResponse> stockReconciliationV1DeletePost(@ApiParam(value = "Capture details of stock transaction", required = true) @Valid @RequestBody StockReconciliationRequest request) {
        return new ResponseEntity(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/reconciliation/v1/bulk/_delete", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> stockReconciliationV1DeletePost(@ApiParam(value = "Capture details of stock transaction", required = true) @Valid @RequestBody StockReconciliationBulkRequest request) {
        return new ResponseEntity(HttpStatus.NOT_IMPLEMENTED);
    }
}
