package org.egov.transformer.models.error;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ErrorType {
    RECOVERABLE("RECOVERABLE"),
    NON_RECOVERABLE("NON_RECOVERABLE");

    private String value;

    private ErrorType(String value) {
        this.value = value;
    }

    @JsonValue
    public String toString() {
        return String.valueOf(this.value);
    }

    @JsonCreator
    public static ErrorType fromValue(String text) {
        for(ErrorType b : values()) {
            if (String.valueOf(b.value).equalsIgnoreCase(text)) {
                return b;
            }
        }

        return null;
    }
}
