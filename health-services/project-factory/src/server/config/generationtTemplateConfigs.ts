import { errorWorksheetName } from "./constants";

export const generationtTemplateConfigs : any = {
    user: {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
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
                lockWholeSheet: false
            }
        ],
    },
    userCredential: {
        sheets: [
            {
                sheetName: "HCM_ADMIN_CONSOLE_USER_LIST",
                schemaName: "user",
                lockWholeSheet: true
            },
            {
                sheetName: errorWorksheetName,
                schemaName: "user",
                lockWholeSheet: false,
                optional: true
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
                lockWholeSheet: false
            }
        ],
    },

    boundary: {
        sheets: [
            {
                sheetName: "HCM_README_SHEETNAME",
                schemaName: "target-readme",
                lockWholeSheet: true
            }
        ]
    },

    attendanceRegister: {
        sheets: [
            {
                sheetName: "HCM_ATTENDANCE_REGISTER_README",
                schemaName: "attendance-register-readme",
                lockWholeSheet: true
            },
            {
                sheetName: "HCM_ATTENDANCE_REGISTER_LIST",
                schemaName: "attendance-register"
            },
            {
                sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
                schemaName: "boundary-data",
                lockWholeSheet: false
            }
        ]
    },

    attendanceRegisterAttendee: {
        sheets: [
            {
                sheetName: "HCM_REGISTER_WORKER_SHEET",
                schemaName: "attendance-register-attendee-worker"
            },
            {
                sheetName: "HCM_REGISTER_MARKER_SHEET",
                schemaName: "attendance-register-attendee-marker"
            },
            {
                sheetName: "HCM_REGISTER_APPROVER_SHEET",
                schemaName: "attendance-register-attendee-approver"
            }
        ]
    }
}
