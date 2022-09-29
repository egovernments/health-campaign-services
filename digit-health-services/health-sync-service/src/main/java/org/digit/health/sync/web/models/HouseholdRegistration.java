package org.digit.health.sync.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdRegistration {
    @JsonProperty("householdId")
    private String householdId;

    private String clientReferenceId;

    private String campaignId;

    private String tenantId;

    @JsonProperty("numberOfIndividuals")
    private int numberOfIndividuals;

    @JsonProperty("dateOfRegistration")
    private long dateOfRegistration;

    @JsonProperty("address")
    private Address address;

    @JsonProperty("location")
    private Location location;

    @JsonProperty("individuals")
    private List<Individual> individuals;

    private Mode mode;

    private String additionalFields;
}
