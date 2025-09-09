package org.egov.excelingestion.constants;

public class GenerationConstants {
    
    // Generation statuses
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    
    // Generation types
    public static final String TYPE_MICROPLAN = "microplan";
    public static final String TYPE_PROJECT = "project";
    public static final String TYPE_CAMPAIGN = "campaign";
    
    // Table and column names
    public static final String TABLE_NAME = "eg_ex_in_generated_files";
    
    private GenerationConstants() {
        // Private constructor to prevent instantiation
    }
}