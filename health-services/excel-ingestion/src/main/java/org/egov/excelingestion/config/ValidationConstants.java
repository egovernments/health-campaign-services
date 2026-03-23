package org.egov.excelingestion.config;

public class ValidationConstants {
    
    // Column names (4 underscores to avoid collision)
    public static final String STATUS_COLUMN_NAME = "HCM_ADMIN_CONSOLE____ROW_STATUS";
    public static final String ERROR_DETAILS_COLUMN_NAME = "HCM_ADMIN_CONSOLE____ERROR_DETAILS";
    
    // Status values
    public static final String STATUS_VALID = "valid";
    public static final String STATUS_INVALID = "invalid";
    public static final String STATUS_CREATED = "created";
    public static final String STATUS_ERROR = "error";
    
    // Multi-select validation constants
    public static final String MULTI_SELECT_SEPARATOR = ",";
    public static final String HCM_VALIDATION_DUPLICATE_SELECTIONS = "HCM_VALIDATION_DUPLICATE_SELECTIONS";
    public static final String HCM_VALIDATION_REQUIRED_MULTI_SELECT = "HCM_VALIDATION_REQUIRED_MULTI_SELECT";
    
    // Error message constants
    public static final String HCM_VALIDATION_FAILED_NO_DETAILS = "HCM_VALIDATION_FAILED_NO_DETAILS";
    
    // Search validation constants
    public static final String INGEST_INVALID_LIMIT = "INGEST_INVALID_LIMIT";
    public static final String INGEST_INVALID_OFFSET = "INGEST_INVALID_OFFSET";
    
    // Attendance Register Attendee — Localization Keys
    public static final String LOC_ATTENDANCE_INVALID_DATE = "HCM_ATTENDANCE_ATTENDEE_INVALID_DATE_FORMAT";
    public static final String LOC_ATTENDANCE_DATE_OUT_OF_RANGE = "HCM_ATTENDANCE_ATTENDEE_DATE_OUT_OF_RANGE";
    public static final String LOC_ATTENDANCE_DEENROLL_WITHOUT_ENROLL = "HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_WITHOUT_ENROLLMENT";
    public static final String LOC_ATTENDANCE_DEENROLL_BEFORE_ENROLL = "HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_BEFORE_ENROLLMENT";
    public static final String LOC_ATTENDANCE_REGISTER_ID_EMPTY = "HCM_ATTENDANCE_ATTENDEE_REGISTER_ID_EMPTY";
    public static final String LOC_ATTENDANCE_REGISTER_ID_MISMATCH = "HCM_ATTENDANCE_ATTENDEE_REGISTER_ID_MISMATCH";
    public static final String LOC_ATTENDANCE_REGISTER_WRONG_CAMPAIGN = "HCM_ATTENDANCE_ATTENDEE_REGISTER_BELONGS_TO_DIFFERENT_CAMPAIGN";

    // Attendance Register Attendee — Default Error Messages
    public static final String DEFAULT_ATTENDANCE_INVALID_DATE = "Invalid date format. Use dd-MM-yyyy or dd/MM/yyyy";
    public static final String DEFAULT_ATTENDANCE_DATE_OUT_OF_RANGE = "Date must be between register start and end dates";
    public static final String DEFAULT_ATTENDANCE_DEENROLL_WITHOUT_ENROLL = "De-enrollment date requires enrollment date";
    public static final String DEFAULT_ATTENDANCE_DEENROLL_BEFORE_ENROLL = "De-enrollment date cannot be before enrollment date";
    public static final String DEFAULT_ATTENDANCE_REGISTER_ID_EMPTY = "Register ID is required";
    public static final String DEFAULT_ATTENDANCE_REGISTER_ID_MISMATCH = "Register ID does not match expected register";
    public static final String DEFAULT_ATTENDANCE_REGISTER_WRONG_CAMPAIGN = "Register already exists in a different campaign";

    private ValidationConstants() {
        // Private constructor to prevent instantiation
    }
}