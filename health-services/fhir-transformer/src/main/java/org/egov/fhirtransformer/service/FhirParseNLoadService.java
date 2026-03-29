package org.egov.fhirtransformer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import digit.web.models.BoundaryRelation;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.stock.*;
import org.egov.fhirtransformer.common.Constants;
import org.egov.fhirtransformer.mapping.fhirBuilder.DIGITHCMBoundaryMapper;
import org.egov.fhirtransformer.mapping.fhirBuilder.DIGITHCMFacilityMapper;
import org.egov.fhirtransformer.mapping.fhirBuilder.DIGITHCMProductVariantMapper;
import org.egov.fhirtransformer.mapping.fhirBuilder.DIGITHCMStockMapper;
import org.egov.fhirtransformer.mapping.requestBuilder.*;
import org.egov.fhirtransformer.repository.KafkaProducerService;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.InventoryItem;
import org.hl7.fhir.r5.model.InventoryReport;
import org.hl7.fhir.r5.model.SupplyDelivery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for parsing FHIR Bundles and loading
 * corresponding DIGIT domain entities.
 */
@Service
public class FhirParseNLoadService {

    private static final Logger logger = LoggerFactory.getLogger(FhirParseNLoadService.class);

    @Autowired
    private FhirContext ctx;

    @Autowired
    private ApiIntegrationService apiIntegrationService;

    @Autowired
    private SupplyDeliveryToStockService sdToStockService;

    @Autowired
    private LocationToFacilityService locToFacilityService;

    @Autowired
    private LocationToBoundaryService locToBoundaryService;

    @Autowired
    private InventoryReportToStockReconciliationService irToStkRecService;

    @Autowired
    private InventoryItemToProductVariant invToProductService;

    @Autowired
    private KafkaProducerService kafkaService;

    @Value("${app.tenant-id}")
    private String tenantID;

    // Helper holder for entity maps extracted from a bundle
    private static class EntityMaps {
        public final HashMap<String, Stock> supplyDeliveryMap = new HashMap<>();
        public final HashMap<String, Facility> facilityMap = new HashMap<>();
        public final HashMap<String, BoundaryRelation> boundaryRelationMap = new HashMap<>();
        public final HashMap<String, StockReconciliation> stockReconciliationMap = new HashMap<>();
        public final HashMap<String, ProductVariant> productVariantMap = new HashMap<>();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /**
     * Processing summary with per-entity metrics and per-entity errors.
     * status values: SUCCESS, PARTIAL_SUCCESS, FAILED.
     */
    public static class EntityProcessingResponse {
        private final HashMap<String, HashMap<String, Integer>> entityResults = new HashMap<>();
        private final HashMap<String, String> entityErrors = new HashMap<>();
        private String status = "SUCCESS";

        public HashMap<String, HashMap<String, Integer>> getEntityResults() {
            return entityResults;
        }

        public HashMap<String, String> getEntityErrors() {
            return entityErrors;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    /**
     * Parses a FHIR Bundle JSON and loads supported resources into DIGIT services
     * @param fhirJson FHIR Bundle payload as JSON
     * @return processing summary containing per-entity metrics and per-entity errors
     */
    public EntityProcessingResponse parseAndLoadFHIRResource(String fhirJson, RequestInfo requestInfo) {
        EntityProcessingResponse response = new EntityProcessingResponse();

        Bundle bundle = parseBundle(fhirJson);
        if (bundle == null) {
            response.getEntityErrors().put("Bundle", "Failed to parse FHIR bundle payload");
            response.setStatus("FAILED");
            return response;
        }
        EntityMaps emaps = extractEntitiesFromBundle(bundle);
        processEntities(emaps, requestInfo, response);
        finalizeStatus(response);

        return response;
    }

    // Parse the incoming JSON into a FHIR Bundle
    private Bundle parseBundle(String fhirJson) {
        IParser parser = ctx.newJsonParser();
        try {
            return parser.parseResource(Bundle.class, fhirJson);
        } catch (Exception e) {
            logger.error("Failed to parse FHIR resource: {}", e.getMessage(), e);
            return null;
        }
    }

    // Extract resources from bundle into typed maps; per-entry failures are reported to Kafka and skipped
    private EntityMaps extractEntitiesFromBundle(Bundle bundle) {
        EntityMaps emaps = new EntityMaps();

        if (bundle.getEntry() == null) return emaps;

        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            try {
                processBundleEntry(entry, emaps);
            } catch (Exception e) {
                logger.error("Error processing entry: {}", e.getMessage(), e);
                try {
                    logger.info("Skipping entry with resource ID: {}", entry.getResource().getIdElement().getIdPart());
                    kafkaService.publishFhirResourceFailures(entry, e.getMessage());
                } catch (Exception ex) {
                    logger.error("Failed to publish resource failure to Kafka: {}", ex.getMessage(), ex);
                }
            }
        }
        return emaps;
    }

