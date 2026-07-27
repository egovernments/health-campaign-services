package org.egov.referralmanagement.ccn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies Referral -> Beckn HealthReferral mapping (HCM downward, non-sensitive). */
class HealthReferralMapperTest {

    private HealthReferralMapper mapper;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setup() {
        CcnProperties p = new CcnProperties();
        p.setDomain("health");
        p.setVersion("2.0.0");
        p.setNetworkId("medtroniclabs.org/sandbox_ccn_reference_registry");
        p.setBapId("sierraleone-hcm-dev.digit.org");
        p.setBapUri("https://sierraleone-hcm-dev.digit.org/beckn");
        p.setBppId("bpp.mdtlabs.org");
        p.setBppUri("https://cc.mdtlabs.org/bpp/receiver");
        mapper = new HealthReferralMapper(p, om);
    }

    private Referral sample() {
        return Referral.builder()
                .projectBeneficiaryClientReferenceId("0690003741962")   // SPICE patientId rides here
                .referrerId("chw-user-42")
                .reasons(List.of("FEVER"))
                .referralCode("REF-0007")
                .build();
    }

    @Test
    void spicePatientIdComesFromBeneficiaryId() {
        assertEquals("0690003741962", HealthReferralMapper.spicePatientId(sample()));
    }

    @Test
    void confirmIsNonSensitiveAndDownward() {
        JsonNode c = mapper.confirm(sample(), "tx-1", "coord-1", "0690003741962");

        assertEquals("confirm", c.at("/context/action").asText());
        assertEquals("bpp.mdtlabs.org", c.at("/context/bppId").asText());
        assertEquals("coord-1", c.at("/message/contract/id").asText());
        assertEquals("ACTIVE", c.at("/message/contract/status/code").asText());

        // patient participant carries ONLY the SPICE patientId — no name/gender/DOB
        JsonNode patient = c.at("/message/contract/participants/0");
        assertEquals("PATIENT", patient.at("/participantAttributes/participantRole").asText());
        assertEquals("SPICE_PATIENT_ID", patient.at("/participantAttributes/healthIds/0/system").asText());
        assertEquals("0690003741962", patient.at("/participantAttributes/healthIds/0/value").asText());
        assertTrue(patient.at("/descriptor/name").isMissingNode(), "no patient name (non-sensitive)");
        assertTrue(patient.at("/participantAttributes/gender").isMissingNode(), "no gender (non-sensitive)");

        // downward marker + coded intent
        JsonNode ca = c.at("/message/contract/contractAttributes");
        assertEquals("hrf:HealthReferral", ca.at("/@type").asText());
        assertEquals("HOME_VISIT", ca.at("/targetCriteria/procedureNeeds/0").asText());
        assertEquals("CONSULTATION", ca.at("/targetCriteria/serviceCategory/code").asText());
        assertEquals("URGENT", ca.at("/clinicalUrgencyTier").asText()); // reasons present

        // no fee/consideration, no village (no slot yet)
        assertTrue(c.at("/message/contract/consideration").isMissingNode(), "no consideration");
        assertFalse(c.toString().contains("village"), "no villageId until SPICE provides a field");
    }

    @Test
    void selectHasNoContractIdAndIsDraft() {
        JsonNode s = mapper.select(sample(), "tx-1", "coord-1", "0690003741962");
        assertTrue(s.at("/message/contract/id").isMissingNode());
        assertEquals("DRAFT", s.at("/message/contract/contractAttributes/lifecycleState").asText());
    }

    @Test
    void statusIsMinimalQuery() {
        JsonNode st = mapper.status(sample(), "tx-1", "coord-1", "0690003741962");
        assertEquals("status", st.at("/context/action").asText());
        assertEquals("coord-1", st.at("/message/contract/id").asText());
    }
}
