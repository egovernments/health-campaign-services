package org.egov.transformer.models.bill;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum for bill transaction report status
 */
public enum ReportStatus {

    INITIATED("INITIATED"),

    GENERATED("GENERATED"),

    FAILED("FAILED");

    private String value;

    ReportStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return String.valueOf(value);
    }

    @JsonCreator
    public static ReportStatus fromValue(String text) {
        for (ReportStatus b : ReportStatus.values()) {
            if (String.valueOf(b.value).equalsIgnoreCase(text)) {
                return b;
            }
        }
        return null;
    }
}
