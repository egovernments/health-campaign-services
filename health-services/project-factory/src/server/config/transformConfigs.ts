export const transformConfigs: any = {
    employeeHrms: {
        metadata: {
            tenantId: "dev",
            hierarchy: "MICROPLAN"
        },
        fields: [
            {
                path: "$.user.name",
                source: { header: "HCM_ADMIN_CONSOLE_USER_NAME" }
            },
            {
                path: "$.user.mobileNumber",
                source: { header: "HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER" }
            },
            {
                path: "$.user.roles[*].name",
                source: { header: "HCM_ADMIN_CONSOLE_USER_ROLE", splitBy: "," }
            },
            {
                path: "$.user.roles[*].code",
                source: { header: "HCM_ADMIN_CONSOLE_USER_ROLE", splitBy: "," }
            },
            {
                path: "$.user.roles[*].tenantId",
                value: "${metadata.tenantId}"
            },
            {
                path: "$.user.userName",
                source: { header: "UserName" }
            },
            {
                path: "$.user.password",
                source: { header: "Password" }
            },
            {
                path: "$.user.tenantId",
                value: "${metadata.tenantId}"
            },
            {
                path: "$.user.dob",
                value: 0
            },
            {
                path: "$.employeeType",
                source: {
                    header: "HCM_ADMIN_CONSOLE_USER_EMPLOYMENT_TYPE",
                    transform: {
                        mapping: {
                            Permanent: "PERMANENT",
                            Temporary: "TEMPORARY",
                            "%default%": "TEMPORARY"
                        }
                    }
                }
            },
            {
                path: "$.jurisdictions[*].boundary",
                source: {
                    header: "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY",
                    splitBy: ","
                }
            },
            {
                path: "$.jurisdictions[*].tenantId",
                value: "${metadata.tenantId}"
            },
            {
                path: "$.jurisdictions[*].hierarchy",
                value: "${metadata.hierarchy}"
            },
            {
                path: "$.jurisdictions[*].boundaryType",
                value: "${metadata.hierarchy}"
            },
            {
                path: "$.tenantId",
                value: "${metadata.tenantId}"
            },
            {
                path: "$.code",
                source: { header: "UserName" }
            }
        ],
        transFormSingle: "transformEmployee",
        transFormBulk: "transformBulkEmployee"
    },

    Facility: {
        metadata: {
            tenantId: "dev",
            hierarchy: "MICROPLAN"
        },
        fields: [
            {
                path: "$.Facility.id",
                source: { header: "HCM_ADMIN_CONSOLE_FACILITY_CODE" }
            },
            {
                path: "$.Facility.tenantId",
                value: "${metadata.tenantId}"
            },
            {
                path: "$.Facility.name",
                source: { header: "HCM_ADMIN_CONSOLE_FACILITY_NAME" }
            },
            {
                path: "$.Facility.isPermanent",
                source: {
                    header: "HCM_ADMIN_CONSOLE_FACILITY_STATUS",
                    transform: {
                        mapping: {
                            Permanent: "true",
                            Temporary: "false",
                            "%default%": "false"
                        }
                    }
                }
            },
            {
                path: "$.Facility.usage",
                source: { header: "HCM_ADMIN_CONSOLE_FACILITY_TYPE" }
            },
            {
                path: "$.Facility.storageCapacity",
                source: { header: "HCM_ADMIN_CONSOLE_FACILITY_CAPACITY" }
            },
            {
                path: "$.Facility.address.locality.code",
                source: { header: "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY" }
            },
            {
                path: "$.Facility.address.tenantId",
                value: "${metadata.tenantId}"
            }
        ],
        transFormBulk: "transformBulkFacility"
    }
};
  