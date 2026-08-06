package org.egov.referralmanagement.ccn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralRequest;
import org.egov.referralmanagement.ccn.CcnIdentityResolver;
import org.egov.referralmanagement.ccn.CcnReferralStatusService;
import org.egov.referralmanagement.ccn.bpp.CcnBppService;
import org.egov.referralmanagement.ccn.bpp.InboundProjectResolver;
import org.egov.referralmanagement.ccn.bpp.ServiceCoordinationMapper;
import org.egov.referralmanagement.ccn.client.CcnOnixClient;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.egov.referralmanagement.ccn.repository.CcnReferralLinkRepository;
import org.egov.referralmanagement.repository.HFReferralRepository;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.common.models.core.Field;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralRequest;
import org.egov.referralmanagement.service.HFReferralService;
import org.egov.referralmanagement.service.ReferralManagementService;

import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Receive flow (UC3, BPP side): inbound discover/confirm from SPICE. */
class CcnBppFlowTest {

    private CcnProperties props;
    private CcnOnixClient onix;
    private CcnReferralLinkRepository linkRepo;
    private ReferralManagementService referralService;
    private HFReferralService hfReferralService;
    private CcnBppService bpp;
    private CcnIdentityResolver identityResolver;
    private InboundProjectResolver resolver;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setup() {
        props = new CcnProperties();
        props.setBppEnabled(true);
        props.setBapId("sierraleone-hcm-dev.digit.org");
        props.setOnixBppCallerUrl("http://onix-bap-hcm:8080/bpp/caller/");
        props.setInboundTenantId("sl");
        props.setInboundProjectId("proj-1");
        onix = mock(CcnOnixClient.class);
        linkRepo = mock(CcnReferralLinkRepository.class);
        referralService = mock(ReferralManagementService.class);
        when(referralService.create(any(ReferralRequest.class)))
                .thenReturn(Referral.builder().id("created-ref-1").build());
        when(referralService.createSkippingValidation(any(ReferralRequest.class)))
                .thenReturn(Referral.builder().id("created-ref-skip-1").build());
        // Resolver keys on the individual identifier (UNIQUE_BENEFICIARY_ID) → its beneficiary's project.
        // Default: resolves to proj-1 (individual/beneficiary found). Unresolved cases re-stub per test.
        resolver = mock(InboundProjectResolver.class);
        when(resolver.resolve(any(), any())).thenAnswer(inv ->
                new InboundProjectResolver.Resolution("proj-1", "pb-1", inv.getArgument(0), true));
        // Real identity resolver: reading/writing additionalFields[abhaId] needs no HCM client calls.
        identityResolver = new CcnIdentityResolver(null, mock(ReferralManagementConfiguration.class), props);
        ServiceCoordinationMapper mapper = new ServiceCoordinationMapper(props, om);
        hfReferralService = mock(HFReferralService.class);
        when(hfReferralService.create(any(HFReferralRequest.class)))
                .thenAnswer(inv -> {
                    HFReferral in = ((HFReferralRequest) inv.getArgument(0)).getHfReferral();
                    in.setId("hf-created-1");
                    return in;
                });
        CcnReferralStatusService statusService = new CcnReferralStatusService(
                props, hfReferralService, mock(HFReferralRepository.class), linkRepo);
        bpp = new CcnBppService(props, mapper, onix, linkRepo, referralService, hfReferralService, resolver, identityResolver, statusService, om);
    }

    private JsonNode inbound(String action) throws Exception {
        return om.readTree("{\"context\":{\"action\":\"" + action + "\",\"bapId\":\"comemr-np-spice-001\","
                + "\"bppId\":\"sierraleone-hcm-dev.digit.org\",\"transactionId\":\"t1\"},"
                + "\"message\":{\"contract\":{\"id\":\"coord-in-1\",\"contractAttributes\":{"
                + "\"@type\":\"scoord:ServiceCoordination\",\"coordinationId\":\"coord-in-1\",\"lifecycleState\":\"ACTIVE\","
                + "\"targetCriteria\":{\"serviceCategory\":{\"code\":\"FIELD_DATA_COLLECTION\"}}},"
                + "\"participants\":[{\"participantAttributes\":{\"participantRole\":\"PATIENT\","
                + "\"healthIds\":[{\"system\":\"ABHA\",\"value\":\"0690003741962\"}]}}]}}}");
    }

    @Test
    void discoverDispatchesOnDiscover() throws Exception {
        bpp.handle("discover", inbound("discover"));
        verify(onix).sendBpp(eq("on_discover"), any(JsonNode.class));
    }

    @Test
    void confirmCreatesHfReferralAndStoresInboundLink() throws Exception {
        // HFReferral-only: confirm creates ONLY the autofilled HFReferral (normal Referral is not used).
        props.setInboundHfFacilityId("PF-1");
        props.setInboundHfProjectId("proj-1");

        bpp.handle("confirm", inbound("confirm"));

        // never touches the normal Referral flow
        verify(referralService, never()).create(any());
        verify(referralService, never()).createSkippingValidation(any());

        // stores an INBOUND link pointing at the created HFReferral
        ArgumentCaptor<CcnReferralLink> link = ArgumentCaptor.forClass(CcnReferralLink.class);
        verify(linkRepo).save(link.capture());
        assertEquals(CcnReferralLink.INBOUND, link.getValue().getDirection());
        assertEquals("BPP", link.getValue().getLocalRole());
        assertEquals("hf-created-1", link.getValue().getHfReferralId());   // links to the HFReferral, not a Referral
        assertEquals("comemr-np-spice-001", link.getValue().getInitiatorSubscriberId());
        verify(onix).sendBpp(eq("on_confirm"), any(JsonNode.class));
    }

