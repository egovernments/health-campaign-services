package org.egov.stock.web.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.egov.stock.web.models.StockReconciliationRequest;
import org.egov.stock.web.models.StockReconciliationResponse;
import org.egov.stock.web.models.StockReconciliationSearchRequest;
import org.egov.stock.web.models.StockRequest;
import org.egov.stock.web.models.StockResponse;
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
import java.io.IOException;
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-08T11:49:06.320+05:30")

@Controller
    @RequestMapping("")
    public class StockApiController{

        private final ObjectMapper objectMapper;

        private final HttpServletRequest request;

        @Autowired
        public StockApiController(ObjectMapper objectMapper, HttpServletRequest request) {
        this.objectMapper = objectMapper;
        this.request = request;
        }

                @RequestMapping(value="/stock/reconciliation/v1/_create", method = RequestMethod.POST)
                public ResponseEntity<StockReconciliationResponse> stockReconciliationV1CreatePost(@ApiParam(value = "Capture details of stock transaction." ,required=true )  @Valid @RequestBody StockReconciliationRequest stockReconciliation) {
                        String accept = request.getHeader("Accept");
                            if (accept != null && accept.contains("application/json")) {
                            try {
                            return new ResponseEntity<StockReconciliationResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"StockReconciliation\" : [ {    \"calculatedCount\" : { },    \"facilityId\" : \"FacilityA\",    \"productVariantId\" : { },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"clientReferenceId\" : { },    \"referenceId\" : \"C-1\",    \"commentsOnReconciliation\" : \"commentsOnReconciliation\",    \"isDeleted\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"referenceIdType\" : \"PROJECT\",    \"physicalCount\" : { },    \"eventTimestamp\" : \"1663218161\"  }, {    \"calculatedCount\" : { },    \"facilityId\" : \"FacilityA\",    \"productVariantId\" : { },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"clientReferenceId\" : { },    \"referenceId\" : \"C-1\",    \"commentsOnReconciliation\" : \"commentsOnReconciliation\",    \"isDeleted\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"referenceIdType\" : \"PROJECT\",    \"physicalCount\" : { },    \"eventTimestamp\" : \"1663218161\"  } ]}", StockReconciliationResponse.class), HttpStatus.NOT_IMPLEMENTED);
                            } catch (IOException e) {
                            return new ResponseEntity<StockReconciliationResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
                            }
                            }

                        return new ResponseEntity<StockReconciliationResponse>(HttpStatus.NOT_IMPLEMENTED);
                }

                @RequestMapping(value="/stock/reconciliation/v1/_search", method = RequestMethod.POST)
                public ResponseEntity<StockReconciliationResponse> stockReconciliationV1SearchPost(@ApiParam(value = "Capture details of Stock Reconciliation." ,required=true )  @Valid @RequestBody StockReconciliationSearchRequest stock,@NotNull 
    @Min(0)
    @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit,@NotNull 
    @Min(0)@ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset,@NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId,@ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince,@ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue="false") Boolean includeDeleted) {
                        String accept = request.getHeader("Accept");
                            if (accept != null && accept.contains("application/json")) {
                            try {
                            return new ResponseEntity<StockReconciliationResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"StockReconciliation\" : [ {    \"calculatedCount\" : { },    \"facilityId\" : \"FacilityA\",    \"productVariantId\" : { },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"clientReferenceId\" : { },    \"referenceId\" : \"C-1\",    \"commentsOnReconciliation\" : \"commentsOnReconciliation\",    \"isDeleted\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"referenceIdType\" : \"PROJECT\",    \"physicalCount\" : { },    \"eventTimestamp\" : \"1663218161\"  }, {    \"calculatedCount\" : { },    \"facilityId\" : \"FacilityA\",    \"productVariantId\" : { },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"clientReferenceId\" : { },    \"referenceId\" : \"C-1\",    \"commentsOnReconciliation\" : \"commentsOnReconciliation\",    \"isDeleted\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"referenceIdType\" : \"PROJECT\",    \"physicalCount\" : { },    \"eventTimestamp\" : \"1663218161\"  } ]}", StockReconciliationResponse.class), HttpStatus.NOT_IMPLEMENTED);
                            } catch (IOException e) {
                            return new ResponseEntity<StockReconciliationResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
                            }
                            }

                        return new ResponseEntity<StockReconciliationResponse>(HttpStatus.NOT_IMPLEMENTED);
                }

                @RequestMapping(value="/stock/reconciliation/v1/_update", method = RequestMethod.POST)
                public ResponseEntity<StockReconciliationResponse> stockReconciliationV1UpdatePost(@ApiParam(value = "Capture details of stock transaction" ,required=true )  @Valid @RequestBody StockReconciliationRequest stockReconciliation) {
                        String accept = request.getHeader("Accept");
                            if (accept != null && accept.contains("application/json")) {
                            try {
                            return new ResponseEntity<StockReconciliationResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"StockReconciliation\" : [ {    \"calculatedCount\" : { },    \"facilityId\" : \"FacilityA\",    \"productVariantId\" : { },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"clientReferenceId\" : { },    \"referenceId\" : \"C-1\",    \"commentsOnReconciliation\" : \"commentsOnReconciliation\",    \"isDeleted\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"referenceIdType\" : \"PROJECT\",    \"physicalCount\" : { },    \"eventTimestamp\" : \"1663218161\"  }, {    \"calculatedCount\" : { },    \"facilityId\" : \"FacilityA\",    \"productVariantId\" : { },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"clientReferenceId\" : { },    \"referenceId\" : \"C-1\",    \"commentsOnReconciliation\" : \"commentsOnReconciliation\",    \"isDeleted\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"referenceIdType\" : \"PROJECT\",    \"physicalCount\" : { },    \"eventTimestamp\" : \"1663218161\"  } ]}", StockReconciliationResponse.class), HttpStatus.NOT_IMPLEMENTED);
                            } catch (IOException e) {
                            return new ResponseEntity<StockReconciliationResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
                            }
                            }

                        return new ResponseEntity<StockReconciliationResponse>(HttpStatus.NOT_IMPLEMENTED);
                }

                @RequestMapping(value="/stock/v1/_create", method = RequestMethod.POST)
                public ResponseEntity<StockResponse> stockV1CreatePost(@ApiParam(value = "Capture details of stock transaction." ,required=true )  @Valid @RequestBody StockRequest stock) {
                        String accept = request.getHeader("Accept");
                            if (accept != null && accept.contains("application/json")) {
                            try {
                            return new ResponseEntity<StockResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"Stock\" : [ {    \"facilityId\" : \"FacilityA\",    \"quantity\" : { },    \"productVariantId\" : { },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"clientReferenceId\" : { },    \"referenceId\" : \"C-1\",    \"transactionReason\" : { },    \"transactionType\" : { },    \"isDeleted\" : { },    \"transactingPartyId\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"transactingPartyType\" : \"WAREHOUSE\",    \"referenceIdType\" : \"PROJECT\"  }, {    \"facilityId\" : \"FacilityA\",    \"quantity\" : { },    \"productVariantId\" : { },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"clientReferenceId\" : { },    \"referenceId\" : \"C-1\",    \"transactionReason\" : { },    \"transactionType\" : { },    \"isDeleted\" : { },    \"transactingPartyId\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"transactingPartyType\" : \"WAREHOUSE\",    \"referenceIdType\" : \"PROJECT\"  } ]}", StockResponse.class), HttpStatus.NOT_IMPLEMENTED);
                            } catch (IOException e) {
                            return new ResponseEntity<StockResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
                            }
                            }

                        return new ResponseEntity<StockResponse>(HttpStatus.NOT_IMPLEMENTED);
                }

                @RequestMapping(value="/stock/v1/_search", method = RequestMethod.POST)
                public ResponseEntity<StockResponse> stockV1SearchPost(@ApiParam(value = "Capture details of Stock Transfer." ,required=true )  @Valid @RequestBody StockSearchRequest stock,@NotNull 
    @Min(0)
    @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit,@NotNull 
    @Min(0)@ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset,@NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId,@ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince,@ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue="false") Boolean includeDeleted) {
                        String accept = request.getHeader("Accept");
                            if (accept != null && accept.contains("application/json")) {
                            try {
                            return new ResponseEntity<StockResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"Stock\" : [ {    \"facilityId\" : \"FacilityA\",    \"quantity\" : { },    \"productVariantId\" : { },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"clientReferenceId\" : { },    \"referenceId\" : \"C-1\",    \"transactionReason\" : { },    \"transactionType\" : { },    \"isDeleted\" : { },    \"transactingPartyId\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"transactingPartyType\" : \"WAREHOUSE\",    \"referenceIdType\" : \"PROJECT\"  }, {    \"facilityId\" : \"FacilityA\",    \"quantity\" : { },    \"productVariantId\" : { },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"clientReferenceId\" : { },    \"referenceId\" : \"C-1\",    \"transactionReason\" : { },    \"transactionType\" : { },    \"isDeleted\" : { },    \"transactingPartyId\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"transactingPartyType\" : \"WAREHOUSE\",    \"referenceIdType\" : \"PROJECT\"  } ]}", StockResponse.class), HttpStatus.NOT_IMPLEMENTED);
                            } catch (IOException e) {
                            return new ResponseEntity<StockResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
                            }
                            }

                        return new ResponseEntity<StockResponse>(HttpStatus.NOT_IMPLEMENTED);
                }

                @RequestMapping(value="/stock/v1/_update", method = RequestMethod.POST)
                public ResponseEntity<StockResponse> stockV1UpdatePost(@ApiParam(value = "Capture details of stock transaction" ,required=true )  @Valid @RequestBody StockRequest stock) {
                        String accept = request.getHeader("Accept");
                            if (accept != null && accept.contains("application/json")) {
                            try {
                            return new ResponseEntity<StockResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"Stock\" : [ {    \"facilityId\" : \"FacilityA\",    \"quantity\" : { },    \"productVariantId\" : { },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"clientReferenceId\" : { },    \"referenceId\" : \"C-1\",    \"transactionReason\" : { },    \"transactionType\" : { },    \"isDeleted\" : { },    \"transactingPartyId\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"transactingPartyType\" : \"WAREHOUSE\",    \"referenceIdType\" : \"PROJECT\"  }, {    \"facilityId\" : \"FacilityA\",    \"quantity\" : { },    \"productVariantId\" : { },    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"rowVersion\" : { },    \"clientReferenceId\" : { },    \"referenceId\" : \"C-1\",    \"transactionReason\" : { },    \"transactionType\" : { },    \"isDeleted\" : { },    \"transactingPartyId\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"transactingPartyType\" : \"WAREHOUSE\",    \"referenceIdType\" : \"PROJECT\"  } ]}", StockResponse.class), HttpStatus.NOT_IMPLEMENTED);
                            } catch (IOException e) {
                            return new ResponseEntity<StockResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
                            }
                            }

                        return new ResponseEntity<StockResponse>(HttpStatus.NOT_IMPLEMENTED);
                }

        }
