package org.egov.individual.web.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lifecycle of a minted team code.
 * UNASSIGNED - minted, no individual carries it.
 * ASSIGNED   - at least one individual carries it.
 * INACTIVE   - retired, cannot be assigned. Reserved for ops, never set by the team APIs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public enum TeamCodeStatus {

    UNASSIGNED,

    ASSIGNED,

    INACTIVE;

    @Override
    @JsonValue
    public String toString() {
        return name();
    }

    @JsonCreator
    public static TeamCodeStatus fromValue(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        for (TeamCodeStatus status : TeamCodeStatus.values()) {
            if (status.name().equalsIgnoreCase(text.trim())) {
                return status;
            }
        }
        return null;
    }
}
