package org.egov.product.web.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.product.ProductApplication;
import org.egov.product.service.ProductService;
import org.egov.product.web.models.ApiOperation;
import org.egov.product.web.models.Product;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.product.service.ProductVariantService;
import org.egov.product.web.models.ApiOperation;
import org.egov.product.web.models.ProductRequest;
import org.egov.product.web.models.ProductResponse;
import org.egov.product.web.models.ProductSearchRequest;
import org.egov.product.web.models.ProductVariant;
import org.egov.product.web.models.ProductVariantRequest;
import org.egov.product.web.models.ProductVariantResponse;
import org.egov.product.web.models.ProductVariantSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
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
import java.util.List;

@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T16:45:24.641+05:30")

@Controller
public class ProductApiController {

    private final ObjectMapper objectMapper;

    private final HttpServletRequest request;

    private final ProductVariantService productVariantService;

    private final ProductService productService;

    @Autowired
    public ProductApiController(ObjectMapper objectMapper, HttpServletRequest request,
                                ProductVariantService productVariantService, ProductService productService) {
        this.objectMapper = objectMapper;
        this.request = request;
        this.productVariantService = productVariantService;
        this.productService = productService;
    }


    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ProductResponse> productV1CreatePost(@ApiParam(value = "Capture details of Product.", required = true) @Valid @RequestBody ProductRequest productRequest) throws Exception {
        if(productRequest.getApiOperation() != ApiOperation.CREATE && productRequest.getApiOperation() != null){
            throw new CustomException("INVALID_API_OPERATION", String.format("API Operation %s not valid for create request", productRequest.getApiOperation().toString()));
        }
        List<Product> products = productService.create(productRequest);
        ProductResponse productResponse = ProductResponse.builder()
                .product(products)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(productRequest.getRequestInfo(), true))
                .build();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(productResponse);
    }

    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<ProductResponse> productV1SearchPost(@ApiParam(value = "Capture details of Product.", required = true) @Valid @RequestBody ProductSearchRequest product, @NotNull
    @Min(0)
    @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit, @NotNull
                                                               @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset, @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId, @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince, @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<ProductResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"Product\" : [ {    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"name\" : \"Paracetamol\",    \"id\" : { },    \"type\" : \"DRUG\",    \"manufacturer\" : \"J&J\"  }, {    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"name\" : \"Paracetamol\",    \"id\" : { },    \"type\" : \"DRUG\",    \"manufacturer\" : \"J&J\"  } ]}", ProductResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<ProductResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<ProductResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ProductResponse> productV1UpdatePost(@ApiParam(value = "Capture details of Product.", required = true) @Valid @RequestBody ProductRequest product) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<ProductResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"Product\" : [ {    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"name\" : \"Paracetamol\",    \"id\" : { },    \"type\" : \"DRUG\",    \"manufacturer\" : \"J&J\"  }, {    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"name\" : \"Paracetamol\",    \"id\" : { },    \"type\" : \"DRUG\",    \"manufacturer\" : \"J&J\"  } ]}", ProductResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<ProductResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<ProductResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/variant/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ProductVariantResponse> productVariantV1CreatePost(@ApiParam(value = "Capture details of Product Variant.", required = true)
                                                                                 @Valid @RequestBody ProductVariantRequest request) throws Exception {
        if (request.getApiOperation() == null || request.getApiOperation().equals(ApiOperation.CREATE)) {
            List<ProductVariant> productVariants = productVariantService.create(request);
            ProductVariantResponse response = ProductVariantResponse.builder()
                    .productVariant(productVariants)
                    .responseInfo(ResponseInfoFactory
                            .createResponseInfo(request.getRequestInfo(), true))
                    .build();

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } else {
            throw new CustomException("INCORRECT_API_OPERATION", "Only null or CREATE apiOperation supported for create endpoint");
        }
    }

    @RequestMapping(value = "/variant/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<ProductVariantResponse> productVariantV1SearchPost(@ApiParam(value = "Capture details of Product variant.", required = true) @Valid @RequestBody ProductVariantSearchRequest productVariant, @NotNull
    @Min(0)
    @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit, @NotNull
                                                                             @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset, @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId, @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince, @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<ProductVariantResponse>(objectMapper.readValue("{  \"ResponseInfo\" : {    \"ver\" : \"ver\",    \"resMsgId\" : \"resMsgId\",    \"msgId\" : \"msgId\",    \"apiId\" : \"apiId\",    \"ts\" : 0,    \"status\" : \"SUCCESSFUL\"  },  \"ProductVariant\" : [ {    \"productId\" : \"productId\",    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"sku\" : \"PAR-200\",    \"variation\" : \"Paracetamol 200mg white color\"  }, {    \"productId\" : \"productId\",    \"additionalFields\" : {      \"schema\" : \"HOUSEHOLD\",      \"fields\" : [ {        \"value\" : \"180\",        \"key\" : \"height\"      }, {        \"value\" : \"180\",        \"key\" : \"height\"      } ],      \"version\" : 2    },    \"isDeleted\" : { },    \"rowVersion\" : { },    \"auditDetails\" : {      \"lastModifiedTime\" : 1,      \"createdBy\" : \"createdBy\",      \"lastModifiedBy\" : \"lastModifiedBy\",      \"createdTime\" : 6    },    \"tenantId\" : \"tenantA\",    \"id\" : { },    \"sku\" : \"PAR-200\",    \"variation\" : \"Paracetamol 200mg white color\"  } ]}", ProductVariantResponse.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                return new ResponseEntity<ProductVariantResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<ProductVariantResponse>(HttpStatus.NOT_IMPLEMENTED);
    }

    @RequestMapping(value = "/variant/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ProductVariantResponse> productVariantV1UpdatePost(@ApiParam(value = "Capture details of Product Variant.", required = true) @Valid @RequestBody ProductVariantRequest request) {
        if (request.getApiOperation().equals(ApiOperation.UPDATE)
                || request.getApiOperation().equals(ApiOperation.DELETE)) {
            List<ProductVariant> productVariants = productVariantService.update(request);
            ProductVariantResponse response = ProductVariantResponse.builder()
                    .productVariant(productVariants)
                    .responseInfo(ResponseInfoFactory
                            .createResponseInfo(request.getRequestInfo(), true))
                    .build();

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } else {
            throw new CustomException("INCORRECT_API_OPERATION", "UPDATE and DELETE apiOperation supported for update endpoint");
        }
    }

}
