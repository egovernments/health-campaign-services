package org.egov.fhirtransformer.web.controller;

import ca.uhn.fhir.validation.ValidationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.web.models.BoundarySearchResponse;
import org.egov.common.contract.models.RequestInfoWrapper;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.facility.FacilityBulkResponse;
import org.egov.common.models.facility.FacilitySearchRequest;
import org.egov.common.models.product.ProductVariantResponse;
import org.egov.common.models.product.ProductVariantSearchRequest;
import org.egov.common.models.stock.*;
import org.egov.fhirtransformer.service.ApiIntegrationService;
import org.egov.fhirtransformer.service.FhirParseNLoadService;
import org.egov.fhirtransformer.service.FhirTransformerService;
import org.egov.fhirtransformer.repository.KafkaProducerService;
import org.egov.fhirtransformer.utils.FhirRequestBuilder;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import digit.web.models.BoundaryRelationshipSearchCriteria;
import java.util.stream.Collectors;

import org.slf4j.Logger;

/**
 * REST controller exposing FHIR transformation and ingestion APIs.
 *
 * <p>This controller:
 * <ul>
 *   <li>Validates incoming FHIR resources</li>
 *   <li>Fetches DIGIT domain data and exposes it as FHIR Bundles</li>
 *   <li>Consumes FHIR Bundles and loads them into DIGIT services</li>
 *   <li>Publishes validation and processing failures to Kafka</li>
 * </ul>
 */
@RestController
@RequestMapping("/fhir-api")
public class FhirApiController {

    @Autowired
    private FhirTransformerService ftService;

    @Autowired
    private ApiIntegrationService diService;

    @Autowired
    private KafkaProducerService kafkaService;

    @Autowired
    private FhirParseNLoadService fpService;

    private static final Logger logger = LoggerFactory.getLogger(FhirApiController.class);

    /**
     * Health check endpoint for service availability.
     * @return health status message
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Service is healthy!");
    }

    /**
     * Validates a FHIR JSON payload against configured FHIR profiles.
     *
     * @param fhirJson FHIR resource payload as JSON
     * @return validation status message
     * @throws JsonProcessingException if payload parsing fails
     */
    @PostMapping("/validate")
    public ResponseEntity<String> validateFHIR(@RequestBody String fhirJson) throws JsonProcessingException {
        ValidationResult result = ftService.validateFHIRResource(fhirJson);
        boolean isValid = result.isSuccessful();
        return ResponseEntity.ok(
                isValid
                        ? "Valid FHIR resource"
                        : "Invalid FHIR resource. Errors: [" +
                        result.getMessages().stream()
                                .filter(msg -> msg.getSeverity() != null && msg.getSeverity().name().equalsIgnoreCase("error"))
                                .map(msg -> msg.getMessage())
                                .collect(Collectors.joining(", "))
                        + "]"
        );
    }

    /**
     * Fetches Facility data and returns it as a FHIR Location Bundle.
     *
     * @param urlParams pagination and tenant parameters
     * @param request facility search request
     * @return FHIR Bundle serialized as JSON, or message if no data found
     */
    @PostMapping("/fetchAllFacilities")
    public ResponseEntity<String> fetchAllFacilities(@Valid @ModelAttribute URLParams urlParams
            , @Valid @RequestBody FacilitySearchRequest request
    ) {
        FacilityBulkResponse response = diService.fetchAllFacilities(urlParams, request);
        if (response == null || response.getFacilities() == null)
            return ResponseEntity.noContent().build();
        Integer totalCount = response.getTotalCount() != null
                ? response.getTotalCount().intValue() : 0;
        String facilities = ftService.convertFacilitiesToFHIR(response.getFacilities(), urlParams, totalCount);
        return ResponseEntity.ok(facilities);
    }

    /**
     * Fetches ProductVariant data and returns it as a FHIR InventoryItem Bundle.
     *
     * @param urlParams pagination and tenant parameters
     * @param request product variant search request
     * @return FHIR Bundle serialized as JSON, or message if no data found
     */
    @PostMapping("/fetchAllProductVariants")
    public ResponseEntity<String> fetchAllProductVariants(@Valid @ModelAttribute URLParams urlParams
            , @Valid @RequestBody ProductVariantSearchRequest request
    ) {
        ProductVariantResponse response = diService.fetchAllProductVariants(urlParams, request);
        if (response == null || response.getProductVariant() == null)
            return ResponseEntity.noContent().build();
        Integer totalCount = response.getTotalCount() != null
                ? response.getTotalCount().intValue() : 0;

        String productVariants = ftService.convertProductVariantsToFHIR(response.getProductVariant(), urlParams, totalCount);
        return ResponseEntity.ok(productVariants);
    }