    @Test
    void inboundConfirmNeverBreaksDispatchWhenHfReferralCreateThrows() throws Exception {
        // even if the HFReferral create blows up, on_confirm must still be dispatched (guarded)
        props.setInboundHfFacilityId("PF-1");
        when(hfReferralService.create(any())).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> bpp.handle("confirm", inbound("confirm")));
        verify(onix).sendBpp(eq("on_confirm"), any(JsonNode.class));
    }

    @Test
    void initAfterSelectDoesNotNpeWhenExistingCreatedTimeIsNull() throws Exception {
        // Regression: on init the link already exists (from select). If it's read back with a null
        // createdTime, the ternary `existing.getCreatedTime() : now` numeric-promotes to long and
        // unboxes null -> NPE. Guard must fall back to `now`.
        when(linkRepo.findByCoordinationId(eq("coord-in-1"), any())).thenReturn(
                CcnReferralLink.builder().coordinationId("coord-in-1")
                        .direction(CcnReferralLink.INBOUND).createdTime(null).build());
        assertDoesNotThrow(() -> bpp.handle("init", inbound("init")));

        ArgumentCaptor<CcnReferralLink> link = ArgumentCaptor.forClass(CcnReferralLink.class);
        verify(linkRepo).save(link.capture());
        assertNotNull(link.getValue().getCreatedTime(), "createdTime must fall back to now, not stay null");
        verify(onix).sendBpp(eq("on_init"), any(JsonNode.class));
    }

    @Test
    void disabledDoesNothing() throws Exception {
        props.setBppEnabled(false);
        bpp.handle("confirm", inbound("confirm"));
        verifyNoInteractions(onix, referralService, linkRepo);
    }

    @Test
    void publishResultPushesOnUpdateWhenInbound() {
        when(linkRepo.findByCoordinationId(eq("coord-in-1"), any())).thenReturn(
                CcnReferralLink.builder().coordinationId("coord-in-1").direction(CcnReferralLink.INBOUND)
                        .lastPayload("{\"context\":{\"action\":\"confirm\"}}").build());
        // HF worker accepted the inbound referral → pushed back to SPICE via on_update (the update API).
        bpp.publishResult("coord-in-1", "ACCEPTED");
        verify(onix).sendBpp(eq("on_update"), any(JsonNode.class));
        verify(linkRepo).updateState(eq("coord-in-1"), eq("ACCEPTED"), eq("publishResult"), anyLong(), any());
    }

    @Test
    void publishResultIgnoresOutboundCoordination() {
        // an OUTBOUND coordination is SPICE's to drive — HCM never pushes status back on it.
        when(linkRepo.findByCoordinationId(eq("coord-out-1"), any())).thenReturn(
                CcnReferralLink.builder().coordinationId("coord-out-1").direction(CcnReferralLink.OUTBOUND).build());
        bpp.publishResult("coord-out-1", "ACCEPTED");
        verify(onix, never()).sendBpp(any(), any());
    }

    @Test
    void inboundConfirmAlsoCreatesAutofilledHfReferral() throws Exception {
        // T2: with a configured facility, confirm ALSO creates a validated HFReferral for the HF worker.
        props.setInboundHfFacilityId("PF-1");
        props.setInboundHfProjectId("proj-1");

        bpp.handle("confirm", inbound("confirm"));

        ArgumentCaptor<HFReferralRequest> cap = ArgumentCaptor.forClass(HFReferralRequest.class);
        verify(hfReferralService).create(cap.capture());
        HFReferral hf = cap.getValue().getHfReferral();
        assertEquals("proj-1", hf.getProjectId());               // downsyncs by projectId
        assertEquals("PF-1", hf.getProjectFacilityId());         // valid project-facility (validated)
        assertEquals("SICK", hf.getSymptom());                   // hardcoded referral reason
        assertEquals("0690003741962", hf.getBeneficiaryId());    // ABHA from inbound PATIENT participant
        assertEquals("coord-in-1", hf.getReferralCode());        // cross-ref to the coordination

        Map<String, String> af = hf.getAdditionalFields().getFields().stream()
                .collect(Collectors.toMap(Field::getKey, Field::getValue, (a, b) -> a));
        assertEquals("FEMALE", af.get("gender"));                // hardcoded gender
        assertEquals("2", af.get("cycle"));                      // hardcoded current cycle
        assertEquals("true", af.get("ccnInbound"));              // loop-guard marker (won't re-forward)
        assertEquals("RECEIVED", af.get("referralStatus"));      // initial status (worker moves to ACCEPTED/…)
    }

    @Test
    void inboundHfReferralSkippedWhenNoFacilityConfigured() throws Exception {
        // Default setup has no inboundHfFacilityId → HFReferral creation self-skips (no NPE, no create).
        bpp.handle("confirm", inbound("confirm"));
        verify(hfReferralService, never()).create(any(HFReferralRequest.class));
    }
}
