package org.egov.household;

public interface Constants {
    String VALIDATION_ERROR = "VALIDATION_ERROR";

    String GET_ID = "getId";

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

    String HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD = "HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD";

    String HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD_MESSAGE = "household has more than one head";

    String HOUSEHOLD_DOES_NOT_HAVE_A_HEAD = "HOUSEHOLD_DOES_NOT_HAVE_A_HEAD";

    String HOUSEHOLD_DOES_NOT_HAVE_A_HEAD_MESSAGE = "Household does not have a head";

    String HOUSEHOLD_HEAD_CANNOT_BE_UNASSIGNED = "HOUSEHOLD_HEAD_CANNOT_BE_UNASSIGNED";

    String HOUSEHOLD_HEAD_CANNOT_BE_UNASSIGNED_MESSAGE = "Household head cannot be unassigned";


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

    String TENANT_ID_EXCEPTION_MESSAGE = "tenant id cannot be null or empty";
}
