package org.egov.referralmanagement.ccn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.referralmanagement.ccn.client.CcnOnixClient;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.ccn.consumer.CcnReferralConsumer;
import org.egov.referralmanagement.ccn.repository.CcnReferralLinkRepository;
import org.egov.referralmanagement.ccn.web.CcnCallbackController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end flow test for the CCN referral forwarder:
 * consumer -> service -> mapper -> ONIX client. ONIX is mocked (it's the separate node).
 */
class CcnReferralFlowTest {

    private CcnProperties props;
    private CcnOnixClient onix;
    private CcnReferralService service;
    private CcnReferralLinkRepository linkRepo;
    private ObjectMapper om;

    @BeforeEach
    void setup() {
        om = new ObjectMapper();
        props = new CcnProperties();
        props.setEnabled(true);
        props.setDomain("health");
        props.setVersion("2.0.0");
        props.setNetworkId("test-net");
        props.setBapId("sierraleone-hcm-dev.digit.org");
        props.setBapUri("https://sierraleone-hcm-dev.digit.org/beckn");
        props.setBppId("comemr-np-spice-001");
        props.setBppUri("https://spice.example.org/beckn");
        props.setOnixCallerUrl("http://onix-bap-hcm:8080/bap/caller/");

        onix = mock(CcnOnixClient.class);
        when(onix.send(anyString(), any(JsonNode.class)))
                .thenReturn(om.createObjectNode().put("status", "ACK"));

        linkRepo = mock(CcnReferralLinkRepository.class);
        HealthReferralMapper mapper = new HealthReferralMapper(props, om);
        service = new CcnReferralService(props, mapper, onix, linkRepo);
    }

    private HFReferral sample() {
        return HFReferral.builder()
                .id("hfref-id-1")
                .beneficiaryId("BEN-123")
                .symptom("Fever, RDT+")
                .referralCode("HFREF-0007")
                .projectFacilityId("PHU-01")
                .nationalLevelId("SL0001234")
                .build();
    }

    @Test
    void forwardSendsSelectInitConfirmInOrderWithConsistentIds() {
        service.forward(sample());

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(onix, times(3)).send(action.capture(), payload.capture());

        // exactly select -> init -> confirm, in order
        assertEquals(Arrays.asList("select", "init", "confirm"), action.getAllValues());

        List<JsonNode> bodies = payload.getAllValues();
        // same transactionId across all three
        String txn = bodies.get(0).at("/context/transactionId").asText();
        assertFalse(txn.isEmpty());
        assertEquals(txn, bodies.get(1).at("/context/transactionId").asText());
        assertEquals(txn, bodies.get(2).at("/context/transactionId").asText());

        // same coordinationId across all three
        String coord = bodies.get(0).at("/message/contract/contractAttributes/coordinationId").asText();
        assertFalse(coord.isEmpty());
        assertEquals(coord, bodies.get(1).at("/message/contract/contractAttributes/coordinationId").asText());
        assertEquals(coord, bodies.get(2).at("/message/contract/contractAttributes/coordinationId").asText());

        // action-correct payloads
        assertEquals("select", bodies.get(0).at("/context/action").asText());
        assertEquals("DRAFT", bodies.get(0).at("/message/contract/contractAttributes/lifecycleState").asText());
        assertEquals("confirm", bodies.get(2).at("/context/action").asText());
        assertEquals("ACTIVE", bodies.get(2).at("/message/contract/contractAttributes/lifecycleState").asText());
        // beneficiary carried through
        assertEquals("BEN-123",
                bodies.get(2).at("/message/contract/participants/0/participantAttributes/healthIds/0/value").asText());
    }

    @Test
    void disabledFlagSendsNothing() {
        props.setEnabled(false);
        service.forward(sample());
        verifyNoInteractions(onix);
    }

    @Test
    void onixFailureDoesNotPropagate() {
        // isolation: a downstream failure must never break the referral path
        when(onix.send(eq("init"), any())).thenThrow(new RuntimeException("ONIX down"));
        assertDoesNotThrow(() -> service.forward(sample()));
        verify(onix, times(1)).send(eq("select"), any());
        verify(onix, times(1)).send(eq("init"), any());
        verify(onix, never()).send(eq("confirm"), any()); // stopped after init failed
    }

    @Test
    void consumerForwardsEachReferralInBulk() throws Exception {
        CcnReferralService svc = mock(CcnReferralService.class);
        CcnReferralConsumer consumer = new CcnReferralConsumer(svc, om);

        // real save-topic payload = JSON ARRAY of HFReferral (List<HFReferral>)
        String jsonArray = om.writeValueAsString(Arrays.asList(sample(),
                HFReferral.builder().id("hfref-id-2").beneficiaryId("BEN-999").build()));

        consumer.onHFReferralCreate(jsonArray, "save-hfreferral-topic");

        verify(svc, times(2)).forward(any(HFReferral.class));
    }

    @Test
    void consumerParsesArrayPayload() throws Exception {
        CcnReferralConsumer consumer = new CcnReferralConsumer(mock(CcnReferralService.class), om);
        String jsonArray = om.writeValueAsString(Arrays.asList(sample()));
        List<HFReferral> parsed = consumer.parse(jsonArray);
        assertEquals(1, parsed.size());
        assertEquals("BEN-123", parsed.get(0).getBeneficiaryId());
    }

    @Test
    void consumerSwallowsBadPayload() {
        CcnReferralService svc = mock(CcnReferralService.class);
        CcnReferralConsumer consumer = new CcnReferralConsumer(svc, om);
        // malformed record must not throw (never disrupt persist flow)
        assertDoesNotThrow(() ->
                consumer.onHFReferralCreate("{ not valid json ]", "save-hfreferral-topic"));
        verifyNoInteractions(svc);
    }

    // ── Acknowledgement / return leg (Option A: correlation table only) ──────────

    @Test
    void forwardPersistsAndUpdatesTheLink() {
        service.forward(sample());
        // link saved up-front (INITIATED) with the originating HFReferral id + beneficiary
        ArgumentCaptor<org.egov.referralmanagement.ccn.model.CcnReferralLink> saved =
                ArgumentCaptor.forClass(org.egov.referralmanagement.ccn.model.CcnReferralLink.class);
        verify(linkRepo).save(saved.capture());
        assertEquals("hfref-id-1", saved.getValue().getHfReferralId());
        assertEquals("BEN-123", saved.getValue().getBeneficiaryId());
        assertEquals("INITIATED", saved.getValue().getLifecycleState());
        // after a clean send it is marked SENT (real lifecycle arrives async via on_confirm)
        verify(linkRepo).updateState(eq(saved.getValue().getCoordinationId()), eq("SENT"), eq("confirm"), anyLong());
    }

    @Test
    void onConfirmCallbackUpdatesLinkLifecycle() throws Exception {
        CcnCallbackController cb = new CcnCallbackController(linkRepo, om);
        when(linkRepo.updateState(anyString(), anyString(), anyString(), anyLong())).thenReturn(1);
        String body = "{\"context\":{\"action\":\"on_confirm\"},\"message\":{\"contract\":{\"id\":\"coord-9\","
                + "\"contractAttributes\":{\"coordinationId\":\"coord-9\",\"lifecycleState\":\"ACTIVE\"}}}}";
        var resp = cb.onConfirm(body);
        assertEquals("ACK", resp.getBody().at("/message/ack/status").asText());
        verify(linkRepo).updateState(eq("coord-9"), eq("ACTIVE"), eq("on_confirm"), anyLong());
    }

    @Test
    void onConfirmCallbackToleratesUnknownCoordinationId() {
        CcnCallbackController cb = new CcnCallbackController(linkRepo, om);
        when(linkRepo.updateState(anyString(), anyString(), anyString(), anyLong())).thenReturn(0);
        var resp = cb.onStatus("{\"message\":{\"contract\":{\"id\":\"nope\",\"contractAttributes\":{\"lifecycleState\":\"CLOSED\"}}}}");
        assertEquals("ACK", resp.getBody().at("/message/ack/status").asText()); // still ACKs
    }
}
