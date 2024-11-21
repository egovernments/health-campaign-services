package org.egov.hrms.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.egov.tracer.model.CustomException;

public enum UserServiceType {

    DEFAULTUSERSERVICE("defaultUserService"), INDIVIDUALSERVICE("individualService");

    private String value;

    UserServiceType(String value) {
        this.value = value;
    }

    @JsonCreator
    public static UserServiceType fromValue(String text) {
        for (UserServiceType b : UserServiceType.values()) {
            if (String.valueOf(b.value).equals(text)) {
                return b;
            }
        }
        throw new CustomException("USER_SERVICE_TYPE_INVALID", "Invalid user service type: " + text);
    }

    @Override
    @JsonValue
    public String toString() {
        return String.valueOf(value);
    }
}
