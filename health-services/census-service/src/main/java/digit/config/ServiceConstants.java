package digit.config;


import org.springframework.stereotype.Component;


@Component
public class ServiceConstants {

    public static final String EXTERNAL_SERVICE_EXCEPTION = "External Service threw an Exception: ";
    public static final String SEARCHER_SERVICE_EXCEPTION = "Exception while fetching from searcher: ";

    public static final String IDGEN_ERROR = "IDGEN ERROR";
    public static final String NO_IDS_FOUND_ERROR = "No ids returned from idgen Service";

    public static final String ERROR_WHILE_FETCHING_FROM_MDMS = "Exception occurred while fetching category lists from mdms: ";

    public static final String ERROR_WHILE_FETCHING_BOUNDARY_DETAILS = "Exception occurred while fetching boundary relationship from boundary service: ";

    public static final String ERROR_WHILE_FETCHING_EMPLOYEE_ASSIGNMENT_DETAILS = "Exception occurred while fetching plan employee assignment details from plan service: ";

    public static final String RES_MSG_ID = "uief87324";
    public static final String SUCCESSFUL = "successful";
    public static final String FAILED = "failed";

    public static final String URL = "url";
    public static final String URL_SHORTENING_ERROR_CODE = "URL_SHORTENING_ERROR";
    public static final String URL_SHORTENING_ERROR_MESSAGE = "Unable to shorten url: ";

    public static final String DOB_FORMAT_Y_M_D = "yyyy-MM-dd";
    public static final String DOB_FORMAT_D_M_Y = "dd/MM/yyyy";
    public static final String ILLEGAL_ARGUMENT_EXCEPTION_CODE = "IllegalArgumentException";
    public static final String OBJECTMAPPER_UNABLE_TO_CONVERT = "ObjectMapper not able to convertValue in userCall";
    public static final String DOB_FORMAT_D_M_Y_H_M_S = "dd-MM-yyyy HH:mm:ss";
    public static final String CREATED_DATE = "createdDate";
    public static final String LAST_MODIFIED_DATE = "lastModifiedDate";
    public static final String DOB = "dob";
    public static final String PWD_EXPIRY_DATE = "pwdExpiryDate";
    public static final String INVALID_DATE_FORMAT_CODE = "INVALID_DATE_FORMAT";
    public static final String INVALID_DATE_FORMAT_MESSAGE = "Failed to parse date format in user";
    public static final String CITIZEN_UPPER = "CITIZEN";
    public static final String CITIZEN_LOWER = "Citizen";
    public static final String USER = "user";
    public static final String PIPE_REGEX = "\\|";
    public static final String FACILITY_ID_FIELD = "facilityId";

    public static final String PARSING_ERROR_CODE = "PARSING ERROR";
    public static final String PARSING_ERROR_MESSAGE = "Failed to parse JSON data from PGobject";

    public static final String FAILED_TO_PARSE_BUSINESS_SERVICE_SEARCH = "Failed to parse response of workflow business service search";
    public static final String BUSINESS_SERVICE_NOT_FOUND = "BUSINESSSERVICE_NOT_FOUND";
    public static final String THE_BUSINESS_SERVICE = "The businessService ";
    public static final String NOT_FOUND = " is not found";
    public static final String TENANTID = "?tenantId=";
    public static final String BUSINESS_SERVICES = "&businessServices=";

    public static final String NO_BOUNDARY_DATA_FOUND_FOR_GIVEN_BOUNDARY_CODE_CODE = "NO_BOUNDARY_DATA_FOUND_FOR_GIVEN_BOUNDARY_CODE";
    public static final String NO_BOUNDARY_DATA_FOUND_FOR_GIVEN_BOUNDARY_CODE_MESSAGE = "Invalid or incorrect boundaryCode. No boundary data found.";

    public static final String USERINFO_MISSING_CODE = "USERINFO_MISSING";
    public static final String USERINFO_MISSING_MESSAGE = "UserInfo is missing in Request Info ";

    public static final String ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_CODE = "ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS";
    public static final String ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_MESSAGE = "Exception occurred while updating additional details  : ";

    public static final String WORKFLOW_INTEGRATION_ERROR_CODE = "WORKFLOW_INTEGRATION_ERROR";
    public static final String WORKFLOW_INTEGRATION_ERROR_MESSAGE = "Exception occured while integrating with workflow : ";

    public static final String INVALID_PARTNER_CODE = "INVALID_PARTNER";
    public static final String INVALID_PARTNER_MESSAGE = "Invalid partner assignment or invalid jurisdiction of the assigned partner";

    public static final String INVALID_CENSUS_CODE = "INVALID_CENSUS";
    public static final String INVALID_CENSUS_MESSAGE = "Provided census does not exist";

    public static final String DUPLICATE_CENSUS_ID_IN_BULK_UPDATE_CODE = "DUPLICATE_CENSUS_ID_IN_BULK_UPDATE";
    public static final String DUPLICATE_CENSUS_ID_IN_BULK_UPDATE_MESSAGE = "Census provided in the bulk update request are not unique.";

    public static final String INVALID_SOURCE_OR_TENANT_ID_FOR_BULK_UPDATE_CODE = "INVALID_SOURCE_OR_TENANT_ID_FOR_BULK_UPDATE";
    public static final String INVALID_SOURCE_OR_TENANT_ID_FOR_BULK_UPDATE_MESSAGE = "Tenant id and source should be same across all entries for bulk update.";

    public static final String WORKFLOW_NOT_FOUND_FOR_BULK_UPDATE_CODE = "WORKFLOW_NOT_FOUND_FOR_BULK_UPDATE";
    public static final String WORKFLOW_NOT_FOUND_FOR_BULK_UPDATE_MESSAGE = "Workflow information is mandatory for each entry for bulk update";

    public static final String DIFFERENT_WORKFLOW_FOR_BULK_UPDATE_CODE = "DIFFERENT_WORKFLOW_FOR_BULK_UPDATE";
    public static final String DIFFERENT_WORKFLOW_FOR_BULK_UPDATE_MESSAGE = "All entries should be in the same state for bulk transitioning census records.";

    public static final String SEARCH_CRITERIA_EMPTY_CODE = "SEARCH_CRITERIA_EMPTY";
    public static final String SEARCH_CRITERIA_EMPTY_MESSAGE = "Search criteria cannot be empty";

    public static final String TENANT_ID_EMPTY_CODE = "TENANT_ID_EMPTY";
    public static final String TENANT_ID_EMPTY_MESSAGE = "Tenant Id cannot be empty, TenantId should be present";

    //Workflow constants
    public static final String MODULE_NAME_VALUE = "census-service";

    public static final String CENSUS_BUSINESS_SERVICE = "CENSUS";
}
