package org.egov.referralmanagement.ccn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.referralmanagement.ccn.bpp.CcnBppService;
import org.egov.referralmanagement.ccn.bpp.InboundProjectResolver;
import org.egov.referralmanagement.ccn.bpp.ServiceCoordinationMapper;
import org.egov.referralmanagement.ccn.client.CcnOnixClient;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.ccn.consumer.CcnHfReferralUpdateConsumer;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.egov.referralmanagement.ccn.repository.CcnReferralLinkRepository;
import org.egov.referralmanagement.repository.HFReferralRepository;
import org.egov.referralmanagement.service.HFReferralService;
import org.egov.referralmanagement.service.ReferralManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Full-chain (in-process) end-to-end for the referral-status push: the REAL consumer →
 * CcnBppService / CcnReferralService → mappers → status service. Only the outermost boundaries
 * (ONIX HTTP client + repositories) are mocked. Asserts the exact Beckn payload that would be sent
 * to SPICE — this is the same path the deployed service runs, minus Kafka/HTTP/DB transport.
 *
 * <p>Reproduces + guards the live bug: a link read back with a null last_payload must not crash the
 * push (responseContext synthesizes the context) and must still produce a well-formed on_update.</p>
 */
class CcnStatusFlowIntegrationTest {

    private CcnProperties props;
    private CcnOnixClient onix;
    private CcnReferralLinkRepository linkRepo;
    private final ObjectMapper om = new ObjectMapper();
    private CcnHfReferralUpdateConsumer consumer;

    // a realistic inbound Beckn confirm payload as stored in ccn_referral_link.last_payload
    private static final String INBOUND_PAYLOAD =
            "{\"context\":{\"networkId\":\"medtroniclabs.org/sandbox_ccn_reference_registry\",\"action\":\"confirm\","
          + "\"version\":\"2.0.0\",\"bapId\":\"bap.mdtlabs.org\",\"bapUri\":\"https://cc.mdtlabs.org/bap/receiver\","
          + "\"bppId\":\"sierraleone-hcm-dev.digit.org\",\"transactionId\":\"txn-1\"},"
          + "\"message\":{\"contract\":{\"id\":\"coord-in-1\",\"contractAttributes\":{\"coordinationId\":\"coord-in-1\"}}}}";

    @BeforeEach
    void setup() {
        props = new CcnProperties();
        props.setBppEnabled(true);
        props.setEnabled(true);
        props.setBapId("sierraleone-hcm-dev.digit.org");
        props.setBapUri("https://sierraleone-hcm-dev.digit.org/beckn");
        props.setNetworkId("medtroniclabs.org/sandbox_ccn_reference_registry");
        props.setDomain("health");
        props.setVersion("2.0.0");
        props.setInboundTenantId("sierraleone");

        onix = mock(CcnOnixClient.class);
        linkRepo = mock(CcnReferralLinkRepository.class);
        HFReferralService hfReferralService = mock(HFReferralService.class);
        HFReferralRepository hfReferralRepository = mock(HFReferralRepository.class);

        // REAL components
        CcnReferralStatusService statusService = new CcnReferralStatusService(
                props, hfReferralService, hfReferralRepository, linkRepo);
        ServiceCoordinationMapper bppMapper = new ServiceCoordinationMapper(props, om, statusService);
        HealthReferralMapper bapMapper = new HealthReferralMapper(props, om, statusService);
        CcnIdentityResolver identityResolver = mock(CcnIdentityResolver.class);
        CcnBppService bpp = new CcnBppService(props, bppMapper, onix, linkRepo,
                mock(ReferralManagementService.class), hfReferralService, mock(InboundProjectResolver.class),
                identityResolver, statusService, om);
        CcnReferralService referralService = new CcnReferralService(props, bapMapper, onix, linkRepo,
                identityResolver, statusService);

        consumer = new CcnHfReferralUpdateConsumer(bpp, referralService, linkRepo, statusService, props, om);
    }

    private String hfMessage(String coordId, String status, String reason, String modifiedBy, boolean inbound) throws Exception {
        java.util.List<Field> fields = new java.util.ArrayList<>();
        fields.add(Field.builder().key("referralStatus").value(status).build());
        if (reason != null) fields.add(Field.builder().key("referralStatusReason").value(reason).build());
        if (inbound) fields.add(Field.builder().key("ccnInbound").value("true").build());
        HFReferral hf = HFReferral.builder()
                .id("hf-1").tenantId("sierraleone").referralCode(coordId).beneficiaryId("0550142480970")
                .auditDetails(AuditDetails.builder().lastModifiedBy(modifiedBy).build())
                .additionalFields(AdditionalFields.builder().schema("HFReferral").version(1).fields(fields).build())
                .build();
        return om.writeValueAsString(List.of(hf));
    }