    /**
     * Fetches Stock data and returns it as a FHIR SupplyDelivery Bundle.
     *
     * @param urlParams pagination and tenant parameters
     * @param stockRequest stock search request
     * @return FHIR Bundle serialized as JSON, or message if no data found
     */
    @PostMapping("/fetchAllStocks")
    public ResponseEntity<String> fetchAllStocks(@Valid @ModelAttribute URLParams urlParams
            , @Valid @RequestBody StockSearchRequest stockRequest) {

        StockBulkResponse response = diService.fetchAllStocks(urlParams, stockRequest);
        if (response.getStock() == null)
            return ResponseEntity.noContent().build();

        Integer totalCount = response.getTotalCount() != null
                ? response.getTotalCount().intValue() : 0;
        String stock = ftService.convertStocksToFHIR(response.getStock(),
                urlParams, totalCount);
        return ResponseEntity.ok(stock);
    }

    /**
     * Fetches StockReconciliation data and returns it as a FHIR InventoryReport Bundle.
     *
     * @param urlParams pagination and tenant parameters
     * @param stockReconciliationSearchRequest stock reconciliation search request
     * @return FHIR Bundle serialized as JSON, or message if no data found
     */
    @PostMapping("/fetchAllStockReconciliation")
    public ResponseEntity<String> fetchAllStockReconciliation(@Valid @ModelAttribute URLParams urlParams,
                                                              @Valid @RequestBody StockReconciliationSearchRequest stockReconciliationSearchRequest) {

        StockReconciliationBulkResponse response = diService.fetchAllStockReconciliation(urlParams, stockReconciliationSearchRequest);
        if (response == null || response.getStockReconciliation() == null)
            return ResponseEntity.noContent().build();

        Integer totalCount = response.getTotalCount() != null
                ? response.getTotalCount().intValue() : 0;
        String stockReconciliation = ftService.convertStocksReconciliationToFHIR(response.getStockReconciliation(),
                urlParams, totalCount);
        return ResponseEntity.ok(stockReconciliation);
    }

    /**
     * Fetches boundary hierarchy data and returns it as a FHIR Location Bundle.
     *
     * @param boundaryRelationshipSearchCriteria boundary search criteria
     * @param requestInfo request metadata
     * @return FHIR Bundle serialized as JSON
     */
    @PostMapping("/fetchAllBoundaries")
    public ResponseEntity<String> fetchAllBoundaries(@Valid @ModelAttribute BoundaryRelationshipSearchCriteria boundaryRelationshipSearchCriteria
              ,@RequestBody RequestInfoWrapper wrapper
    ) {
        RequestInfo requestInfo = wrapper.getRequestInfo();
        BoundarySearchResponse response = diService.fetchAllBoundaries(boundaryRelationshipSearchCriteria, requestInfo);
        String boundaries = ftService.convertBoundaryRelationshipToFHIR(response.getTenantBoundary());
        return ResponseEntity.ok(boundaries);
    }

    /**
     * Consumes a FHIR Bundle payload and loads supported resources into DIGIT services.
     *
     * @param fhirJson FHIR Bundle payload as JSON
     * @return processing result or error message
     * @throws Exception if downstream processing fails
     */
    @PostMapping("/consumeFHIR")
    public ResponseEntity<String> consumeFHIR(@RequestHeader(value = "Authorization", required = false) String authToken,
                                              @RequestBody FhirRequestBuilder fhirRequestBuilder) throws Exception {

        FhirParseNLoadService.EntityProcessingResponse response;
        try {
            RequestInfo requestInfo = fhirRequestBuilder.getRequestInfo();
            String fhirJson = new ObjectMapper().writeValueAsString(fhirRequestBuilder.getFhir());

            if (requestInfo != null && authToken != null && !authToken.isEmpty()) {
                requestInfo.setAuthToken(authToken);
            }

            //Parse incoming FHIR JSON
            JsonNode root = new ObjectMapper().readTree(fhirJson);
            String bundleId = root.path("id").asText();

            // Validate the FHIR resource
            ValidationResult result = ftService.validateFHIRResource(fhirJson);

            // If validation fails → publish to DLQ
            if (!result.isSuccessful()) {
                kafkaService.publishToDLQ(result, bundleId, root);
                return ResponseEntity
                        .badRequest()
                        .body("Invalid FHIR resource");
            }

            // If valid → parse and load FHIR resource
            response = fpService.parseAndLoadFHIRResource(fhirJson, requestInfo);

            String responseBody = new ObjectMapper().writeValueAsString(response);
            if ("PARTIAL_SUCCESS".equalsIgnoreCase(response.getStatus())) {
                return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(responseBody);
            }
            if ("FAILED".equalsIgnoreCase(response.getStatus())) {
                return ResponseEntity.badRequest().body(responseBody);
            }

            return ResponseEntity.ok(responseBody);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse FHIR JSON :", e);
            return ResponseEntity.badRequest().body("Invalid FHIR resource");
        } catch (Exception e) {
            logger.error("Unexpected error while processing FHIR resource", e);
            return ResponseEntity
                   .badRequest()
                   .body("Processing Failed");
        }
   }
}
