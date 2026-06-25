package org.egov.excelingestion.constants;

public class GenerationConstants {
    
    // Generation statuses
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_EXPIRED = "expired";
    
    // Generation types
    public static final String TYPE_MICROPLAN = "microplan";
    public static final String TYPE_PROJECT = "project";
    public static final String TYPE_CAMPAIGN = "campaign";
    
    // Table and column names
    public static final String TABLE_NAME = "eg_ex_in_generated_files";

    // Hidden metadata sheet (and cells) carrying the generationId in unprotected join-mode files.
    // The "_h_..._h_" name is auto-hidden at generation and auto-skipped by the processing pipeline.
    // Row 0 layout: cell 0 = generationId, cell 1 = HMAC signature over the generationId (exact-file guard).
    public static final String META_SHEET_NAME = "_h_Meta_h_";
    public static final int META_GENERATION_ID_CELL = 0;
    public static final int META_SIGNATURE_CELL = 1;
    
    private GenerationConstants() {
        // Private constructor to prevent instantiation
    }
}