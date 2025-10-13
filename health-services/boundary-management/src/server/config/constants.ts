import Error from "../config/error.interface";

export const CONSTANTS: any = {
    ERROR_CODES: {
        COMMON: {
            UNKNOWN_ERROR: "Unknown error. Check logs",
            VALIDATION_ERROR: "Validation error",
            INTERNAL_SERVER_ERROR: "Internal server error",
            KAFKA_ERROR: "Some error occured in kafka",
            SCHEMA_ERROR: " Schema related error",
        },
        FILE: {
            INVALID_FILE: "No download URL returned for the given fileStoreId",
            FETCHING_COLUMN_ERROR: "Error fetching Column Headers From Schema",
        },
        BOUNDARY: {
            BOUNDARY_SHEET_HEADER_ERROR: "Boundary sheet header error",
            BOUNDARY_RELATIONSHIP_CREATE_ERROR: "Some error occured during boundary relationship creation",
            BOUNDARY_SHEET_UPLOADED_INVALID_ERROR: "Error in the boundary data uploaded",
            BOUNDARY_SHEET_FIRST_COLUMN_INVALID_ERROR: "First Column Of Boundary Sheet uploaded should be unique as it is the root of hierarchy"
        },
    }
}
const unknownError = "Unknown Error. Check Logs";



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