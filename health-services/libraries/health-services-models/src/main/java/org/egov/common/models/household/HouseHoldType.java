package org.egov.common.models.household;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public enum HouseHoldType {

    FAMILY("FAMILY"),

    COMMUNITY("COMMUNITY"),

    OTHER("OTHER");

    private String value;

    HouseHoldType(String value) {
        this.value = value;
    }

    @JsonCreator
    public static HouseHoldType fromValue(String text) {
        for (HouseHoldType b : HouseHoldType.values()) {
            if (String.valueOf(b.value).equalsIgnoreCase(text)) {
                return b;
            }
        }
        return null;
    }

}
