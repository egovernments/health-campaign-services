package org.egov.transformer;

import org.apache.kafka.common.protocol.types.Field;

public interface Constants {
    String PROJECT_TYPES = "projectTypes";
    String INDIVIDUAL = "INDIVIDUAL";
    String HOUSEHOLD = "HOUSEHOLD";
    String CYCLE_NUMBER = "cycleIndex";
    String DOSE_NUMBER = "doseIndex";
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
    String AGE = "age";
    String DATE_OF_BIRTH = "dateOfBirth";
    String GENDER = "gender";
    String USERNAME = "userName";
    String NAME = "name";
    String ROLE = "role";
    String INDIVIDUAL_ID = "individualId";
    String ADDRESS_CODE = "addressLocalityCode";
    String CHILDREN_PRESENTED_US = "childrenPresentedUS";
    String MALARIA_POSITIVE_US = "malariaPositiveUS";
    String MALARIA_NEGATIVE_US = "malariaNegativeUS";
    String CHILDREN_PRESENTED_APE = "childrenPresentedAPE";
    String MALARIA_POSITIVE_APE = "malariaPositiveAPE";
    String MALARIA_NEGATIVE_APE = "malariaNegativeAPE";
    String DEFAULT_FACILITY_NAME = "APS";

}
