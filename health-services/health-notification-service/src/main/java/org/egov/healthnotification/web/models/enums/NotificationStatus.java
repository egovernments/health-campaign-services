package org.egov.healthnotification.web.models.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NotificationStatus {
    PENDING("PENDING"),
    SENT("SENT"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED");

    private final String value;

    NotificationStatus(String value) {
        this.value = value;
    }

    @JsonCreator
    public static NotificationStatus fromValue(String text) {
        for (NotificationStatus status : NotificationStatus.values()) {
            if (String.valueOf(status.value).equalsIgnoreCase(text)) {
                return status;
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
