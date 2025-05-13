// Configuration object for defining sheet templates for different modules (like user, boundary)
export const templateConfigs: any = {
    // Configuration for the 'user' module
    user: {
        sheets: [
            {
                // Sheet name to be used in the Excel file
                sheetName: "HCM_README_SHEETNAME",
                // Refers to the schema used to generate columns for this sheet
                schemaName: "user-readme"
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_USER_LIST",
                schemaName: "user"
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
                schemaName: "boundary-data"
            }
        ],
        // Enable processing of input Excel sheets into JSON
        processing: true,
        // Enable generation of Excel templates from schema
        generation: true
    },

    // Configuration for the 'boundary' module
    boundary: {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                schemaName: "target-readme"
            }
        ],
        processing: true,
        generation: true
    }
}
