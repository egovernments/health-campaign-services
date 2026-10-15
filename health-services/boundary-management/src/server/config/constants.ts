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
            BOUNDARY_SHEET_FIRST_COLUMN_INVALID_ERROR: "First Column Of Boundary Sheet uploaded should be unique as it is the root of hierarchy",
            // Registered so these validation rejections surface a clear message and their real
            // code/status instead of falling back to "Unknown Error. Check Logs" (Issue 4).
            MIXED_BOUNDARY_FLOW: "All rows must either provide boundary service codes or leave them all empty — mixed files are not supported. Provide a code for every boundary, or for none.",
            MISSING_BOUNDARY_CODE: "A boundary service code is missing. In manual flow every boundary must have a service code.",
            MISSING_PARENT_BOUNDARY_CODE: "One or more parent/intermediate boundaries are missing service codes. In manual flow all boundaries must have service codes.",
            DUPLICATE_BOUNDARY_CODE: "Duplicate boundary service codes were provided. Each boundary service code must be unique.",
            FLOW_MISMATCH_ERROR: "This hierarchy was created with a different code flow (auto vs manual). Continue with the same flow that was used for the existing boundaries."
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