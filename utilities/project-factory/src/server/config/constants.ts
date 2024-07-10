import Error from "./error.interface"

export const CONSTANTS: any = {
    ERROR_CODES: {
        COMMON: {
            UNKNOWN_ERROR: "Unknown error. Check logs",
            IDGEN_ERROR: "Error during generating campaign number",
            VALIDATION_ERROR: "Validation error",
            INTERNAL_SERVER_ERROR: "Internal server error",
            INVALID_PAGINATION: "Invalid pagination",
            KAFKA_ERROR: "Some error occured in kafka",
            SCHEMA_ERROR: " Schema related error",
            RESPONSE_NOT_FOUND_ERROR: "Response not found",
            GENERATE_ERROR: "Error while generating user/facility/boundary"
        },
        FILE: {
            INVALID_FILE: "No download URL returned for the given fileStoreId",
            INVALID_SHEETNAME: "Invalid sheet name",
            STATUS_FILE_CREATION_ERROR: "Error in creating status file",
            FETCHING_SHEET_ERROR: "Error occured while fetching sheet data",
            INVALID_FILE_ERROR: "Invalid file",
            DOWNLOAD_URL_NOT_FOUND: "Not any download URL returned for the given fileStoreId",
            INVALID_FILE_FORMAT: "The uploaded file is not a valid excel file (xlsx or xls).",
            INVALID_COLUMNS: "Columns are invalid",
            FETCHING_COLUMN_ERROR: "Error fetching Column Headers From Schema"
        },
        FACILITY: {
            FACILITY_SEARCH_FAILED: "Search failed for facility. Check logs",
        },
        CAMPAIGN: {
            CAMPAIGN_SEARCH_ERROR: "Error in campaign search",
            CAMPAIGNNAME_MISMATCH: "CampaignName is not matching",
            CAMPAIGN_NOT_FOUND: "Campaign not found",
            GENERATION_REQUIRE: "First generate then download",
            RESOURCE_CREATION_ERROR: "Some error occured during resource creation",
            CAMPAIGN_NAME_ERROR: "Campaign name already exists",
            CAMPAIGN_ALREADY_MAPPED: "Campaign is already mapped",
        },
        BOUNDARY: {
            BOUNDARY_DATA_NOT_FOUND: "No boundary data found in the system.",
            BOUNDARY_HIERARCHY_INSERT_ERROR: "Insert boundary hierarchy level wise",
            BOUNDARY_SEARCH_ERROR: "Error in boundary search. Check boundary codes",
            BOUNDARY_NOT_FOUND: "Boundary not found",
            BOUNDARY_SHEET_HEADER_ERROR: "Boundary sheet header error",
            BOUNDARY_ENTITY_CREATE_ERROR: "Some error occured during boundary entity creation",
            BOUNDARY_RELATIONSHIP_CREATE_ERROR: "Some error occured during boundary relationship creation",
            BOUNDARY_TARGET_ERROR: "Target either not present or invalid value",
            BOUNDARY_CONFIRMATION_FAILED: "Error in boundary creation and persistence"
        },
        PROJECT: {
            PROJECT_CREATION_FAILED: "Error occured in project creation",
            PROJECT_SEARCH_ERROR: "Error occured during project search , check projectId",
            PROJECT_UPDATE_ERROR: "Error occured during project update , check projectId",
            PROJECT_CREATION_ERROR: "Some error occured during project creation",
            PROJECT_CONFIRMATION_FAILED: "Error occured in project creation and peristence",
        },
        MDMS: {
            INVALID_README_CONFIG: "Invalid readme config",
            MDMS_DATA_NOT_FOUND_ERROR: "Mdms Data not present"
        }
    }
}

export const headingMapping: any = {
    "userWithBoundary": "USERWITHBOUNDARY_README_MAINHEADER",
    "facilityWithBoundary": "FACILITYWITHBOUNDARY_README_MAINHEADER",
    "boundary": "BOUNDARY_README_MAINHEADER"
}

const unknownError = "Unknown Error. Check Logs";


//  Retrieves the error message associated with the given error key.
const getMessage = (key: any) => {
    // Retrieve the error codes from the CONSTANTS object
    const errors = CONSTANTS.ERROR_CODES;

    // Iterate over each module and error key to find the matching error message
    for (const moduleKey in errors) {
        for (const errorKey in errors[moduleKey]) {
            if (key === errorKey) {
                return errors[moduleKey][errorKey];
            }
        }
    }

    // Return 'unknownError' if the error key is not found
    return unknownError;
}

export const campaignStatuses: any = {
    drafted: "drafted",
    started: "creating",
    inprogress: "created",
    failed: "failed"
}

export const resourceDataStatuses: any = {
    failed: "failed",
    completed: "completed",
    invalid: "invalid",
    started: "validation-started",
    accepted: "data-accepted"
}

export const generatedResourceStatuses: any = {
    inprogress: "inprogress",
    failed: "failed",
    completed: "completed",
    expired: "expired"
}

export const processTrackTypes = {
    validation: "validation",
    triggerResourceCreation: "triggerResourceCreation",
    facilityCreation: "facilityCreation",
    staffCreation: "staffCreation",
    targetAndDeliveryRulesCreation: "targetAndDeliveryRulesCreation",
    confirmingResouceCreation: "confirmingResouceCreation",
    prepareResourceForMapping: "prepareResourceForMapping",
    validateMappingResource: "validateMappingResource",
    staffMapping: "staffMapping",
    resourceMapping: "resourceMapping",
    facilityMapping: "facilityMapping",
    campaignCreation: "campaignCreation",
    error: "error"
}

export const processTrackStatuses = {
    inprogress: "inprogress",
    completed: "completed",
    toBeCompleted: "toBeCompleted",
    failed: "failed",
}


// Retrieves the error object containing the error code, message, and notFound flag.
export const getErrorCodes = (module: string, key: string): Error => {
    // Retrieve the error message from the CONSTANTS object
    const message = CONSTANTS.ERROR_CODES?.[module]?.[key] || getMessage(key)

    // Determine the error code based on whether the message is 'unknownError' or not
    const code = message == unknownError ? "UNKNOWN_ERROR" : key

    // Return the error object
    return {
        code: code,
        notFound: true,
        message: message
    }
}
