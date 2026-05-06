package org.egov.transformer.models.bill;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum for bill report type
 */
public enum ReportType {

    TRANSACTION_REPORT_EXCEL("TRANSACTION_REPORT_EXCEL"),

    TRANSACTION_REPORT_PDF("TRANSACTION_REPORT_PDF"),

    PAYMENT_ADVISORY_EXCEL("PAYMENT_ADVISORY_EXCEL");

    private String value;

    ReportType(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return String.valueOf(value);
    }

    @JsonCreator
    public static ReportType fromValue(String text) {
        for (ReportType b : ReportType.values()) {
            if (String.valueOf(b.value).equalsIgnoreCase(text)) {
                return b;
            }
        }
        return null;
    }
}
