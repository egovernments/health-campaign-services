package org.egov.processor.config;

import org.springframework.stereotype.Component;

@Component
public class ErrorConstants {

    public static final String FILE_NOT_FOUND_CODE = "FILE_NOT_FOUND";
    public static final String FILE_NOT_FOUND_MESSAGE = "The file with ID %s was not found in the tenant %s";
    public static final String FILE_NOT_FOUND_TEMPLATE_IDENTIFIER_MESSAGE = "No file with the specified templateIdentifier found - ";
    public static final String FILE_NOT_FOUND_LOG = "File not found: {} in tenant: {}";

    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE = "NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT";
    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE = "Invalid or incorrect TenantId. No mdms data found for provided Tenant.";

    public static final String ERROR_WHILE_FETCHING_FROM_PLAN_SERVICE = "Exception occurred while fetching plan configuration from plan service ";

    public static final String ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_CODE = "ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS";
    public static final String ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_MESSAGE = "Exception occurred while updating additional details  : ";

    public static final String NOT_ABLE_TO_CONVERT_MULTIPARTFILE_TO_BYTESTREAM_CODE = "NOT_ABLE_TO_CONVERT_MULTIPARTFILE_TO_BYTESTREAM";
    public static final String NOT_ABLE_TO_CONVERT_MULTIPARTFILE_TO_BYTESTREAM_MESSAGE = "Not able to fetch byte stream from a multipart file";

    public static final String PROVIDED_KEY_IS_NOT_PRESENT_IN_JSON_OBJECT_CODE = "PROVIDED_KEY_IS_NOT_PRESENT_IN_JSON_OBJECT";
    public static final String PROVIDED_KEY_IS_NOT_PRESENT_IN_JSON_OBJECT_MESSAGE = "Key is not present in json object - ";

    public static final String EMPTY_HEADER_ROW_CODE = "EMPTY_HEADER_ROW";
    public static final String EMPTY_HEADER_ROW_MESSAGE = "The header row is empty for the given sheet";

    public static final String INPUT_NOT_FOUND_CODE = "INPUT_VALUE_NOT_FOUND";
    public static final String INPUT_NOT_FOUND_MESSAGE = "Input value not found: ";

    public static final String EXCEL_FILE_NOT_FOUND_CODE = "EXCEL_FILE_NOT_FOUND";
    public static final String EXCEL_FILE_NOT_FOUND_MESSAGE = "The specified file was not found ";

    public static final String INVALID_FILE_FORMAT_CODE = "INVALID_FILE_FORMAT";
    public static final String INVALID_FILE_FORMAT_MESSAGE = "The file format is not supported ";

    public static final String UNABLE_TO_CREATE_ADDITIONAL_DETAILS_CODE = "UNABLE_TO_CREATE_ADDITIONAL_DETAILS";
    public static final String UNABLE_TO_CREATE_ADDITIONAL_DETAILS_MESSAGE = "Unable to create additional details for facility creation.";

    public static final String NO_CENSUS_FOUND_FOR_GIVEN_DETAILS_CODE = "NO_PLAN_FOUND_FOR_GIVEN_DETAILS";
    public static final String NO_CENSUS_FOUND_FOR_GIVEN_DETAILS_MESSAGE = "Census records do not exists for the given details: ";

    public static final String NO_PLAN_FOUND_FOR_GIVEN_DETAILS_CODE = "NO_PLAN_FOUND_FOR_GIVEN_DETAILS";
    public static final String NO_PLAN_FOUND_FOR_GIVEN_DETAILS_MESSAGE = "Plan records do not exists for the given details: ";

    public static final String NO_PLAN_FACILITY_FOUND_FOR_GIVEN_DETAILS_CODE = "NO_PLAN_FACILITY_FOUND_FOR_GIVEN_DETAILS";
    public static final String NO_PLAN_FACILITY_FOUND_FOR_GIVEN_DETAILS_MESSAGE = "Plan facilities do not exists for the given details. ";

    public static final String README_SHEET_NAME_LOCALISATION_NOT_FOUND_CODE = "README_SHEET_NAME_LOCALISATION_NOT_FOUND";
    public static final String README_SHEET_NAME_LOCALISATION_NOT_FOUND_MESSAGE = "Constant defined for error message when the README sheet name localization is not found or plan facilities do not exist for the provided details.";

    public static final String NO_MDMS_DATA_FOUND_FOR_MIXED_STRATEGY_MASTER_CODE = "NO_MDMS_DATA_FOUND_FOR_MIXED_STRATEGY_MASTER";
    public static final String NO_MDMS_DATA_FOUND_FOR_MIXED_STRATEGY_MASTER_CODE_MESSAGE = "Master data not found for Mixed Strategy master";

    public static final String BOUNDARY_CODE = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE";
    public static final String TOTAL_POPULATION = "HCM_ADMIN_CONSOLE_TOTAL_POPULATION";

    public static final String ERROR_PROCESSING_DATA_FROM_MDMS = "Exception occurred while processing data from mdms ";
    public static final String ERROR_WHILE_FETCHING_FROM_PLAN_SERVICE_FOR_LOCALITY = "Exception occurred while fetching plan configuration from plan service for Locality ";
    public static final String ERROR_WHILE_PUSHING_TO_PLAN_SERVICE_FOR_LOCALITY = "Exception occurred while fetching plan configuration from plan service for Locality ";
    public static final String ERROR_WHILE_SEARCHING_CAMPAIGN = "Exception occurred while searching/updating campaign.";
    public static final String ERROR_WHILE_DATA_CREATE_CALL = "Exception occurred while creating data for campaign - ";
    public static final String ERROR_WHILE_CALLING_MICROPLAN_API =
            "Unexpected error while calling fetch from Microplan API for plan config Id: ";
    public static final String INVALID_HEX = "Invalid hex color specified: ";
    public static final String ERROR_PROCESSING_EXCEL_FILE = "Exception occurred while processing excel file ";
    public static final String ERROR_CONVERTING_TO_EXCEL_FILE = "Exception occurred while converting workbook to XLS: ";
    public static final String ERROR_SAVING_EXCEL_FILE = "Exception occurred while saving XLS file: ";

    public static final String WORKBOOK_READ_ERROR_CODE = "WORKBOOK_READ_ERROR";
    public static final String WORKBOOK_READ_ERROR_MESSAGE = "Failed to read the workbook from file.";
}