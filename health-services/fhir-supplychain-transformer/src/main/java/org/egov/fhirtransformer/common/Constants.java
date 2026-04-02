package org.egov.fhirtransformer.common;

import org.springframework.stereotype.Component;

@Component
public class Constants {

    //  Boundary Related Constants
    public static final String PROFILE_DIGIT_HCM_BOUNDARY = "https://digit.org/fhir/StructureDefinition/DIGITHCMBoundary";
    public static final String IDENTIFIER_SYSTEM_BOUNDARY = "https://digit.org/fhir/boundarymasterdata";
    public static final String LOCATION_TYPE_SYSTEM = "https://digit.org/CodeSystem/DIGITHCM.Location.Types";
    public static final String LOCATION_TYPE_JURISDICTION = "jurisdiction";

    //  Facility Related Constants
    public static final String PROFILE_DIGIT_HCM_FACILITY = "https://digit.org/fhir/StructureDefinition/DIGITHCMFacilityLocation";
    public static final String IDENTIFIER_SYSTEM_FACILITY = "https://digit.org/fhir/facilityid";
    public static final String FACILITY_USAGE_SYSTEM = "http://digit.org/fhir/CodeSystem/facilityUsage";
    public static final String FACILITY_LOCATION_TYPE = "facility";
    public static final String FACILITY_ID_SYSTEM = "https://digit.org/fhir/facilityid";

    // Product Variant Related Constants
    public static final String PROFILE_DIGIT_HCM_PV = "https://digit.org/fhir/StructureDefinition/DIGITHCMInventoryItem";
    public static final String IDENTIFIER_SYSTEM_PV = "http://digit.org/fhir/productVariant-identifier";
    public static final String IDENTIFIER_SYSTEM_SKUPV = "http://digit.org/fhir/productVariantSku-identifier";
    public static final String IDENTIFIER_SYSTEM_PRDCT = "http://digit.org/fhir/productID";
    public static final String CATEGORY_SYSTEM_PV = "http://digit.org/fhir/CodeSystem/ProductVariant-Producttype";
    public static final String NAMETYPE_SYSTEM_PV = "http://hl7.org/fhir/inventoryitem-nametype";
    public static final String RESPORG_SYSTEM_PV = "http://digit.org/fhir/CodeSystem/responsibleOrganization-role";
    public static final String PRODUCT_VARIANT_IDENTIFIER_SYSTEM = "https://digit.org/fhir/productVariant-identifier";
    public static final String GTIN_PV = "https://www.gs1.org";
    public static final String TRADENAME_PV = "trade-name";
    public static final String COMMONNAME_PV = "alias";
    public static final String MANUFACTURER_PV = "manufacturer";

    // Supply / Stock Constants
    public static final String IDENTIFIER_SYSTEM_WAYBILL = "http://digit.org/fhir/identifier/waybill";
    public static final String SD_CONDITION_URL = "https://digit.org/fhir/StructureDefinition/supplydelivery-condition";
    public static final String SD_STAGE_URL = "https://digit.org/fhir/StructureDefinition/SupplyDeliveryStage";
    public static final String EVENT_LOCATION_URL ="http://hl7.org/fhir/StructureDefinition/event-location";
    public static final String SD_SENDER_LOCATION_URL = "https://digit.org/fhir/StructureDefinition/supplydelivery-sender-location";
    public static final String TRANSACTION_REASON_SYSTEM = "https://digit.org/fhir/CodeSystem/transactionReason";
    public static final String TRANSACTION_TYPE_SYSTEM = "http://digit.org/fhir/CodeSystem/transactiontype";
    public static final String UOM_SYSTEM = "https://digit.org/CodeSystem/units";


    // ----- API PATH VALUES
    public static final String STOCKS_API_PATH= "/fetchAllStocks";
    public static final String STOCK_RECONCILIATION_API_PATH= "/fetchAllStockReconciliation";
    public static final String FACILITIES_API_PATH= "/fetchAllFacilities";
    public static final String PRODUCT_VARIANT_API_PATH= "/fetchAllProductVariants";


    // Pagination / Query Constants
    public static final String SELF = "SELF";
    public static final String FIRST = "FIRST";
    public static final String NEXT = "NEXT";
    public static final String SET_OFFSET = "&_offset=";
    public static final String FIRST_OFFSET = "&_offset=0";
    public static final String SET_TENANT_ID = "&tenantId=";
    public static final String SET_LIMIT = "?limit=";

    // Misc / Processing Constants
    public static final String LOCATION = "Location";
    public static final String INVENTORY_ITEM = "InventoryItem";
    public static final String LOCATION_PREFIX = "Location/";
    public static final String HIERARCHY_TYPE = "ADMIN";
    public static final boolean INCLUDE_CHILDREN = false;
    public static final String NEW_IDS = "newIDs";
    public static final String EXISTING_IDS = "existingIDs";
    public static final String TOTAL_PROCESSED = "totalProcessed";
    public static final Integer ROW_VERSION = 1;
}
