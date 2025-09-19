package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum UserActionEnum {
    CLOSED_HOUSEHOLD("CLOSED_HOUSEHOLD"),
    LOCATION_CAPTURE("LOCATION_CAPTURE"),
    TRANSIT("TRANSIT"),
    FIXED("FIXED"),
    DAILY_PLAN("DAILY_PLAN"),
    NON_COMPLIANCE("NON_COMPLIANCE"),
    OTHER("OTHER");

    private String value;

    UserActionEnum(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return String.valueOf(value);
    }

    @JsonCreator
    public static UserActionEnum fromValue(String text) {
        for (UserActionEnum status : UserActionEnum.values()) {
            if (String.valueOf(status.value).equals(text)) {
                return status;
            }
        }
        return null;
    }

}
