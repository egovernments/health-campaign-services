package org.egov.fhirtransformer.service;

import digit.web.models.BoundaryRelationshipSearchCriteria;
import digit.web.models.BoundarySearchResponse;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.facility.FacilityBulkResponse;
import org.egov.common.models.facility.FacilitySearchRequest;
import org.egov.common.models.product.ProductVariantResponse;
import org.egov.common.models.product.ProductVariantSearchRequest;
import org.egov.common.models.stock.*;
import org.egov.fhirtransformer.web.controller.FhirApiController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for integrating with external DIGIT domain services.
 */
@Service
public class ApiIntegrationService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${app.tenant-id}")
    private String tenantId;

    @Value("${facility.search.url}")
    private String facilityUrl;

    @Value("${product.variant.search.url}")
    private String productVariantUrl;

    @Value("${stock.search.url}")
    private String stockSearchUrl;

    @Value("${stock.reconciliation.search.url}")
    private String stockReconciliationUrl;

    @Value("${boundary.relationship.search.url}")
    private String boundaryRelationshipUrl;

    private static final Logger logger = LoggerFactory.getLogger(FhirApiController.class);

    /**
     * Builds a URI with pagination and tenant query parameters.
     * @param urlParams pagination and tenant parameters
     * @param url base service URL
     * @return constructed {@link URI}
     */
    public URI formUri(URLParams urlParams, String url){

        URI uri = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("limit", urlParams.getLimit())
                .queryParam("offset", urlParams.getOffset())
                .queryParam("tenantId", urlParams.getTenantId())
                .build().toUri();
        return uri;
    }

    /**
     * Builds a URI for boundary relationship search using provided criteria.
     * @param criteria boundary relationship search criteria
     * @param url base boundary search URL
     * @return constructed {@link URI}
     */
    public URI formBoundaryUri(BoundaryRelationshipSearchCriteria criteria, String url) {

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);

        if (hasText(criteria.getTenantId())) {
            builder.queryParam("tenantId", criteria.getTenantId().trim());
        }
        if (hasText(criteria.getBoundaryType())) {
            builder.queryParam("boundaryType", criteria.getBoundaryType().trim());
        }
        if (hasText(criteria.getHierarchyType())) {
            builder.queryParam("hierarchyType", criteria.getHierarchyType().trim());
        }
        if (criteria.getIncludeChildren() != null) {
            builder.queryParam("includeChildren", criteria.getIncludeChildren());
        }
        if (criteria.getIncludeParents() != null) {
            builder.queryParam("includeParents", criteria.getIncludeParents());
        }
        if (criteria.getCodes() != null && !criteria.getCodes().isEmpty()) {
            builder.queryParam("codes", criteria.getCodes());
        }
        return builder.build().toUri();
    }

    /**
     * Checks whether the given string contains non-whitespace text.
     *
     * @param value string to check; may be {@code null}
     * @return {@code true} if the string is not {@code null} and contains
     *         at least one non-whitespace character, otherwise {@code false}
     */
    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Fetches Facility records from the Facility service.
     *
     * @param urlParams pagination and tenant parameters
     * @param facilitySearchRequest facility search request payload
     * @return {@link FacilityBulkResponse}, or {@code null} if the response is empty
     */
    public FacilityBulkResponse fetchAllFacilities(URLParams urlParams, FacilitySearchRequest facilitySearchRequest) {

        URI uri = formUri(urlParams,facilityUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<FacilitySearchRequest> entity = new HttpEntity<>(facilitySearchRequest, headers);

        ResponseEntity<FacilityBulkResponse> response = restTemplate.exchange(
                uri,
                HttpMethod.POST,
                entity,
                FacilityBulkResponse.class
        );
        if (!response.hasBody() || response.getBody() == null) {
            logger.warn("Empty response body received from Facility service for URI: {}", uri);
            return null;
        }
        return response.getBody();
    }

    /**
     * Fetches ProductVariant records from the Product service.
     *
     * @param urlParams pagination and tenant parameters
     * @param productVariantSearchRequest product variant search request payload
     * @return {@link ProductVariantResponse}, or {@code null} if the response is empty
     */
    public ProductVariantResponse fetchAllProductVariants(URLParams urlParams, ProductVariantSearchRequest productVariantSearchRequest) {

        URI uri = formUri(urlParams,productVariantUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ProductVariantSearchRequest> entity = new HttpEntity<>(productVariantSearchRequest, headers);

        ResponseEntity<ProductVariantResponse> response = restTemplate.exchange(
                uri,
                HttpMethod.POST,
                entity,
                ProductVariantResponse.class
        );
        if (!response.hasBody() || response.getBody() == null) {
            logger.warn("Empty response body received from ProductVariant service for URI: {}", uri);
            return null;
        }
        return response.getBody();
    }

    /**
     * Fetches Stock records from the Stock service.
     *
     * @param urlParams pagination and tenant parameters
     * @param stockRequest stock search request payload
     * @return {@link StockBulkResponse}, or {@code null} if the response is empty
     */
    public StockBulkResponse fetchAllStocks(URLParams urlParams, StockSearchRequest stockRequest) {

        URI uri = formUri(urlParams,stockSearchUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<StockSearchRequest> entity = new HttpEntity<>(stockRequest, headers);

        ResponseEntity<StockBulkResponse> response = restTemplate.exchange(
                uri,
                HttpMethod.POST,
                entity,
                StockBulkResponse.class
        );

        if (!response.hasBody() || response.getBody() == null) {
            logger.warn("Empty response body received from Stock service for URI: {}", uri);
            return null;
        }
        return response.getBody();
    }

    /**
     * Fetches StockReconciliation records from the Stock Reconciliation service.
     *
     * @param urlParams pagination and tenant parameters
     * @param stockReconciliationSearchRequest stock reconciliation search request payload
     * @return {@link StockReconciliationBulkResponse}, or {@code null} if the response is empty
     */
    public StockReconciliationBulkResponse fetchAllStockReconciliation(URLParams urlParams, StockReconciliationSearchRequest stockReconciliationSearchRequest) {

        URI uri = formUri(urlParams,stockReconciliationUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<StockReconciliationSearchRequest> entity = new HttpEntity<>(stockReconciliationSearchRequest, headers);

        ResponseEntity<StockReconciliationBulkResponse> response = restTemplate.exchange(
                uri,
                HttpMethod.POST,
                entity,
                StockReconciliationBulkResponse.class
        );
        if (!response.hasBody() || response.getBody() == null) {
            logger.warn("Empty response body received from StockReconciliation service for URI: {}", uri);
            return null;
        }
        return response.getBody();
    }

    /**
     * Fetches Boundary relationship records from the Boundary service.
     *
     * @param boundaryRelationshipSearchCriteria boundary search criteria
     * @param requestInfo request metadata
     * @return {@link BoundarySearchResponse}, or {@code null} if the response is empty
     */
    public BoundarySearchResponse fetchAllBoundaries( BoundaryRelationshipSearchCriteria boundaryRelationshipSearchCriteria,RequestInfo requestInfo) {
        URI uri = formBoundaryUri(boundaryRelationshipSearchCriteria, boundaryRelationshipUrl);
        Map<String, Object> body = new HashMap<>();
        body.put("RequestInfo", requestInfo);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<BoundarySearchResponse> response = restTemplate.exchange(
                uri,
                HttpMethod.POST,
                entity,
                BoundarySearchResponse.class
        );

        if (!response.hasBody() || response.getBody() == null) {
            logger.warn("Empty response body received from Boundary service for URI: {}", uri);
            return null;
        }
        return response.getBody();
    }

    /**
     * Sends a POST request to an external service endpoint.
     *
     * @param requestBody request payload
     * @param url target service URL
     * @param <T> request payload type
     * @return {@link ResponseEntity} containing {@link ResponseInfo}
     */
    public <T> ResponseEntity<ResponseInfo> sendRequestToAPI(T requestBody, String url) {

        URI uri = URI.create(url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.exchange(
                uri,
                HttpMethod.POST,
                entity,
                ResponseInfo.class
        );
    }

    /**
     * Creates pagination and tenant parameters for search requests.
     *
     * @param idList list of entity identifiers
     * @return populated {@link URLParams}
     */
    public URLParams formURLParams(List<String> idList) {
        URLParams urlParams = new URLParams();
        urlParams.setLimit(idList.size());
        urlParams.setOffset(0);
        urlParams.setTenantId(tenantId);
        return urlParams;
    }

}
