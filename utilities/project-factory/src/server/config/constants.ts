import Error from "./error.interface"

export const CONSTANTS: any = {
    ERROR_CODES: {

        CAMPAIGN: {
            NO_MUSTER_ROLL_FOUND: "No Muster Roll Found for given Criteria",
            FACILITY_SEARCH_FAILED: "Search failed for Facility. Check Logs",
            UNKNOWN_ERROR: "Unknown Error. Check Logs",
            IDGEN_ERROR: "Error during generating campaign number",
            BOUNDARY_DATA_NOT_FOUND: "No boundary data found in the system.",
            GENERATION_REQUIRE: "First Generate then Download",
            INVALID_FILE: "No download URL returned for the given fileStoreId",
            STATUS_FILE_CREATION_ERROR: "Error in Creating Status File",
            PROJECT_CREATION_FAILED: "Error occured in project creation",
            INVALID_SHEETNAME: "Invalid Sheet Name",
            FETCHING_SHEET_ERROR: "Error occured while fetching sheet data",
            BOUNDARY_HIERARCHY_INSERT_ERROR: "Insert boundary hierarchy level wise",
            PROJECT_SEARCH_ERROR: "Error occured during project search , Check projectId",
            PROJECT_UPDATE_ERROR: "Error occured during project update , Check projectId",
            VALIDATION_ERROR: "Validation Error",
            INTERNAL_SERVER_ERROR: "Internal Server Error",
            INVALID_FILE_ERROR: "Invalid File",
            DOWNLOAD_URL_NOT_FOUND: "Not any download URL returned for the given fileStoreId",
            BOUNDARY_SEARCH_ERROR: "Error in Boundary Search. Check Boundary codes",
            BOUNDARY_NOT_FOUND: "Boundary not found",
            CAMPAIGN_SEARCH_ERROR: "Error in Campaign Search",
            CAMPAIGNNAME_MISMATCH: "CampaignName is not matching",
            CAMPAIGN_NOT_FOUND: "Campaign not found",
            INVALID_PAGINATION: "Invalid pagination",
            BOUNDARY_SHEET_HEADER_ERROR: "Boundary Sheet Header Error",
            PROJECT_CREATION_ERROR: "Some error occured during project creation",
            RESOURCE_CREATION_ERROR: "Some error occured during resource creation",
            BOUNDARY_ENTITY_CREATE_ERROR: "Some error occured during boundary entity creation",
            BOUNDARY_RELATIONSHIP_CREATE_ERROR: "Some error occured during boundary relationship creation"
        }

    }

}

export const getErrorCodes = (module: string, key: string): Error => {
    return {
        code: key,
        notFound: true,
        message: CONSTANTS.ERROR_CODES?.[module]?.[key]
    }
}