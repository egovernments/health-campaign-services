package org.egov.transformer;

public interface Constants {
    String PROJECT_TYPES = "projectTypes";
    String INDIVIDUAL = "INDIVIDUAL";
    String HOUSEHOLD = "HOUSEHOLD";
    String CYCLE_INDEX = "cycleIndex";
    String DOSE_INDEX = "doseIndex";
    String DELIVERY_STRATEGY = "deliveryStrategy";

    String NULL_STRING = "null";
    String PREFIX_ZERO = "0";

    String QUANTITY_WASTED = "quantityWasted";

    Long RE_ADMINISTERED_DOSES = 2L;
    String RE_DOSE_QUANTITY_KEY = "reDoseQuantity";

    String BENEFICIARY_REFERRED = "BENEFICIARY_REFERRED";
    String TASK_STATUS = "taskStatus";
    String PRODUCT_VARIANT_ID = "productVariantId";
    String MEMBER_COUNT = "memberCount";
    String HOUSEHOLD_ID = "householdId";
    String ADMINISTRATION_NOT_SUCCESSFUL = "ADMINISTRATION_NOT_SUCCESSFUL";

    String PROJECT_STAFF_ROLES = "projectStaffRoles";

    String MDMS_RESPONSE = "MdmsRes";

    String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    String TRANSFORMER_LOCALIZATIONS = "transformerLocalizations";
    String TRANSFORMER_ELASTIC_INDEX_LABELS = "transformerElasticIndexLabels";
    String PROJECT = "PROJECT";
    String WAREHOUSE = "WAREHOUSE";
    String FACILITY = "FACILITY";
    String DISTRICT_WAREHOUSE = "DISTRICT_WAREHOUSE";
    String SATELLITE_WAREHOUSE = "SATELLITE_WAREHOUSE";
    String PARTIAL_BLISTERS_RETURNED = "partialBlistersReturned";
    String WASTED_BLISTERS_RETURNED = "wastedBlistersReturned";
    String TYPE_KEY = "type";
    String FACILITY_TARGET_KEY = "target";
    String FIELD_TARGET = "targets";
    String BENEFICIARY_TYPE = "beneficiaryType";
    String TOTAL_NO_CHECK = "totalNo";
    String TARGET_NO_CHECK = "targetNo";
    String HYPHEN = "-";
    String COLON = ":";
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
    String HEIGHT = "height";
    String DISABILITY_TYPE = "disabilityType";
    String LOCALIZATION_CODES_JSONPATH = "$.messages.*.code";
    String LOCALIZATION_MSGS_JSONPATH = "$.messages.*.message";
    String PREGNANTWOMEN = "pregnantWomen";
    String CHILDREN = "children";
    String ISVULNERABLE = "isVulnerable";
    String CLOSED_HOUSEHOLD ="CLOSED_HOUSEHOLD";
    String REASON_OF_REFUSAL = "reasonOfRefusal";
    String NO_OF_ROOMS= "noOfRooms";
    String MEN_COUNT = "menCount";
    String WOMEN_COUNT = "womenCount";
    String PROJECT_ID = "projectId";
    String PROJECT_TYPE_ID = "projectTypeId";
    String PROJECT_TARGET_NUMBER_TYPE_PER_DAY = "PER_DAY";
    String PROJECT_TARGET_NUMBER_TYPE_OVERALL = "OVERALL";
    String NO_OF_ROOMS_SPRAYED_KEY = "noOfRoomsSprayedKey";
    String LAT = "lat";
    String LNG = "lng";
    String STAFF = "STAFF";
    String BOUNDARY_CODE_KEY = "boundaryCode";

    String KEY_VALUE = "keyValue";
    String VALUE_TYPE = "valueType";
    String VALUE = "value";
    String INTEGER = "Integer";
    String STRING = "String";
    String DOUBLE = "Double";
    String LONG = "Long";
    String BOOLEAN = "Boolean";

    String INDIVIDUAL_CLIENT_REFERENCE_ID = "individualClientReferenceId";
    String HOUSEHOLD_CLIENT_REFERENCE_ID = "householdClientReferenceId";
    String UNIQUE_BENEFICIARY_ID = "uniqueBeneficiaryId";

    // User Action Constants
    String USER_ACTION_DAILY_PLAN = "USER_ACTION_DAILY_PLAN";
    String HCM_MODULE = "HCM";
    String SUPERVISOR_ROLE_KEY = "SupervisorRole";
    String SUB_BOUNDARY_TYPE_KEY = "SubBoundaryType";
    String BOUNDARY_TYPE_KEY = "BoundaryType";
    
    // User Action Additional Details Keys
    String SUPERVISOR_ROLE = "supervisorRole";
    String SUB_BOUNDARY_TYPE = "subBoundaryType";
    String BOUNDARY_TYPE = "boundaryType";

    String DAY_OF_VISIT = "dayOfVisit";
    String VISITED_BOUNDARIES_SUFFIX = "_visited_boundaries";

}
