package org.egov.referralmanagement.spice;

import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.core.Boundary;
import org.egov.common.models.household.HouseHoldType;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.Relationship;
import org.egov.common.models.individual.Address;
import org.egov.common.models.individual.AddressType;
import org.egov.common.models.individual.Gender;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.Name;
import org.egov.referralmanagement.spice.model.SpiceHousehold;
import org.egov.referralmanagement.spice.model.SpiceMember;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Maps live Spice objects to HCM registry models for the downsync response.
 *
 * <p>The Spice server id is copied verbatim into {@code clientReferenceId} on every entity so the
 * app can correlate records without HCM ever persisting them. HCM {@code id} stays null (live, unstored).
 * Spice-specific attributes with no native HCM field are dropped here (kept minimal for the live path).</p>
 */
@Component
public class SpiceMapper {

    private static final String HEAD = "HouseholdHead";
    private static final String ACTOR = "spice-downsync";

    public Household toHousehold(SpiceHousehold s, String localityCode, String tenantId) {
        Household h = new Household();
        h.setId(s.getId());                    // Spice server id -> id
        h.setClientReferenceId(s.getId());     // Spice server id -> clientReferenceId
        h.setTenantId(tenantId);
        h.setMemberCount(s.getNoOfPeople());
        h.setHouseholdType(HouseHoldType.FAMILY);
        h.setRowVersion(1);
        h.setAuditDetails(audit(s.getLastUpdated()));

        org.egov.common.models.household.Address addr = new org.egov.common.models.household.Address();
        addr.setTenantId(tenantId);
        addr.setLatitude(s.getLatitude());
        addr.setLongitude(s.getLongitude());
        addr.setLandmark(s.getLandmark());
        addr.setLocality(boundary(localityCode, tenantId));
        h.setAddress(addr);
        return h;
    }

    public Individual toIndividual(SpiceMember m, String localityCode, String wardCode, String tenantId) {
        Individual ind = new Individual();
        ind.setId(m.getId());                  // Spice server id -> id
        ind.setClientReferenceId(m.getId());   // Spice server id -> clientReferenceId
        ind.setTenantId(tenantId);
        ind.setRowVersion(1);
        ind.setAuditDetails(audit(m.getLastUpdated()));
        ind.setName(name(m));
        ind.setGender(gender(m.getGender()));
        ind.setDateOfBirth(parseDate(m.getDateOfBirth()));
        ind.setMobileNumber(m.getPhoneNumber());
        ind.setPhoto(m.getSignature());
        ind.setIdentifiers(identifiers(m));

        Address a = Address.builder()
                .tenantId(tenantId)
                .type(AddressType.PERMANENT)
                .latitude(m.getLatitude())
                .longitude(m.getLongitude())
                .locality(boundary(localityCode, tenantId))
                .ward(boundary(wardCode, tenantId))
                .build();
        List<Address> addresses = new ArrayList<>();
        addresses.add(a);
        ind.setAddress(addresses);
        return ind;
    }

    public HouseholdMember toHouseholdMember(SpiceMember m, String tenantId) {
        HouseholdMember hm = new HouseholdMember();
        hm.setId(m.getId());                                      // Spice member server id -> id
        hm.setClientReferenceId(m.getId());                       // Spice member server id -> clientReferenceId
        hm.setTenantId(tenantId);
        hm.setRowVersion(1);
        hm.setAuditDetails(audit(m.getLastUpdated()));
        hm.setHouseholdId(m.getHouseholdId());                    // Spice household server id -> id
        hm.setHouseholdClientReferenceId(m.getHouseholdId());     // Spice household server id -> clientReferenceId
        hm.setIndividualId(m.getId());                            // links to the Individual above (by id)
        hm.setIndividualClientReferenceId(m.getId());             // links to the Individual above (by clientRef)
        boolean isHead = HEAD.equalsIgnoreCase(m.getHouseholdHeadRelationship());
        hm.setIsHeadOfHousehold(isHead);

        String rel = m.getHouseholdHeadRelationship();
        if (rel != null && !isHead) {
            Relationship r = new Relationship();
            r.setTenantId(tenantId);
            r.setRelationshipType(rel);
            List<Relationship> rels = new ArrayList<>();
            rels.add(r);
            hm.setMemberRelationships(rels);
        }
        return hm;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** Synthesises DIGIT audit details from Spice's lastUpdated (falls back to now). */
    private AuditDetails audit(String lastUpdatedIso) {
        long t = parseEpoch(lastUpdatedIso);
        return AuditDetails.builder()
                .createdBy(ACTOR).createdTime(t)
                .lastModifiedBy(ACTOR).lastModifiedTime(t)
                .build();
    }

    private long parseEpoch(String iso) {
        if (iso != null && !iso.isBlank()) {
            try {
                return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
            } catch (Exception ignored) {
                // fall through
            }
        }
        return System.currentTimeMillis();
    }

    private Boundary boundary(String code, String tenantId) {
        if (code == null) return null;
        return Boundary.builder().code(code).tenantId(tenantId).build();
    }

    private Name name(SpiceMember m) {
        String full = m.getName() == null ? "" : m.getName().trim();
        String given = full.isEmpty() ? "UNKNOWN" : full;
        String family = null;
        if (full.contains(" ")) {
            String[] parts = full.split("\\s+");
            given = parts[0];
            family = parts[parts.length - 1];
        }
        return Name.builder()
                .givenName(given)
                .familyName(family)
                .otherNames(m.getInitial())
                .build();
    }

    private List<Identifier> identifiers(SpiceMember m) {
        List<Identifier> ids = new ArrayList<>();
        if (m.getPatientId() != null) {
            ids.add(Identifier.builder().identifierType("SPICE_PATIENT_ID").identifierId(m.getPatientId()).build());
            // Canonical patient identity for the CCN integration. The inbound resolver matches on
            // identifierType=UNIQUE_BENEFICIARY_ID, so a downsynced individual must carry it (= the
            // Spice patient id, the 13-digit value that also rides the wire as the ABHA healthId).
            // NOTE(ccn): the canonical id is defined as idgen-generated from the beneficiary id-pool;
            // this live downsync has no idgen step, so we source it from the Spice patientId to keep
            // the key identical on both sides of the wire.
            ids.add(Identifier.builder().identifierType("UNIQUE_BENEFICIARY_ID").identifierId(m.getPatientId()).build());
        }
        ids.add(Identifier.builder().identifierType("SPICE_MEMBER_ID").identifierId(m.getId()).build());
        if (m.getMotherPatientId() != null)
            ids.add(Identifier.builder().identifierType("SPICE_MOTHER_PATIENT_ID").identifierId(m.getMotherPatientId()).build());
        return ids;
    }

    private Gender gender(String g) {
        if (g == null) return null;
        switch (g.toLowerCase()) {
            case "male": return Gender.MALE;
            case "female": return Gender.FEMALE;
            case "transgender": return Gender.TRANSGENDER;
            default: return Gender.OTHER;
        }
    }

    private Date parseDate(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Date.from(OffsetDateTime.parse(iso).toInstant());
        } catch (Exception e) {
            return null;
        }
    }
}
