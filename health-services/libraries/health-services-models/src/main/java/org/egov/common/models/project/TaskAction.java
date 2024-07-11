package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskAction {
    CLOSED_HOUSEHOLD("CLOSED_HOUSEHOLD"),
    LOCATION_CAPTURE("LOCATION_CAPTURE"),
    OTHER("OTHER");

    private String value;

    TaskAction(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return String.valueOf(value);
    }

    @JsonCreator
    public static TaskAction fromValue(String text) {
        for (TaskAction status : TaskAction.values()) {
            if (String.valueOf(status.value).equals(text)) {
                return status;
            }
        }
        return null;
    }

}
