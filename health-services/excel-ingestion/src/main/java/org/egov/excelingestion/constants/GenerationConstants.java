package org.egov.excelingestion.constants;

public class GenerationConstants {
    
    // Generation statuses
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    
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