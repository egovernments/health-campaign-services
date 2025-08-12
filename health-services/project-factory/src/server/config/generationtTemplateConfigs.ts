// Configuration object for defining sheet templates for different modules (like user, boundary)

export const generationtTemplateConfigs : any = {
    // Configuration for the 'user' module
    user: {
        sheets: [
            {
                // Sheet name to be used in the Excel file
                sheetName: "HCM_README_SHEETNAME",
                // Refers to the schema used to generate columns for this sheet
                schemaName: "user-readme",
                lockWholeSheet: true
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_USER_LIST",
                schemaName: "user"
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
                schemaName: "boundary-data",
                lockWholeSheet: true
            }
        ],
    },
    userCredential: {
        sheets: [
            {
                sheetName: "HCM_ADMIN_CONSOLE_USER_LIST",
                schemaName: "user",
                lockWholeSheet: true
            }
        ],
    },
    facility: {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                schemaName: "facility-readme",
                lockWholeSheet: true
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_FACILITIES",
                schemaName: "facility"
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
                schemaName: "boundary-data",
                lockWholeSheet: true
            }
        ],
    },

    // Configuration for the 'boundary' module
    boundary: {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                schemaName: "target-readme",
                lockWholeSheet: true
            }
        ]
    }
}
