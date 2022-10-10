package org.digit.health.registration.web.models;

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
public class HouseholdRegistration extends CampaignData {
    @JsonProperty("householdId")
    private String householdId;

    @JsonProperty("clientReferenceId")
    private String clientReferenceId;

    @JsonProperty("campaignId")
    private String campaignId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("apiMode")
    private ApiMode apiMode;

    @JsonProperty("administrativeUnit")
    private String administrativeUnit;

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

    @JsonProperty("additionalFields")
    private String additionalFields;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;
}
