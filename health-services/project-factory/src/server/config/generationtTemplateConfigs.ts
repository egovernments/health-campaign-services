export const generationtTemplateConfigs : any = {
    user: {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
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
        generation: true
    },
    boundary:{
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                schemaName: "target-readme"
            }
        ],
        generation: true
    }
}