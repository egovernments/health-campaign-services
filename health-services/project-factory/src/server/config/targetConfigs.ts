export const targetConfigs: any = {
    "LLIN-mz": {
        "beneficiaries": [
            {
                "beneficiaryType": "INDIVIDUAL",
                "columns": ["HCM_ADMIN_CONSOLE_TARGET_BEDNET_COLUMN_2"]
            },
            {
                "beneficiaryType": "HOUSEHOLD",
                "columns": ["HCM_ADMIN_CONSOLE_TARGET"]
            },
            {
                "beneficiaryType": "PRODUCT",
                "columns": ["HCM_ADMIN_CONSOLE_TARGET_BEDNET_COLUMN_3"]
            }
        ]
    },
    "MR-DN": {
        "beneficiaries": [
            {
                "beneficiaryType": "INDIVIDUAL",
                "columns": ["HCM_ADMIN_CONSOLE_TARGET_SMC_AGE_3_TO_11", "HCM_ADMIN_CONSOLE_TARGET_SMC_AGE_12_TO_59"]
            },
            {
                "beneficiaryType": "HOUSEHOLD",
                "columns": ["HCM_ADMIN_CONSOLE_TARGET_SMC_COLUUM_3"]
            },
            {
                "beneficiaryType": "PRODUCT",
                "columns": ["HCM_ADMIN_CONSOLE_TARGET_SMC_COLUUM_4"]
            }
        ]
    },
    "IRS-mz": {
        "beneficiaries": [
            {
                "beneficiaryType": "INDIVIDUAL",
                "columns": ["HCM_ADMIN_CONSOLE_TARGET_IRS_4"]
            },
            {
                "beneficiaryType": "HOUSEHOLD",
                "columns": ["HCM_ADMIN_CONSOLE_TARGET_IRS_1"]
            },
            {
                "beneficiaryType": "PRODUCT",
                "columns": ["HCM_ADMIN_CONSOLE_TARGET_IRS_3"]
            }
        ]
    }
}