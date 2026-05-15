package org.egov.transformer.models.musterRoll;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Status {
    ACTIVE("ACTIVE"),
    INWORKFLOW("INWORKFLOW"),
    INACTIVE("INACTIVE"),
    CANCELLED("CANCELLED");

    private final String value;

    private Status(String value) {
        this.value = value;
    }

    @JsonValue
    public String toString() {
        return String.valueOf(this.value);
    }

    @JsonCreator
    public static Status fromValue(String text) {
        for(Status b : values()) {
            if (String.valueOf(b.value).equals(text)) {
                return b;
            }
        }

        return null;
    }
}
