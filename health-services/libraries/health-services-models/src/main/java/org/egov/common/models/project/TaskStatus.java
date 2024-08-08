package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskStatus {
    ADMINISTRATION_FAILED("ADMINISTRATION_FAILED"),
    ADMINISTRATION_SUCCESS("ADMINISTRATION_SUCCESS"),
    BENEFICIARY_REFUSED("BENEFICIARY_REFUSED"),
    CLOSED_HOUSEHOLD("CLOSED_HOUSEHOLD"),
    DELIVERED("DELIVERED"),
    NOT_ADMINISTERED("NOT_ADMINISTERED");

    private String value;

    TaskStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return String.valueOf(value);
    }

    @JsonCreator
    public static TaskStatus fromValue(String text) {
        for (TaskStatus status : TaskStatus.values()) {
            if (String.valueOf(status.value).equals(text)) {
                return status;
            }
        }
        return null;
    }
}
