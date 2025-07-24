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

    // Household and household member related constants
    String GET_CLIENT_REFERENCE_ID = "getClientReferenceId";
    String ID_FIELD = "id";
    String CLIENT_REFERENCE_ID_FIELD = "clientReferenceId";
    String GET_HOUSEHOLD_CLIENT_REFERENCE_ID = "getHouseholdClientReferenceId";
    String HOUSEHOLD_ID_FIELD = "householdId";
    String HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD = "householdClientReferenceId";
    String GET_INDIVIDUAL_ID = "getIndividualId";
    String GET_INDIVIDUAL_CLIENT_REFERENCE_ID = "getIndividualClientReferenceId";
    String GET_HOUSEHOLD_MEMBERS = "getHouseholdMembers";
    String SET_HOUSEHOLD_MEMBERS = "setHouseholdMembers";
    String INVALID_HOUSEHOLD = "INVALID_HOUSEHOLD";
    String INVALID_HOUSEHOLD_MESSAGE = "invalid household id";
    String INVALID_HOUSEHOLD_TYPE_MESSAGE = "invalid household type";
    String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    String INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD = "INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD";
    String INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD_MESSAGE = "individual is already member of household";
    String INDIVIDUAL_NOT_FOUND = "INDIVIDUAL_NOT_FOUND";
    String HOUSEHOLD_ALREADY_HAS_HEAD = "HOUSEHOLD_ALREADY_HAS_HEAD";
    String HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE = "household already has head";
    String INDIVIDUAL_NOT_FOUND_MESSAGE = "individual id is not found";
    String GET_HOUSEHOLDS = "getHouseholds";
    String SET_HOUSEHOLDS = "setHouseholds";
    String INDIVIDUAL_CANNOT_BE_NULL = "INDIVIDUAL_CANNOT_BE_NULL";
    String INDIVIDUAL_CANNOT_BE_NULL_MESSAGE = "individual id and individual client reference id both cannot be null";
    String INVALID_HOUSEHOLD_MEMBER_RELATIONSHIP = "INVALID_HOUSEHOLD_MEMBER_RELATIONSHIP";
    String INVALID_HOUSEHOLD_MEMBER_RELATIONSHIP_MESSAGE = "household member relationship type is not valid";
    String HOUSEHOLD_MEMBER_RELATIONSHIP_CONFIG_NOT_FOUND_MESSAGE = "MDMS config HCM.HOUSEHOLD_MEMBER_RELATIONSHIP_TYPES for household member is not found";
    String HOUSEHOLD_MEMBER_RELATIONSHIP_NOT_ALLOWED_MESSAGE = "household member relationship is not allowed";
    String TENANT_ID_EXCEPTION = "TENANT_ID_EXCEPTION";
}
