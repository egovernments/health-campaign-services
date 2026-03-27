package org.egov.transformer.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Operation {
    TASK("TASK"),
    PROJECT_STAFF("PROJECT_STAFF"),
    PROJECT("PROJECT"),
    STOCK("STOCK"),
    SERVICE("SERVICE"),
    USER_ACTION("USER_ACTION");

    private String value;

    Operation(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return String.valueOf(value);
    }

    @JsonCreator
    public static Operation fromValue(String text) {
        for (Operation b : Operation.values()) {
            if (String.valueOf(b.value).equals(text)) {
                return b;
            }
        }
        return null;
    }
}
