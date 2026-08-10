package org.egov.referralmanagement.ccn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.referralmanagement.ccn.CcnReferralStatusService;
import org.egov.referralmanagement.ccn.client.CcnOnixClient;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.ccn.consumer.CcnReferralConsumer;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.egov.referralmanagement.ccn.repository.CcnReferralLinkRepository;
import org.egov.referralmanagement.ccn.web.CcnCallbackController;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end flow test for the CCN referral forwarder (normal Referral flow):
 * consumer -> service -> mapper -> ONIX client. ONIX is mocked (the separate node).
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
        props.setNetworkId("medtroniclabs.org/sandbox_ccn_reference_registry");
        props.setBapId("sierraleone-hcm-dev.digit.org");
        props.setBapUri("https://sierraleone-hcm-dev.digit.org/beckn");
        props.setBppId("bpp.mdtlabs.org");
        props.setBppUri("https://cc.mdtlabs.org/bpp/receiver");
        props.setOnixCallerUrl("http://onix-bap-hcm:8080/bap/caller/");

        onix = mock(CcnOnixClient.class);
        when(onix.send(anyString(), any(JsonNode.class))).thenReturn(om.createObjectNode().put("status", "ACK"));
        linkRepo = mock(CcnReferralLinkRepository.class);
        HealthReferralMapper mapper = new HealthReferralMapper(props, om, new CcnReferralStatusService(props, null, null, null));
        // Real identity resolver; sample() carries the canonical id in additionalFields, so the
        // outbound resolve short-circuits at step 1 and no HCM client is called here.
        CcnIdentityResolver identityResolver =
                new CcnIdentityResolver(null, mock(ReferralManagementConfiguration.class), props);
        service = new CcnReferralService(props, mapper, onix, linkRepo, identityResolver,
                mock(CcnReferralStatusService.class));
    }

    private Referral sample() {
        return Referral.builder()
                .id("ref-id-1")
                .projectBeneficiaryClientReferenceId("pb-client-ref-1")   // project-beneficiary link (NOT the ABHA value)
                .additionalFields(AdditionalFields.builder()
                        .fields(List.of(Field.builder().key("abhaId").value("0690003741962").build())) // canonical UNIQUE_BENEFICIARY_ID
                        .build())
                .referrerId("chw-user-42")
                .reasons(List.of("FEVER"))
                .referralCode("REF-0007")
                .build();
    }

    @Test
    void forwardSendsSelectInitConfirmStatusInOrder() {
        service.forward(sample());
        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(onix, times(4)).send(action.capture(), payload.capture());
        assertEquals(Arrays.asList("select", "init", "confirm", "status"), action.getAllValues());

        List<JsonNode> b = payload.getAllValues();
        String txn = b.get(0).at("/context/transactionId").asText();
        String coord = b.get(2).at("/message/contract/contractAttributes/coordinationId").asText();
        assertFalse(txn.isEmpty());
        assertFalse(coord.isEmpty());
        // patientId sent, no PII
        assertEquals("0690003741962",
                b.get(2).at("/message/contract/participants/0/participantAttributes/healthIds/0/value").asText());
    }

    @Test
    void forwardPersistsLinkWithSpicePatientId() {
        service.forward(sample());
        ArgumentCaptor<CcnReferralLink> saved = ArgumentCaptor.forClass(CcnReferralLink.class);
        verify(linkRepo).save(saved.capture());
        assertEquals("ref-id-1", saved.getValue().getHfReferralId());
        assertEquals("0690003741962", saved.getValue().getBeneficiaryId()); // the SPICE patientId sent
        assertEquals("INITIATED", saved.getValue().getLifecycleState());
        verify(linkRepo).updateState(eq(saved.getValue().getCoordinationId()), eq("SENT"), eq("confirm"), anyLong(), any());
    }

    @Test
    void disabledFlagSendsNothing() {
        props.setEnabled(false);
        service.forward(sample());
        verifyNoInteractions(onix);
    }

    @Test
    void skipsWhenNoSpicePatientId() {
        Referral r = Referral.builder().id("ref-x").build(); // no beneficiary id
        service.forward(r);
        verifyNoInteractions(onix);
    }

    @Test
    void consumerForwardsEachReferralInBulk() throws Exception {
        CcnReferralService svc = mock(CcnReferralService.class);
        CcnReferralConsumer consumer = new CcnReferralConsumer(svc, om);
        String jsonArray = om.writeValueAsString(Arrays.asList(sample(),
                Referral.builder().id("ref-id-2").projectBeneficiaryClientReferenceId("SL999").build()));
        consumer.onReferralCreate(jsonArray, "save-referral-topic");
        verify(svc, times(2)).forward(any(Referral.class));
    }

    @Test
    void consumerSwallowsBadPayload() {
        CcnReferralService svc = mock(CcnReferralService.class);
        CcnReferralConsumer consumer = new CcnReferralConsumer(svc, om);
        assertDoesNotThrow(() -> consumer.onReferralCreate("{ not valid ]", "save-referral-topic"));
        verifyNoInteractions(svc);
    }

    @Test
    void onConfirmCallbackUpdatesLink() {
        CcnCallbackController cb = new CcnCallbackController(linkRepo, mock(CcnReferralStatusService.class), om);
        when(linkRepo.updateState(anyString(), anyString(), anyString(), anyLong(), any())).thenReturn(1);
        String body = "{\"message\":{\"contract\":{\"id\":\"coord-9\",\"contractAttributes\":{\"coordinationId\":\"coord-9\",\"lifecycleState\":\"ACTIVE\"}}}}";
        var resp = cb.onConfirm(body);
        assertEquals("ACK", resp.getBody().at("/message/ack/status").asText());
        verify(linkRepo).updateState(eq("coord-9"), eq("ACTIVE"), eq("on_confirm"), anyLong(), any());
    }
}
