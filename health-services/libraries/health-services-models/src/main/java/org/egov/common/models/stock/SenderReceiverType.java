package org.egov.common.models.stock;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonIgnoreProperties(ignoreUnknown = true)
public enum SenderReceiverType {
    WAREHOUSE("WAREHOUSE"),

    STAFF("STAFF");

    private String value;

    SenderReceiverType(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return String.valueOf(value);
    }

    @JsonCreator
    public static SenderReceiverType fromValue(String text) {
        for (SenderReceiverType b : SenderReceiverType.values()) {
            if (String.valueOf(b.value).equals(text)) {
                return b;
            }
        }
        return null;
    }
}
