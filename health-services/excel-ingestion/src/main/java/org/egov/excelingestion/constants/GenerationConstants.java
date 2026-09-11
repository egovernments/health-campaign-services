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

    // Hidden metadata sheet carrying per-file metadata stamped at generation time.
    // The "_h_..._h_" name is auto-hidden at generation and auto-skipped by the processing pipeline.
    public static final String META_SHEET_NAME = "_h_Meta_h_";

    // Fixed cell layout of the metadata sheet. Positional (not key/value) to stay byte-compatible
    // with files generated before the locale cell existed, where row 0 / cell 0 held the generationId
    // and nothing else. Never renumber these - older files in the wild depend on them.
    public static final int META_ROW_INDEX = 0;
    public static final int META_GENERATION_ID_CELL_INDEX = 0;
    public static final int META_LOCALE_CELL_INDEX = 1;

    private GenerationConstants() {
        // Private constructor to prevent instantiation
    }
}