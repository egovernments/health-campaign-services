package org.egov.excelingestion.config;

public class ProcessingConstants {
    
    
    // MDMS Configuration
    public static final String MDMS_SCHEMA_CODE = "HCM-ADMIN-CONSOLE.schemas";
    
    // Status Constants
    public static final String STATUS_GENERATED = "generated";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_PROCESSED = "processed";
    
    private ProcessingConstants() {
        // Private constructor to prevent instantiation
    }
}