    // Process a single bundle entry and populate the passed EntityMaps
    private void processBundleEntry(Bundle.BundleEntryComponent entry, EntityMaps emaps) throws Exception {

        DIGITHCMStockMapper stockMapper = new DIGITHCMStockMapper();
        DIGITHCMFacilityMapper facilityMapper = new DIGITHCMFacilityMapper();
        DIGITHCMBoundaryMapper boundaryMapper = new DIGITHCMBoundaryMapper();
        DIGITHCMProductVariantMapper productVariantMapper = new DIGITHCMProductVariantMapper();
        if (entry.getResource() instanceof SupplyDelivery) {
            SupplyDelivery supplyDelivery = (SupplyDelivery) entry.getResource();
            String logicalId = supplyDelivery.getIdElement().getIdPart();
            Stock stock = stockMapper.buildStockFromSupplyDelivery(supplyDelivery, tenantID);
            emaps.supplyDeliveryMap.put(logicalId, stock);
            return;
        }

        if (entry.getResource() instanceof org.hl7.fhir.r5.model.Location) {
            org.hl7.fhir.r5.model.Location location = (org.hl7.fhir.r5.model.Location) entry.getResource();
            List<String> profiles = location.getMeta().getProfile().stream()
                    .map(p -> p.getValue())
                    .collect(Collectors.toList());
            String logicalId = location.getIdElement().getIdPart();
            if (profiles.contains(Constants.PROFILE_DIGIT_HCM_FACILITY)){
                Facility facility = facilityMapper.convertFhirLocationToFacility(location, tenantID);
                emaps.facilityMap.put(logicalId, facility);
            }
            else if (profiles.contains(Constants.PROFILE_DIGIT_HCM_BOUNDARY)) {
                logicalId = location.getName();
                BoundaryRelation boundaryRelation = boundaryMapper.convertFhirLocationToBoundaryRelation(location, tenantID);
                emaps.boundaryRelationMap.put(logicalId, boundaryRelation);
            }
            return;
        }
        if (entry.getResource() instanceof InventoryReport inventoryReport) {

            String logicalId = inventoryReport.getIdElement().getIdPart();
            StockReconciliation stockRecon= stockMapper.buildStockReconFromInventoryReport(inventoryReport, tenantID);
            emaps.stockReconciliationMap.put(logicalId, stockRecon);
            return;
        }

        if (entry.getResource() instanceof InventoryItem inventoryItem) {
            String logicalId = inventoryItem.getIdElement().getIdPart();
            ProductVariant productVariant = productVariantMapper.buildProductVariantFromInventoryItem(inventoryItem, tenantID);
            emaps.productVariantMap.put(logicalId, productVariant);
        }
    }

    // Call downstream services independently so one entity failure does not block others
    private void processEntities(EntityMaps emaps, RequestInfo requestInfo, EntityProcessingResponse response) {
        logger.info("supply delivery map: {}", emaps.supplyDeliveryMap);
        processEntity("Stock", response,
                () -> sdToStockService.transformSupplyDeliveryToStock(emaps.supplyDeliveryMap, requestInfo));

        logger.info("facility map: {}", emaps.facilityMap);
        processEntity("Facility", response,
                () -> locToFacilityService.transformLocationToFacility(emaps.facilityMap, requestInfo));

        logger.info("boundary relation map: {}", emaps.boundaryRelationMap);
        processEntity("Boundary", response,
                () -> locToBoundaryService.transformLocationToBoundary(emaps.boundaryRelationMap, requestInfo));

        logger.info("Stock Reconciliation map: {}", emaps.stockReconciliationMap);
        processEntity("StockReconciliation", response,
                () -> irToStkRecService.transformInventoryReportToStockReconciliation(emaps.stockReconciliationMap, requestInfo));

        logger.info("Product Variant map: {}", emaps.productVariantMap);
        processEntity("ProductVariant", response,
                () -> invToProductService.transformInventoryItemToProductVariant(emaps.productVariantMap, requestInfo));
    }

    private void processEntity(String entityName,
                               EntityProcessingResponse response,
                               ThrowingSupplier<HashMap<String, Integer>> processingFn) {
        try {
            HashMap<String, Integer> result = processingFn.get();
            response.getEntityResults().put(entityName, result != null ? result : new HashMap<>());
        } catch (Exception e) {
            logger.error("Failed processing entity type {}: {}", entityName, e.getMessage(), e);
            response.getEntityErrors().put(entityName, e.getMessage());
        }
    }

    private void finalizeStatus(EntityProcessingResponse response) {
        boolean hasResults = !response.getEntityResults().isEmpty();
        boolean hasErrors = !response.getEntityErrors().isEmpty();

        if (hasResults && hasErrors) {
            response.setStatus("PARTIAL_SUCCESS");
            return;
        }
        if (!hasResults && hasErrors) {
            response.setStatus("FAILED");
            return;
        }
        response.setStatus("SUCCESS");
    }

}
