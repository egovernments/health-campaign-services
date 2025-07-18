package org.egov.individual;

public interface Constants {
    String SET_INDIVIDUALS = "setIndividuals";
    String VALIDATION_ERROR = "VALIDATION_ERROR";
    String SYSTEM_GENERATED = "SYSTEM_GENERATED";
    String GET_INDIVIDUALS = "getIndividuals";
    String GET_SKILLS = "getSkills";
    String GET_IDENTIFIERS = "getIdentifiers";
    String GET_ADDRESS = "getAddress";
    String GET_IDENTIFIER_TYPE = "getIdentifierType";
    String GET_TYPE = "getType";
    String GET_ID = "getId";
    String INDIVIDUAL_CREATE_LOCALIZATION_CODE = "INDIVIDUAL_NOTIFICATION_ON_CREATE";
    String INDIVIDUAL_UPDATE_LOCALIZATION_CODE = "INDIVIDUAL_NOTIFICATION_ON_UPDATE";
    String INDIVIDUAL_NOTIFICATION_ENG_LOCALE_CODE = "en_IN";
    String INDIVIDUAL_MODULE_CODE = "rainmaker-masters";
    String INDIVIDUAL_LOCALIZATION_CODES_JSONPATH = "$.messages.*.code";
    String INDIVIDUAL_LOCALIZATION_MSGS_JSONPATH = "$.messages.*.message";
    String ORG_ADMIN_ROLE_CODE = "ORG_ADMIN";
    String INVALID_TENANT_ID = "INVALID_TENANT_ID";
    String INVALID_TENANT_ID_MSG = "Tenant ID is not valid";
    String UNIQUE_BENEFICIARY_ID = "UNIQUE_BENEFICIARY_ID";
    String INVALID_BENEFICIARY_ID = "INVALID_BENEFICIARY_ID";
    String INVALID_USER_ID = "INVALID_USER_ID";
    String BENEFICIARY_ID_ALREADY_ASSIGNED = "BENEFICIARY_ID_ALREADY_ASSIGNED";
}
