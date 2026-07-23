package org.egov.referralmanagement.ccn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies HFReferral -> Beckn HealthReferral mapping (CCN UC1 shape). */
class HealthReferralMapperTest {

    private HealthReferralMapper mapper;

    @BeforeEach
    void setup() {
        CcnProperties p = new CcnProperties();
        p.setDomain("health");
        p.setVersion("2.0.0");
        p.setNetworkId("test-net");
        p.setBapId("sierraleone-hcm-dev.digit.org");
        p.setBapUri("https://sierraleone-hcm-dev.digit.org/beckn");
        p.setBppId("comemr-np-spice-001");
        p.setBppUri("https://spice.example.org/beckn");
        mapper = new HealthReferralMapper(p, new ObjectMapper());
    }

    private HFReferral sample() {
        return HFReferral.builder()
                .beneficiaryId("BEN-123")
                .symptom("Fever, RDT+")
                .symptomSurveyId("SURVEY-9")
                .referralCode("HFREF-0007")
                .projectFacilityId("PHU-01")
                .nationalLevelId("SL0001234")
                .build();
    }

    @Test
    void confirmProducesHealthReferralShape() {
        JsonNode c = mapper.confirm(sample(), "txn-1", "coord-1");

        assertEquals("confirm", c.at("/context/action").asText());
        assertEquals("2.0.0", c.at("/context/version").asText());
        assertEquals("sierraleone-hcm-dev.digit.org", c.at("/context/bapId").asText());
        assertEquals("comemr-np-spice-001", c.at("/context/bppId").asText());

        assertEquals("coord-1", c.at("/message/contract/id").asText());
        assertEquals("ACTIVE", c.at("/message/contract/status/code").asText());

        JsonNode ca = c.at("/message/contract/contractAttributes");
        assertEquals("hrf:HealthReferral", ca.at("/@type").asText());
        assertEquals("ACTIVE", ca.at("/lifecycleState").asText());
        assertEquals("HFREF-0007", ca.at("/referralCode").asText());
        assertEquals("PHU-01", ca.at("/referringFacilityId").asText());
        assertEquals("Fever, RDT+", ca.at("/targetCriteria/reason/display").asText());
        // confirm-only: referralNote pointer present
        assertEquals("ACTIVE", ca.at("/referralNote/revocationStatus").asText());
        assertEquals("SURVEY-9", ca.at("/referralNote/symptomSurveyRef").asText());

        // patient participant carries beneficiary + national id
        JsonNode patient = c.at("/message/contract/participants/0");
        assertEquals("PATIENT", patient.at("/participantAttributes/participantRole").asText());
        assertEquals("BEN-123", patient.at("/participantAttributes/healthIds/0/value").asText());
        assertEquals("SL0001234", patient.at("/participantAttributes/healthIds/1/value").asText());
    }

    @Test
    void selectIsDraftWithoutReferralNote() {
        JsonNode s = mapper.select(sample(), "txn-1", "coord-1");
        JsonNode ca = s.at("/message/contract/contractAttributes");
        assertEquals("DRAFT", ca.at("/lifecycleState").asText());
        assertTrue(ca.at("/referralNote").isMissingNode(), "select must not carry referralNote");
        // select omits contract.id (not yet issued)
        assertTrue(s.at("/message/contract/id").isMissingNode());
    }
}
