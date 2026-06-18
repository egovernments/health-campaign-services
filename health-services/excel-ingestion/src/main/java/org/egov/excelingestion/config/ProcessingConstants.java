package org.egov.excelingestion.config;

public class ProcessingConstants {
    
    
    // MDMS Configuration
    public static final String MDMS_SCHEMA_CODE = "HCM-ADMIN-CONSOLE.schemas";
    public static final String MDMS_EXCEL_INGESTION_GENERATE_SCHEMA = "HCM-ADMIN-CONSOLE.excelIngestionGenerate";
    public static final String MDMS_ATTENDANCE_REGISTER_ATTENDEE_CONFIG_NAME = "attendanceRegisterAttendee";

    // Column Keys
    public static final String BOUNDARY_CODE_COLUMN_KEY = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE";
    public static final String WORKER_ID_COLUMN_KEY = "HCM_ADMIN_CONSOLE_USER_WORKER_ID";

    // Payee field column keys
    public static final String PAYMENT_PROVIDER_COL = "HCM_ADMIN_CONSOLE_USER_PAYMENT_PROVIDER";
    public static final String PAYEE_NAME_COL = "HCM_ADMIN_CONSOLE_USER_PAYEE_NAME";
    public static final String BENEFICIARY_CODE_COL = "HCM_ADMIN_CONSOLE_USER_BENEFICIARY_CODE";
    public static final String BANK_ACCOUNT_COL = "HCM_ADMIN_CONSOLE_USER_BANK_ACCOUNT";
    public static final String BANK_CODE_COL = "HCM_ADMIN_CONSOLE_USER_BANK_CODE";
    public static final String PAYEE_PHONE_NUMBER_COL = "HCM_ADMIN_CONSOLE_USER_PAYEE_PHONE_NUMBER";

    // Payment provider values used in conditional formatting rules
    public static final String PAYMENT_PROVIDER_BANK = "BANK";
    public static final String PAYMENT_PROVIDER_MTN = "MTN";
    public static final String REGISTER_ID_COLUMN_KEY = "HCM_ATTENDANCE_REGISTER_ID";
    public static final String ENROLLMENT_DATE_COLUMN_KEY = "HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE";
    public static final String DEENROLLMENT_DATE_COLUMN_KEY = "HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE";
    public static final String USERNAME_COLUMN_KEY = "UserName";

    // Reference Type Constants
    public static final String REFERENCE_TYPE_CAMPAIGN = "campaign";
    public static final String REFERENCE_TYPE_ATTENDANCE_REGISTER = "attendanceRegister";

    // AdditionalDetails Keys
    public static final String ADDITIONAL_DETAILS_CAMPAIGN_ID = "campaignId";
    public static final String ADDITIONAL_DETAILS_REGISTER_ID = "registerId";

    // Attendance Register API Response Keys
    public static final String ATTENDANCE_REGISTER_RESPONSE_KEY = "attendanceRegister";
    public static final String ATTENDANCE_REGISTER_CAMPAIGN_ID_KEY = "campaignId";
    public static final String ATTENDANCE_REGISTER_SERVICE_CODE_KEY = "serviceCode";
    
    // Attendance Register Attendee Column Keys
    public static final String TEAM_CODE_COLUMN_KEY = "HCM_ATTENDANCE_ATTENDEE_TEAM_CODE";

    // Attendance Register Response Field Keys
    public static final String ATTENDANCE_REGISTER_START_DATE_KEY = "startDate";
    public static final String ATTENDANCE_REGISTER_END_DATE_KEY = "endDate";
    public static final String ATTENDANCE_REGISTER_ATTENDEES_KEY = "attendees";
    public static final String ATTENDANCE_REGISTER_STAFF_KEY = "staff";

    // Attendee/Staff Record Field Keys
    public static final String ATTENDEE_INDIVIDUAL_ID_KEY = "individualId";
    public static final String ATTENDEE_REGISTER_ID_KEY = "registerId";
    public static final String STAFF_USER_ID_KEY = "userId";
    public static final String STAFF_TYPE_KEY = "staffType";
    public static final String ENROLLMENT_DATE_KEY = "enrollmentDate";
    public static final String DEENROLLMENT_DATE_KEY = "denrollmentDate";

    // Attendee/Staff Search API Response Keys
    public static final String ATTENDEE_SEARCH_RESPONSE_KEY = "attendees";
    public static final String STAFF_SEARCH_RESPONSE_KEY = "staff";

    // Search criteria key for passing a list of individual IDs
    public static final String INDIVIDUAL_IDS_CRITERIA_KEY = "individualIds";

    // Staff Type Values
    public static final String STAFF_TYPE_OWNER = "OWNER";
    public static final String STAFF_TYPE_APPROVER = "APPROVER";

    // AdditionalDetails Keys — Attendance
    public static final String ADDITIONAL_DETAILS_STAFF_TYPE = "staffType";

    // HRMS API Response Keys
    public static final String HRMS_EMPLOYEES_RESPONSE_KEY = "Employees";
    public static final String HRMS_EMPLOYEE_CODE_KEY = "code";
    public static final String HRMS_EMPLOYEE_USER_KEY = "user";
    public static final String HRMS_USER_UUID_KEY = "uuid";
    public static final String HRMS_USER_ROLES_KEY = "roles";
    public static final String HRMS_ROLE_CODE_KEY = "code";

    // Common API Keys
    public static final String REQUEST_INFO_KEY = "RequestInfo";

    // Header carrying the server-to-server secret for the project-factory crypto endpoint
    public static final String CRYPTO_INTERNAL_KEY_HEADER = "x-internal-key";

    // Excel Internal Keys
    public static final String ACTUAL_ROW_NUMBER_KEY = "__actualRowNumber__";

    // Status Constants
    public static final String STATUS_GENERATED = "generated";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_PROCESSED = "processed";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_COMPLETED = "completed";
    
    // Table Constants
    public static final String PROCESS_TABLE_NAME = "eg_ex_in_excel_processing";
    
    private ProcessingConstants() {
        // Private constructor to prevent instantiation
    }
}