package org.egov.referralmanagement.spice;

import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.individual.Gender;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.referralmanagement.spice.model.SpiceHousehold;
import org.egov.referralmanagement.spice.model.SpiceMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpiceMapper — Spice objects -> HCM registry models")
class SpiceMapperTest {

    private final SpiceMapper mapper = new SpiceMapper();
    private static final String LOC = "SL_C1_D16_CH24_V64";
    private static final String WARD = "SL_C1_D16_CH24";
    private static final String TENANT = "ba";

    private SpiceHousehold household() {
        SpiceHousehold h = new SpiceHousehold();
        h.setId("998359");
        h.setNoOfPeople(3);
        h.setLandmark("Opposite the school");
        h.setLatitude(8.8664136);
        h.setLongitude(-12.05292);
        h.setVillageId("64");
        return h;
    }

    private SpiceMember member() {
        SpiceMember m = new SpiceMember();
        m.setId("998357");
        m.setHouseholdId("998359");
        m.setName("Moses comEMR");
        m.setInitial("Mr");
        m.setGender("male");
        m.setPhoneNumber("80684592");
        m.setSignature("https://s3/consent/998357.JPEG");
        m.setPatientId("90112342190743");
        m.setDateOfBirth("1986-07-14T00:00:00+00:00");
        m.setHouseholdHeadRelationship("HouseholdHead");
        m.setLatitude(8.8662374);
        m.setLongitude(-12.0527274);
        return m;
    }

    @Nested
    @DisplayName("toHousehold")
    class ToHousehold {
        @Test
        @DisplayName("copies spice id to both id and clientReferenceId and maps core fields")
        void mapsHousehold() {
            Household h = mapper.toHousehold(household(), LOC, TENANT);
            assertEquals("998359", h.getClientReferenceId());
            assertEquals("998359", h.getId(), "Spice server id goes into id too");
            assertEquals(TENANT, h.getTenantId());
            assertEquals(3, h.getMemberCount());
            assertNotNull(h.getHouseholdType());
            assertEquals("FAMILY", h.getHouseholdType().toString());
        }

        @Test
        @DisplayName("maps address with locality boundary code")
        void mapsAddress() {
            Household h = mapper.toHousehold(household(), LOC, TENANT);
            assertNotNull(h.getAddress());
            assertEquals("Opposite the school", h.getAddress().getLandmark());
            assertEquals(8.8664136, h.getAddress().getLatitude());
            assertEquals(-12.05292, h.getAddress().getLongitude());
            assertNotNull(h.getAddress().getLocality());
            assertEquals(LOC, h.getAddress().getLocality().getCode());
            assertEquals(TENANT, h.getAddress().getLocality().getTenantId());
        }
    }

    @Nested
    @DisplayName("toIndividual")
    class ToIndividual {
        @Test
        @DisplayName("copies spice id to both id and clientReferenceId")
        void ids() {
            Individual i = mapper.toIndividual(member(), LOC, WARD, TENANT);
            assertEquals("998357", i.getClientReferenceId());
            assertEquals("998357", i.getId());
            assertEquals(TENANT, i.getTenantId());
        }

        @Test
        @DisplayName("splits two-part name into given + family, keeps initial as otherNames")
        void nameTwoParts() {
            Individual i = mapper.toIndividual(member(), LOC, WARD, TENANT);
            assertEquals("Moses", i.getName().getGivenName());
            assertEquals("comEMR", i.getName().getFamilyName());
            assertEquals("Mr", i.getName().getOtherNames());
        }

        @Test
        @DisplayName("single-word name -> givenName only, familyName null")
        void nameSingle() {
            SpiceMember m = member();
            m.setName("Moses");
            Individual i = mapper.toIndividual(m, LOC, WARD, TENANT);
            assertEquals("Moses", i.getName().getGivenName());
            assertNull(i.getName().getFamilyName());
        }

        @Test
        @DisplayName("blank/null name -> UNKNOWN given name")
        void nameBlank() {
            SpiceMember m = member();
            m.setName(null);
            assertEquals("UNKNOWN", mapper.toIndividual(m, LOC, WARD, TENANT).getName().getGivenName());
            m.setName("   ");
            assertEquals("UNKNOWN", mapper.toIndividual(m, LOC, WARD, TENANT).getName().getGivenName());
        }

        @Test
        @DisplayName("gender strings map to enum; unknown -> OTHER; null -> null")
        void genderMapping() {
            assertEquals(Gender.MALE, gender("male"));
            assertEquals(Gender.FEMALE, gender("female"));
            assertEquals(Gender.TRANSGENDER, gender("transgender"));
            assertEquals(Gender.OTHER, gender("something-else"));
            assertNull(gender(null));
        }

