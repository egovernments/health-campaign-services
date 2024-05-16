import Error from "./error.interface"

export const CONSTANTS: any = {
    ERROR_CODES: {
        COMMON: {
            UNKNOWN_ERROR: "Unknown Error. Check Logs",
            IDGEN_ERROR: "Error during generating campaign number",
            VALIDATION_ERROR: "Validation Error",
            INTERNAL_SERVER_ERROR: "Internal Server Error",
            INVALID_PAGINATION: "Invalid pagination",
        },
        FILE: {
            INVALID_FILE: "No download URL returned for the given fileStoreId",
            INVALID_SHEETNAME: "Invalid Sheet Name",
            STATUS_FILE_CREATION_ERROR: "Error in Creating Status File",
            FETCHING_SHEET_ERROR: "Error occured while fetching sheet data",
            INVALID_FILE_ERROR: "Invalid File",
            DOWNLOAD_URL_NOT_FOUND: "Not any download URL returned for the given fileStoreId",
            INVALID_FILE_FORMAT: "The uploaded file is not a valid Excel file (xlsx or xls).",
            INVALID_COLUMNS: "Columns are invalid"
        },
        FACILITY: {
            FACILITY_SEARCH_FAILED: "Search failed for Facility. Check Logs",
        },
        CAMPAIGN: {
            CAMPAIGN_SEARCH_ERROR: "Error in Campaign Search",
            CAMPAIGNNAME_MISMATCH: "CampaignName is not matching",
            CAMPAIGN_NOT_FOUND: "Campaign not found",
            GENERATION_REQUIRE: "First Generate then Download",
            RESOURCE_CREATION_ERROR: "Some error occured during resource creation",
            CAMPAIGN_NAME_ERROR: "Campaign name already exists"
        },
        BOUNDARY: {
            BOUNDARY_DATA_NOT_FOUND: "No boundary data found in the system.",
            BOUNDARY_HIERARCHY_INSERT_ERROR: "Insert boundary hierarchy level wise",
            BOUNDARY_SEARCH_ERROR: "Error in Boundary Search. Check Boundary codes",
            BOUNDARY_NOT_FOUND: "Boundary not found",
            BOUNDARY_SHEET_HEADER_ERROR: "Boundary Sheet Header Error",
            BOUNDARY_ENTITY_CREATE_ERROR: "Some error occured during boundary entity creation",
            BOUNDARY_RELATIONSHIP_CREATE_ERROR: "Some error occured during boundary relationship creation",
            BOUNDARY_TARGET_ERROR: "Target either not present or invalid value"
        },
        PROJECT: {
            PROJECT_CREATION_FAILED: "Error occured in project creation",
            PROJECT_SEARCH_ERROR: "Error occured during project search , Check projectId",
            PROJECT_UPDATE_ERROR: "Error occured during project update , Check projectId",
            PROJECT_CREATION_ERROR: "Some error occured during project creation",
        },
        MDMS: {
            INVALID_README_CONFIG: "Invalid readme config"
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

export const userRoles: any = {
    "Registrar": "REGISTRAR",
    "Distributor": "DISTRIBUTOR",
    "Supervisor": "SUPERVISOR",
    "Help Desk": "HELPDESK_USER",
    "Monitor Local": "MONITOR_LOCAL",
    "Logistical officer": "LOGISTICAL_OFFICER",
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
    inprogress: "In Progress",
    failed: "failed",
    completed: "Completed",
    expired: "expired"
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
