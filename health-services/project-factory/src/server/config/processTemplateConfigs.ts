export const processTemplateConfigs: any = {
    user: {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                lockWholeSheetInProcessedFile : true
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_USER_LIST",
                schemaName: "user"
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
                lockWholeSheetInProcessedFile : true
            }
        ]
    }
}