package org.egov.product.web.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.egov.common.models.product.Product;
import org.egov.common.models.product.ProductRequest;
import org.egov.common.models.product.ProductResponse;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.product.ProductVariantRequest;
import org.egov.common.models.product.ProductVariantResponse;
import org.egov.common.utils.CommonUtils;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.product.service.ProductService;
import org.egov.product.service.ProductVariantService;
import org.egov.product.web.models.ProductSearchRequest;
import org.egov.product.web.models.ProductVariantSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T16:45:24.641+05:30")

@Controller
@Validated
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
        if (!CommonUtils.isForCreate(productRequest)){
            throw new CustomException("INVALID_API_OPERATION", String.format("API Operation %s not valid for create request", productRequest.getApiOperation()));
        }

        List<Product> products = productService.create(productRequest);
        ProductResponse response = ProductResponse.builder()
                .product(products)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(productRequest.getRequestInfo(), true))
                .build();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<ProductResponse> productV1SearchPost(@ApiParam(value = "Capture details of Product.", required = true) @Valid @RequestBody ProductSearchRequest productSearchRequest,
                                                               @NotNull @Min(0) @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit,
                                                               @NotNull @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset,
                                                               @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @Size(min = 2, max = 1000) @RequestParam(value = "tenantId", required = true) String tenantId,
                                                               @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince,
                                                               @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) throws Exception {

        List<Product> products = productService.search(productSearchRequest, limit, offset, tenantId,
                lastChangedSince, includeDeleted);
        ProductResponse productResponse = ProductResponse.builder()
                .product(products)
                .responseInfo(ResponseInfoFactory.createResponseInfo(productSearchRequest.getRequestInfo(), true))
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(productResponse);
    }

    @RequestMapping(value = "/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ProductResponse> productV1UpdatePost(@ApiParam(value = "Capture details of Product.", required = true) @Valid @RequestBody ProductRequest productRequest) throws Exception {
        if (!CommonUtils.isForUpdate(productRequest)
                && !CommonUtils.isForDelete(productRequest)) {
            throw new CustomException("INVALID_API_OPERATION", String.format("API Operation %s not valid for update request",
                    productRequest.getApiOperation()));
        }

        List<Product> products = productService.update(productRequest);
        ProductResponse response = ProductResponse.builder()
                .product(products)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(productRequest.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/variant/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<ProductVariantResponse> productVariantV1CreatePost(@ApiParam(value = "Capture details of Product Variant.", required = true)
                                                                                 @Valid @RequestBody ProductVariantRequest request) throws Exception {
        if (CommonUtils.isForCreate(request)) {
            List<ProductVariant> productVariants = productVariantService.create(request);
            ProductVariantResponse response = ProductVariantResponse.builder()
                    .productVariant(productVariants)
                    .responseInfo(ResponseInfoFactory
                            .createResponseInfo(request.getRequestInfo(), true))
                    .build();

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } else {
            throw new CustomException("INVALID_API_OPERATION", String.format("API Operation %s not valid for create request", request.getApiOperation()));
        }
    }

    @RequestMapping(value = "/variant/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<ProductVariantResponse> productVariantV1SearchPost(@ApiParam(value = "Capture details of Product variant.", required = true) @Valid @RequestBody ProductVariantSearchRequest productVariantSearchRequest,
                                                                             @NotNull @Min(0) @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit,
                                                                             @NotNull @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset,
                                                                             @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @Size(min = 2, max = 1000) @RequestParam(value = "tenantId", required = true) String tenantId,
                                                                             @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince,
                                                                             @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) throws Exception {
        List<ProductVariant> productVariants = productVariantService.search(productVariantSearchRequest, limit, offset,
                tenantId, lastChangedSince, includeDeleted);
        ProductVariantResponse productVariantResponse = ProductVariantResponse.builder()
                .productVariant(productVariants)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(productVariantSearchRequest.getRequestInfo(), true))
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(productVariantResponse);
    }

    @RequestMapping(value = "/variant/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<ProductVariantResponse> productVariantV1UpdatePost(@ApiParam(value = "Capture details of Product Variant.", required = true) @Valid @RequestBody ProductVariantRequest request) {
        if (CommonUtils.isForUpdate(request)
                || CommonUtils.isForDelete(request)) {
            List<ProductVariant> productVariants = productVariantService.update(request);
            ProductVariantResponse response = ProductVariantResponse.builder()
                    .productVariant(productVariants)
                    .responseInfo(ResponseInfoFactory
                            .createResponseInfo(request.getRequestInfo(), true))
                    .build();

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } else {
            throw new CustomException("INVALID_API_OPERATION", String.format("API Operation %s not valid for update request", request.getApiOperation()));
        }
    }

}
