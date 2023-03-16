package org.egov.household;

public interface Constants {
    String VALIDATION_ERROR = "VALIDATION_ERROR";

    String GET_ID = "getId";

    String GET_INDIVIDUAL_ID = "getIndividualId";

    String GET_INDIVIDUAL_CLIENT_REFERENCE_ID = "getIndividualClientReferenceId";

    String GET_HOUSEHOLD_MEMBERS = "getHouseholdMembers";

    String SET_HOUSEHOLD_MEMBERS = "setHouseholdMembers";

    String INVALID_HOUSEHOLD = "INVALID_HOUSEHOLD";

    String INVALID_HOUSEHOLD_MESSAGE = "invalid household id";

    String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    String INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD = "INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD";

    String INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD_MESSAGE = "individual is already member od household";

    String INDIVIDUAL_NOT_FOUND = "INDIVIDUAL_NOT_FOUND";

    String HOUSEHOLD_ALREADY_HAS_HEAD = "HOUSEHOLD_ALREADY_HAS_HEAD";

    String HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE = "household already has head";

    String INDIVIDUAL_NOT_FOUND_MESSAGE = "individual id is not found";

    String GET_HOUSEHOLDS = "getHouseholds";

    String SET_HOUSEHOLDS = "setHouseholds";

    String INDIVIDUAL_CANNOT_BE_NULL = "INDIVIDUAL_CANNOT_BE_NULL";

    String INDIVIDUAL_CANNOT_BE_NULL_MESSAGE = "individual id and individual client reference id both cannot be null";

}
