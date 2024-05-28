package org.egov.transformer;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public interface Constants {
    String PROJECT_TYPES = "projectTypes";
    String INDIVIDUAL = "INDIVIDUAL";
    String HOUSEHOLD = "HOUSEHOLD";
    String CYCLE_NUMBER = "cycleIndex";
    String DOSE_NUMBER = "doseIndex";
    List<String> NON_BALES_ADDITIONAL_FIELDS_KEYS = new ArrayList<>(Arrays.asList(
            "vehicle_number", "waybill_quantity", "transport_type", "lat", "lng"
    ));
    List<String> BALES_COMMENTS_KEYS = new ArrayList<>(Arrays.asList(
            "comments", "baleMismtachComments", "manualScanComments"
    ));
    String BALES_QUANTITY = "bales_quantity";
    String MANUAL_SCAN = "manualScan";
    String MANUAL_SCANS_INDEX_KEY = "manualScans";
    String ACTUAL_BALE_SCANS_INDEX_KEY = "actualBaleScans";
    String  BALES_QUANTITY_INDEX_KEY = "balesQuantity";
    String DELIVERY_STRATEGY = "deliveryStrategy";

    String QUANTITY_WASTED = "quantityWasted";

    Double RE_ADMINISTERED_DOSES = 2.0;

    String BENEFICIARY_REFERRED = "BENEFICIARY_REFERRED";
    String TASK_STATUS = "taskStatus";
    String PRODUCT_VARIANT_ID = "productVariantId";
    String ADMINISTRATION_NOT_SUCCESSFUL = "ADMINISTRATION_NOT_SUCCESSFUL";

    String PROJECT_STAFF_ROLES = "projectStaffRoles";

    String MDMS_RESPONSE = "MdmsRes";

    String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    String TRANSFORMER_LOCALIZATIONS = "transformerLocalizations";

    String PROJECT = "PROJECT";
    String WAREHOUSE = "WAREHOUSE";
    String FACILITY = "FACILITY";
    String DISTRICT_WAREHOUSE = "DISTRICT_WAREHOUSE";
    String SATELLITE_WAREHOUSE = "SATELLITE_WAREHOUSE";
    String TYPE_KEY = "type";
    String FACILITY_TARGET_KEY = "target";
    String FIELD_TARGET = "targets";
    String BENEFICIARY_TYPE = "beneficiaryType";
    String TOTAL_NO_CHECK = "totalNo";
    String TARGET_NO_CHECK = "targetNo";
    String HYPHEN = "-";
    String LEVEL = "level";
    String LABEL = "label";
    String INDEX_LABEL = "indexLabel";
    String BOUNDARY_HIERARCHY = "boundaryHierarchy";
    String BOUNDARY_DATA = "boundaryData";
    String ID = "id";
    String COMMA = ",";
    String TIME_STAMP_SPLIT = "T";
    Long DAY_MILLIS = 86400000L;
    String AGE = "age";
    String DATE_OF_BIRTH = "dateOfBirth";
    String GENDER = "gender";
    String USERNAME = "userName";
    String NAME = "name";
    String ROLE = "role";

    String CITY = "city";
    String INDIVIDUAL_ID = "individualId";
    String ADDRESS_CODE = "addressLocalityCode";
    String CHILDREN_PRESENTED_US = "childrenPresentedUS";
    String MALARIA_POSITIVE_US = "malariaPositiveUS";
    String MALARIA_NEGATIVE_US = "malariaNegativeUS";
    String CHILDREN_PRESENTED_APE = "childrenPresentedAPE";
    String MALARIA_POSITIVE_APE = "malariaPositiveAPE";
    String MALARIA_NEGATIVE_APE = "malariaNegativeAPE";
    String DEFAULT_FACILITY_NAME = "APS";
    String START_DATE = "startDate";
    String END_DATE = "endDate";
    String CYCLES = "cycles";
    String DELIVERIES = "deliveries";
    String RECEIVED = "received";
    String RETURNED = "returned";
    String ISSUED = "issued";
    String LOST = "lost";
    String GAINED = "gained";
    String DAMAGED = "damaged";
    String INHAND = "inHand";

}
