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
                validateRowsGap : true,
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
                validateRowsGap : true,
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
    },
    userValidation : {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                // schemaName: "user-readme",
                lockWholeSheetInProcessedFile: true
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_USER_LIST",
                lockWholeSheetInProcessedFile: true,
                validateRowsGap: true,
                schemaName: "user"
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
                // schemaName: "boundary-data",
                lockWholeSheetInProcessedFile: true
            }
        ]
    },
    facilityValidation: {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                // schemaName: "user-readme",
                lockWholeSheetInProcessedFile: true
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_FACILITIES",
                lockWholeSheetInProcessedFile: true,
                validateRowsGap: true,
                schemaName: "facility"
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
                // schemaName: "boundary-data",
                lockWholeSheetInProcessedFile: true
            }
        ]
    },
    boundaryValidation : {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                // schemaName: "user-readme",
                lockWholeSheetInProcessedFile: true
            }
        ],
        enrichmentFunction: "enrichTargetProcessConfig"
    }
}