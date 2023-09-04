package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;
import org.egov.common.models.stock.TransactionReason;

/**
 * Gets or Sets multi round task status
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public enum MultiRoundStatus {
    DELIVERED("DELIVERED"),
    ADMINISTERED("ADMINISTERED"),
    OBSERVED("OBSERVED");

    private String value;

    MultiRoundStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return String.valueOf(value);
    }

    @JsonCreator
    public static MultiRoundStatus fromValue(String text) {
        for (MultiRoundStatus b : MultiRoundStatus.values()) {
            if (String.valueOf(b.value).equals(text)) {
                return b;
            }
        }
        return null;
    }
}