        private Gender gender(String g) {
            SpiceMember m = member();
            m.setGender(g);
            return mapper.toIndividual(m, LOC, WARD, TENANT).getGender();
        }

        @Test
        @DisplayName("valid ISO dateOfBirth parses; invalid/null -> null")
        void dob() {
            assertNotNull(mapper.toIndividual(member(), LOC, WARD, TENANT).getDateOfBirth());
            SpiceMember bad = member();
            bad.setDateOfBirth("not-a-date");
            assertNull(mapper.toIndividual(bad, LOC, WARD, TENANT).getDateOfBirth());
            SpiceMember none = member();
            none.setDateOfBirth(null);
            assertNull(mapper.toIndividual(none, LOC, WARD, TENANT).getDateOfBirth());
        }

        @Test
        @DisplayName("identifiers include patientId, canonical UNIQUE_BENEFICIARY_ID, member id, and motherPatientId when present")
        void identifiersFull() {
            SpiceMember m = member();
            m.setMotherPatientId("90112342190265");
            List<Identifier> ids = mapper.toIndividual(m, LOC, WARD, TENANT).getIdentifiers();
            assertEquals(4, ids.size());
            assertTrue(ids.stream().anyMatch(x -> x.getIdentifierType().equals("SPICE_PATIENT_ID") && x.getIdentifierId().equals("90112342190743")));
            // canonical CCN key mirrors the Spice patientId so the inbound resolver can match a downsynced individual
            assertTrue(ids.stream().anyMatch(x -> x.getIdentifierType().equals("UNIQUE_BENEFICIARY_ID") && x.getIdentifierId().equals("90112342190743")));
            assertTrue(ids.stream().anyMatch(x -> x.getIdentifierType().equals("SPICE_MEMBER_ID") && x.getIdentifierId().equals("998357")));
            assertTrue(ids.stream().anyMatch(x -> x.getIdentifierType().equals("SPICE_MOTHER_PATIENT_ID")));
        }

        @Test
        @DisplayName("no patientId / no motherPatientId -> only member-id identifier")
        void identifiersMinimal() {
            SpiceMember m = member();
            m.setPatientId(null);
            m.setMotherPatientId(null);
            List<Identifier> ids = mapper.toIndividual(m, LOC, WARD, TENANT).getIdentifiers();
            assertEquals(1, ids.size());
            assertEquals("SPICE_MEMBER_ID", ids.get(0).getIdentifierType());
        }

        @Test
        @DisplayName("address carries locality and ward boundary codes + phone + photo")
        void address() {
            Individual i = mapper.toIndividual(member(), LOC, WARD, TENANT);
            assertEquals("80684592", i.getMobileNumber());
            assertEquals("https://s3/consent/998357.JPEG", i.getPhoto());
            assertEquals(1, i.getAddress().size());
            assertEquals(LOC, i.getAddress().get(0).getLocality().getCode());
            assertEquals(WARD, i.getAddress().get(0).getWard().getCode());
        }
    }

    @Nested
    @DisplayName("toHouseholdMember")
    class ToHouseholdMember {
        @Test
        @DisplayName("head member -> isHeadOfHousehold true, no relationships")
        void head() {
            HouseholdMember hm = mapper.toHouseholdMember(member(), TENANT);
            assertTrue(hm.getIsHeadOfHousehold());
            assertNull(hm.getMemberRelationships());
            assertEquals("998357", hm.getId());
            assertEquals("998357", hm.getClientReferenceId());
            assertEquals("998359", hm.getHouseholdId());
            assertEquals("998359", hm.getHouseholdClientReferenceId());
            assertEquals("998357", hm.getIndividualId());
            assertEquals("998357", hm.getIndividualClientReferenceId());
        }

        @Test
        @DisplayName("non-head member -> isHeadOfHousehold false + relationshipType")
        void nonHead() {
            SpiceMember m = member();
            m.setHouseholdHeadRelationship("Son / Daughter");
            HouseholdMember hm = mapper.toHouseholdMember(m, TENANT);
            assertFalse(hm.getIsHeadOfHousehold());
            assertNotNull(hm.getMemberRelationships());
            assertEquals(1, hm.getMemberRelationships().size());
            assertEquals("Son / Daughter", hm.getMemberRelationships().get(0).getRelationshipType());
        }

        @Test
        @DisplayName("null relationship -> not head, no relationships")
        void nullRelationship() {
            SpiceMember m = member();
            m.setHouseholdHeadRelationship(null);
            HouseholdMember hm = mapper.toHouseholdMember(m, TENANT);
            assertFalse(hm.getIsHeadOfHousehold());
            assertNull(hm.getMemberRelationships());
        }
    }
}
