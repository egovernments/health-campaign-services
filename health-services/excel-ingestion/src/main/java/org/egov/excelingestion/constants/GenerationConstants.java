package org.egov.excelingestion.constants;

public class GenerationConstants {

    // Generation statuses
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_EXPIRED = "expired";

    // Generation types
    public static final String TYPE_MICROPLAN = "microplan";
    public static final String TYPE_PROJECT = "project";
    public static final String TYPE_CAMPAIGN = "campaign";

    // Table and column names
    public static final String TABLE_NAME = "eg_ex_in_generated_files";

    // Redis cache key prefix for "by reference" generation lists
    public static final String CACHE_KEY_GENERATION_BY_REF = "eg:in:gen:byRef:";

    // Failure when the persister-created row never materializes within the wait window
    public static final String GENERATION_ROW_NOT_MATERIALIZED = "GENERATION_ROW_NOT_MATERIALIZED";
    public static final String GENERATION_ROW_NOT_MATERIALIZED_MESSAGE =
            "Generation record was not persisted in time; please retry";

    private GenerationConstants() {
        // Private constructor to prevent instantiation
    }
}