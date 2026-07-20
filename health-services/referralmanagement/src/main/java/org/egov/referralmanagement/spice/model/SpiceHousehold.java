package org.egov.referralmanagement.spice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Raw household object as returned by Spice {@code spice-service/household/list}.
 * Only the fields consumed by the mapper are declared; everything else is ignored.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpiceHousehold {

    private String id;                 // Spice server id -> copied to HCM clientReferenceId
    private Integer householdNo;
    private String name;
    private String village;
    private String villageId;
    private String landmark;
    private String headPhoneNumber;
    private String headPhoneNumberCategory;
    private Integer noOfPeople;
    private Boolean ownedHandWashingFacilityWithSoap;
    private Boolean ownedTreatedBedNet;
    private Boolean ownedAnImprovedLatrine;
    private Boolean hasImprovedWaterSource;
    private Integer bedNetCount;
    private Double latitude;
    private Double longitude;
    private String lastUpdated;
}
