export const processTemplateConfigs: any = {
    user: {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                // schemaName: "user-readme",
                lockWholeSheet : true
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_USER_LIST",
                validateRowsGap : true,
                schemaName: "user"
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
                // schemaName: "boundary-data",
                lockWholeSheet : true
            }
        ]
    },
    facility: {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                // schemaName: "user-readme",
                lockWholeSheet : true
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_FACILITIES",
                validateRowsGap : true,
                schemaName: "facility"
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
                // schemaName: "boundary-data",
                lockWholeSheet : true
            }
        ]
    },
    boundary: {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                // schemaName: "user-readme",
                lockWholeSheet : true
            }
        ],
        enrichmentFunction: "enrichTargetProcessConfig"
    },
    userValidation : {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                // schemaName: "user-readme",
                lockWholeSheet: true
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_USER_LIST",
                lockWholeSheet: true,
                validateRowsGap: true,
                schemaName: "user"
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
                // schemaName: "boundary-data",
                lockWholeSheet: true
            }
        ],
        passFromController : true
    },
    facilityValidation: {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                // schemaName: "user-readme",
                lockWholeSheet: true
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_FACILITIES",
                lockWholeSheet: true,
                validateRowsGap: true,
                schemaName: "facility"
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
                // schemaName: "boundary-data",
                lockWholeSheet: true
            }
        ],
        passFromController : true
    },
    boundaryValidation : {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                // schemaName: "user-readme",
                lockWholeSheet: true
            }
        ],
        enrichmentFunction: "enrichTargetProcessConfig",
        passFromController : true
    }
}