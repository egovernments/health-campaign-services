package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets multi round task status
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MultiRoundConstants {
    public enum Status {
        DELIVERED("DELIVERED"),
        ADMINISTERED("ADMINISTERED"),
        OBSERVED("OBSERVED");

        private String value;

        Status(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static Status fromValue(String text) {
            for (Status b : Status.values()) {
                if (String.valueOf(b.value).equals(text)) {
                    return b;
                }
            }
            return null;
        }
    }

    public enum DeliveryType {
        DIRECT("DIRECT"),
        INDIRECT("INDIRECT");

        private String value;

        DeliveryType(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static DeliveryType fromValue(String text) {
            for (DeliveryType b : DeliveryType.values()) {
                if (String.valueOf(b.value).equals(text)) {
                    return b;
                }
            }
            return null;
        }

    }
}
