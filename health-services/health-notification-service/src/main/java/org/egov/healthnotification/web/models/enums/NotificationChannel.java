package org.egov.healthnotification.web.models.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NotificationChannel {

    SMS("SMS"),
    PUSH("PUSH");

    private final String value;

    NotificationChannel(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static NotificationChannel fromValue(String text) {
        for (NotificationChannel channel : NotificationChannel.values()) {
            if (channel.value.equalsIgnoreCase(text)) {
                return channel;
            }
        }
        return null;
    }
}
