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

    /**
     * Parses a FHIR Bundle JSON and loads supported resources into DIGIT services
     * @param fhirJson FHIR Bundle payload as JSON
     * @return map of entity name to processing metrics
     * @throws Exception if downstream service invocation fails
     */
    public HashMap<String, HashMap<String, Integer>> parseAndLoadFHIRResource(String fhirJson, RequestInfo requestInfo) throws Exception {
        HashMap<String, HashMap<String, Integer>> entityResults = new HashMap<>();

        Bundle bundle = parseBundle(fhirJson);
        if (bundle == null) return entityResults;
        EntityMaps emaps = extractEntitiesFromBundle(bundle);
        entityResults = processEntities(emaps, requestInfo);

        return entityResults;
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

    // Call downstream services to create/update entities and gather metrics
    private HashMap<String, HashMap<String, Integer>> processEntities(EntityMaps emaps,RequestInfo requestInfo) throws Exception {
        HashMap<String, HashMap<String, Integer>> entityResults = new HashMap<>();

        logger.info("supply delivery map: {}", emaps.supplyDeliveryMap);
        HashMap<String, Integer> stockResults = sdToStockService.transformSupplyDeliveryToStock(emaps.supplyDeliveryMap, requestInfo);
        entityResults.put("Stock", stockResults);

        logger.info("facility map: {}", emaps.facilityMap);
        HashMap<String, Integer> facilityResults = locToFacilityService.transformLocationToFacility(emaps.facilityMap, requestInfo);
        entityResults.put("facility", facilityResults);

        logger.info("boundary relation map: {}", emaps.boundaryRelationMap);
        HashMap<String, Integer> boundaryResults = locToBoundaryService.transformLocationToBoundary(emaps.boundaryRelationMap, requestInfo);
        entityResults.put("boundary", boundaryResults);

        logger.info("Stock Reconciliation map: {}", emaps.stockReconciliationMap);
        HashMap<String, Integer> stockReconResults = irToStkRecService.transformInventoryReportToStockReconciliation(emaps.stockReconciliationMap, requestInfo);
        // put under a descriptive key
        entityResults.put("StockReconciliation", stockReconResults);

        logger.info("Product Variant map: {}", emaps.productVariantMap);
        HashMap<String, Integer> productVariantResults = invToProductService.transformInventoryItemToProductVariant(emaps.productVariantMap, requestInfo);
        entityResults.put("Product Variant", productVariantResults);

        return entityResults;
    }

}
