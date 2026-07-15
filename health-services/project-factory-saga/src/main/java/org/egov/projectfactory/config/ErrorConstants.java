package org.egov.projectfactory.config;

/**
 * Reusable error codes/messages. Never hardcode these strings elsewhere
 * (see excel-ingestion CLAUDE.md — constants for repeated strings).
 */
public final class ErrorConstants {

    private ErrorConstants() {
    }

    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    public static final String INTERNAL_SERVER_ERROR_MESSAGE = "An unexpected error occurred while processing the request";

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
}