    @Test
    void inboundRejectWithReason_producesWellFormedOnUpdateToSpice() throws Exception {
        // link with a REAL stored inbound payload (the fixed RowMapper now returns this on reads)
        when(linkRepo.findByCoordinationId(eq("coord-in-1"), any())).thenReturn(
                CcnReferralLink.builder().coordinationId("coord-in-1").direction(CcnReferralLink.INBOUND)
                        .lastPayload(INBOUND_PAYLOAD).build());

        consumer.onHfReferralUpdate(hfMessage("coord-in-1", "REJECTED", "Patient outside catchment", "field-user", true),
                "sierraleone-update-hfreferral-topic");

        ArgumentCaptor<JsonNode> cap = ArgumentCaptor.forClass(JsonNode.class);
        verify(onix).sendBpp(eq("on_update"), cap.capture());
        JsonNode payload = cap.getValue();
        // context: flipped to on_update, carries SPICE routing from the inbound payload
        assertEquals("on_update", payload.at("/context/action").asText());
        assertEquals("bap.mdtlabs.org", payload.at("/context/bapId").asText());
        assertEquals("2.0.0", payload.at("/context/version").asText());
        // CCN displays from status.code: REJECTED -> wire CANCELLED; commitment descriptor CLOSED;
        // reason in contractAttributes.statusReason (NOT under status); lifecycleState carried too.
        assertEquals("coord-in-1", payload.at("/message/contract/id").asText());
        assertEquals("CANCELLED", payload.at("/message/contract/status/code").asText());
        assertTrue(payload.at("/message/contract/status/descriptor").isMissingNode());     // no descriptor under status
        assertTrue(payload.at("/message/contract/commitments").isArray()
                && payload.at("/message/contract/commitments").size() > 0);                 // required
        assertEquals("CLOSED", payload.at("/message/contract/commitments/0/status/descriptor/code").asText());
        assertEquals("CANCELLED", payload.at("/message/contract/contractAttributes/lifecycleState").asText());  // REJECTED->CANCELLED
        assertEquals("Patient outside catchment", payload.at("/message/contract/contractAttributes/statusReason").asText());
        assertFalse(payload.at("/message/contract/contractAttributes/targetCriteria/serviceCategory/code").isMissingNode());
        verify(linkRepo).updateState(eq("coord-in-1"), eq("REJECTED"), eq("publishResult"), anyLong(), any());
        verify(linkRepo).updatePostUpdateAck(eq("coord-in-1"), any(), any());               // ACK stored
    }

    @Test
    void inboundReject_withNullLastPayload_stillDispatches_synthesizedContext() throws Exception {
        // THE LIVE BUG: link read back with null last_payload — must not crash, must still dispatch
        when(linkRepo.findByCoordinationId(eq("coord-in-1"), any())).thenReturn(
                CcnReferralLink.builder().coordinationId("coord-in-1").direction(CcnReferralLink.INBOUND)
                        .lastPayload(null).build());

        assertDoesNotThrow(() -> consumer.onHfReferralUpdate(
                hfMessage("coord-in-1", "REJECTED", "dup", "field-user", true), "sierraleone-update-hfreferral-topic"));

        ArgumentCaptor<JsonNode> cap = ArgumentCaptor.forClass(JsonNode.class);
        verify(onix).sendBpp(eq("on_update"), cap.capture());
        JsonNode payload = cap.getValue();
        assertEquals("on_update", payload.at("/context/action").asText());
        assertEquals("sierraleone-hcm-dev.digit.org", payload.at("/context/bppId").asText());   // synthesized from config
        assertEquals("CANCELLED", payload.at("/message/contract/status/code").asText());
        assertEquals("CANCELLED", payload.at("/message/contract/contractAttributes/lifecycleState").asText());  // REJECTED->CANCELLED
    }

    @Test
    void outboundCancel_firesBapUpdateToSpice() throws Exception {
        when(linkRepo.findByCoordinationId(eq("coord-out-1"), any())).thenReturn(
                CcnReferralLink.builder().coordinationId("coord-out-1").direction(CcnReferralLink.OUTBOUND)
                        .transactionId("txn-out-1").beneficiaryId("0550142480970").tenantId("sierraleone").build());

        consumer.onHfReferralUpdate(hfMessage("coord-out-1", "CANCELLED", "Duplicate", "field-user", false),
                "sierraleone-update-hfreferral-topic");

        ArgumentCaptor<JsonNode> cap = ArgumentCaptor.forClass(JsonNode.class);
        verify(onix).send(eq("update"), cap.capture());     // BAP update action, not sendBpp
        JsonNode payload = cap.getValue();
        assertEquals("update", payload.at("/context/action").asText());
        assertEquals("coord-out-1", payload.at("/message/contract/id").asText());
        assertEquals("CANCELLED", payload.at("/message/contract/status/code").asText());     // CCN displays from status.code
        assertEquals("CANCELLED", payload.at("/message/contract/contractAttributes/lifecycleState").asText());
        assertFalse(payload.at("/message/contract/contractAttributes/targetCriteria/serviceCategory/code").isMissingNode());
        assertEquals("0550142480970", payload.at("/message/contract/participants/0/participantAttributes/healthIds/0/value").asText());
        verify(onix, never()).sendBpp(any(), any());
    }

    @Test
    void systemMirrorWrite_isNotPushedBack() throws Exception {
        // outbound HFReferral whose status we mirrored FROM SPICE (lastModifiedBy=ccn-system) must not bounce
        when(linkRepo.findByCoordinationId(any(), any())).thenReturn(
                CcnReferralLink.builder().coordinationId("coord-out-1").direction(CcnReferralLink.OUTBOUND).build());
        consumer.onHfReferralUpdate(hfMessage("coord-out-1", "COMPLETED", null, "ccn-system", false),
                "sierraleone-update-hfreferral-topic");
        verify(onix, never()).send(any(), any());
        verify(onix, never()).sendBpp(any(), any());
    }
}
