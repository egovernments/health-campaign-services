package org.digit.health.sync.web.models;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = HouseholdRegistration.class,
        name = "householdRegistration"),
        @JsonSubTypes.Type(value = Delivery.class, name = "delivery")})
public class CampaignData {
    private String type;
}
