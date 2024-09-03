package org.egov.transformer;

import org.apache.kafka.common.protocol.types.Field;

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
    String HF_TESTED_FOR_MALARIA = "testedForMalaria";
    String HF_MALARIA_RESULT = "malariaResult";
    String HF_ADMITTED_WITH_ILLNESS = "admittedWithSeriousIllness";
    String HF_NEGATIVE_ADMITTED_WITH_ILLNESS = "negativeAndAdmittedWithSeriousIllness";
    String HF_TREATED_WITH_ANTI_MALARIALS = "treatedWithAntiMalarials";
    String HF_NAME_OF_ANTI_MALARIALS = "nameOfAntiMalarials";
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
}
