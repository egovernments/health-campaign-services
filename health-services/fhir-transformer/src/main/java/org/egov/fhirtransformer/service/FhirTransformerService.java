package org.egov.fhirtransformer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.ValidationResult;
import digit.web.models.EnrichedBoundary;
import digit.web.models.HierarchyRelation;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.stock.*;
import org.egov.fhirtransformer.common.Constants;
import org.egov.fhirtransformer.mapping.fhirBuilder.DIGITHCMFacilityMapper;
import org.egov.fhirtransformer.mapping.fhirBuilder.DIGITHCMProductVariantMapper;
import org.egov.fhirtransformer.mapping.fhirBuilder.DIGITHCMStockMapper;
import org.egov.fhirtransformer.utils.BundleBuilder;
import org.egov.fhirtransformer.validator.CustomFHIRValidator;
import org.hl7.fhir.r5.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.egov.fhirtransformer.mapping.fhirBuilder.DIGITHCMBoundaryMapper.buildLocationFromHierarchyRelation;

/**
 * Service responsible for transforming DIGIT domain models
 * into FHIR R5 resources and Bundles.
 */
@Service
public class FhirTransformerService {


    private final CustomFHIRValidator validator;

    private final FhirContext ctx;

    @Autowired
    public FhirTransformerService(CustomFHIRValidator validator, FhirContext ctx) {
        this.validator = validator;
        this.ctx = ctx;
    }


    /**
     * Validates a FHIR JSON payload against configured FHIR profiles.
     *
     * @param fhirJson FHIR payload as JSON
     * @return validation result containing errors and warnings
     */
    public ValidationResult validateFHIRResource(String fhirJson) {
        return validator.validate(fhirJson);
    }

    /**
     * Converts Facility domain objects into a FHIR Location Bundle.
     *
     * @param facilities list of Facility domain objects
     * @param urlParams pagination and tenant parameters
     * @param totalCount total number of records available
     * @return FHIR Bundle serialized as JSON
     */
    public String convertFacilitiesToFHIR(List<Facility> facilities, URLParams urlParams, Integer totalCount) {
        List<Location> locations = facilities.stream()
                .map(DIGITHCMFacilityMapper::buildLocationFromFacility)
                .collect(Collectors.toList());

        Bundle bundle = BundleBuilder.buildBundle(locations, urlParams, totalCount, Constants.FACILITIES_API_PATH);
        return ctx.newJsonParser().encodeResourceToString(bundle);
    }

    /**
     * Converts ProductVariant domain objects into a FHIR InventoryItem Bundle.
     *
     * @param productVariants list of ProductVariant domain objects
     * @param urlParams pagination and tenant parameters
     * @param totalCount total number of records available
     * @return FHIR Bundle serialized as JSON
     */
    public String convertProductVariantsToFHIR(List<ProductVariant> productVariants, URLParams urlParams, Integer totalCount) {
        List<InventoryItem> inventoryItems = productVariants.stream()
                .map(DIGITHCMProductVariantMapper::buildInventoryFromProductVariant)
                .collect(Collectors.toList());

        Bundle bundle = BundleBuilder.buildBundle(inventoryItems, urlParams, totalCount, Constants.PRODUCT_VARIANT_API_PATH);
        return ctx.newJsonParser().encodeResourceToString(bundle);
    }

    /**
     * Converts Stock domain objects into a FHIR SupplyDelivery Bundle.
     *
     * @param stock list of Stock domain objects
     * @param urlParams pagination and tenant parameters
     * @param totalCount total number of records available
     * @return FHIR Bundle serialized as JSON
     */
    public String convertStocksToFHIR(List<Stock> stock, URLParams urlParams, Integer totalCount) {
        List<SupplyDelivery> supplyDeliveries = stock.stream()
                .map(DIGITHCMStockMapper::buildSupplyDeliveryFromStock)
                .toList();

        Bundle bundle = BundleBuilder.buildBundle(supplyDeliveries, urlParams, totalCount, Constants.STOCKS_API_PATH);
        return ctx.newJsonParser().encodeResourceToString(bundle);
    }

    /**
     * Converts StockReconciliation domain objects into a FHIR InventoryReport Bundle.
     *
     * @param stockReconciliation list of StockReconciliation domain objects
     * @param urlParams pagination and tenant parameters
     * @param totalCount total number of records available
     * @return FHIR Bundle serialized as JSON
     */
    public String convertStocksReconciliationToFHIR(List<StockReconciliation> stockReconciliation,
                                                    URLParams urlParams, Integer totalCount) {
        List<InventoryReport> inventoryReport = stockReconciliation.stream()
                .map(DIGITHCMStockMapper::buildInventoryReportFromStockReconciliation)
                .toList();

        Bundle bundle = BundleBuilder.buildBundle(inventoryReport, urlParams, totalCount, Constants.STOCK_RECONCILIATION_API_PATH);
        return ctx.newJsonParser().encodeResourceToString(bundle);
    }

    /**
     * Converts hierarchical boundary relationships into FHIR Location resources.
     * @param hierarchyRelations list of boundary hierarchy relations
     * @return FHIR Bundle serialized as JSON
     */
    public String convertBoundaryRelationshipToFHIR(List<HierarchyRelation> hierarchyRelations){
        List<Location> locations = new ArrayList<>();

        for (HierarchyRelation relation : hierarchyRelations) {
            for (EnrichedBoundary boundary : relation.getBoundary()) {
                traverseBoundary(boundary, null, locations);
            }
        }

        Bundle bundle = BundleBuilder.buildBoundaryLocationBundle(locations);
        return ctx.newJsonParser().encodeResourceToString(bundle);
    }

    /**
     * Recursively traverses boundary hierarchy and builds FHIR Location resources.
     *
     * @param current current boundary being processed
     * @param parentLocation parent FHIR Location; may be {@code null}
     * @param locations accumulator for generated Location resources
     */
    private void traverseBoundary(EnrichedBoundary current, Location parentLocation, List<Location> locations) {
        Location location = buildLocationFromHierarchyRelation(current, parentLocation);
        locations.add(location);

        if (current.getChildren() != null) {
            for (EnrichedBoundary child : current.getChildren()) {
                traverseBoundary(child, location, locations);
            }
        }
    }
}
