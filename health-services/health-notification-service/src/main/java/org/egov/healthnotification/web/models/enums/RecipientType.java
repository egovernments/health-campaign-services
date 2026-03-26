package org.egov.healthnotification.web.models.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RecipientType {
    HOUSEHOLD_HEAD("HOUSEHOLD_HEAD"),
    HEALTH_WORKER("HEALTH_WORKER");

    private final String value;

    RecipientType(String value) {
        this.value = value;
    }

    @JsonCreator
    public static RecipientType fromValue(String text) {
        for (RecipientType type : RecipientType.values()) {
            if (String.valueOf(type.value).equalsIgnoreCase(text)) {
                return type;
            }
        }
        return null;
    }

    @Override
    @JsonValue
    public String toString() {
        return String.valueOf(value);
    }
}
