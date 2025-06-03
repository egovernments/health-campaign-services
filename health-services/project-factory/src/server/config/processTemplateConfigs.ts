export const processTemplateConfigs: any = {
    user: {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                // schemaName: "user-readme",
                lockWholeSheetInProcessedFile : true
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_USER_LIST",
                schemaName: "user"
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
                // schemaName: "boundary-data",
                lockWholeSheetInProcessedFile : true
            }
        ]
    },
    facility: {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                // schemaName: "user-readme",
                lockWholeSheetInProcessedFile : true
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_FACILITIES",
                schemaName: "facility"
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
                // schemaName: "boundary-data",
                lockWholeSheetInProcessedFile : true
            }
        ]
    },
    boundary: {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                // schemaName: "user-readme",
                lockWholeSheetInProcessedFile : true
            }
        ],
        enrichmentFunction: "enrichTargetProcessConfig"
    }
}