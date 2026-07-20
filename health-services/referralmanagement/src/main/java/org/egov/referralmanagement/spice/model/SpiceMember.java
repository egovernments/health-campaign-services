package org.egov.referralmanagement.spice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Raw member (person) object as returned by Spice {@code spice-service/household/member/list}.
 * One Spice member maps to an HCM Individual + an HCM HouseholdMember link.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpiceMember {

    private String id;                 // Spice server id -> HCM Individual & HouseholdMember clientReferenceId
    private String name;
    private String householdId;        // Spice server id of the household -> householdClientReferenceId
    private String phoneNumber;
    private String phoneNumberCategory;
    private String patientId;
    private String gender;
    private String initial;
    private String signature;
    private String village;
    private String villageId;
    private String householdHeadRelationship;
    private String dateOfBirth;
    private Boolean isPregnant;
    private Boolean isChild;
    private Boolean isActive;
    private String deceasedReason;
    private String motherPatientId;
    private String motherMemberId;
    private Boolean hasTbContactTracing;
    private Double latitude;
    private Double longitude;
    private String lastUpdated;
}
