package org.egov.excelingestion.config;

public class ProcessingConstants {
    
    
    // MDMS Configuration
    public static final String MDMS_SCHEMA_CODE = "HCM-ADMIN-CONSOLE.schemas";

    // Column Keys
    public static final String BOUNDARY_CODE_COLUMN_KEY = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE";
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