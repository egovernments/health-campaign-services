package org.egov.referralmanagement.ccn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralRequest;
import org.egov.referralmanagement.ccn.bpp.CcnBppService;
import org.egov.referralmanagement.ccn.bpp.ServiceCoordinationMapper;
import org.egov.referralmanagement.ccn.client.CcnOnixClient;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.egov.referralmanagement.ccn.repository.CcnReferralLinkRepository;
import org.egov.referralmanagement.service.ReferralManagementService;
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
    private CcnBppService bpp;
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
        ServiceCoordinationMapper mapper = new ServiceCoordinationMapper(props, om);
        bpp = new CcnBppService(props, mapper, onix, linkRepo, referralService, om);
    }

    private JsonNode inbound(String action) throws Exception {
        return om.readTree("{\"context\":{\"action\":\"" + action + "\",\"bapId\":\"comemr-np-spice-001\","
                + "\"bppId\":\"sierraleone-hcm-dev.digit.org\",\"transactionId\":\"t1\"},"
                + "\"message\":{\"contract\":{\"id\":\"coord-in-1\",\"contractAttributes\":{"
                + "\"@type\":\"scoord:ServiceCoordination\",\"coordinationId\":\"coord-in-1\",\"lifecycleState\":\"ACTIVE\","
                + "\"targetCriteria\":{\"serviceCategory\":{\"code\":\"FIELD_DATA_COLLECTION\"}}},"
                + "\"participants\":[{\"participantAttributes\":{\"participantRole\":\"PATIENT\","
                + "\"healthIds\":[{\"system\":\"SPICE_PATIENT_ID\",\"value\":\"0690003741962\"}]}}]}}}");
    }

    @Test
    void discoverDispatchesOnDiscover() throws Exception {
        bpp.handle("discover", inbound("discover"));
        verify(onix).sendBpp(eq("on_discover"), any(JsonNode.class));
    }

    @Test
    void confirmCreatesReferralAndStoresInboundLink() throws Exception {
        bpp.handle("confirm", inbound("confirm"));

        // creates a normal Referral (validated, in-process) with the SPICE patientId on beneficiary id
        ArgumentCaptor<ReferralRequest> req = ArgumentCaptor.forClass(ReferralRequest.class);
        verify(referralService).create(req.capture());
        assertEquals("0690003741962", req.getValue().getReferral().getProjectBeneficiaryClientReferenceId());
        assertEquals("coord-in-1", req.getValue().getReferral().getReferralCode());

        // stores an INBOUND link linked to the created referral
        ArgumentCaptor<CcnReferralLink> link = ArgumentCaptor.forClass(CcnReferralLink.class);
        verify(linkRepo).save(link.capture());
        assertEquals(CcnReferralLink.INBOUND, link.getValue().getDirection());
        assertEquals("BPP", link.getValue().getLocalRole());
        assertEquals("created-ref-1", link.getValue().getHfReferralId());
        assertEquals("comemr-np-spice-001", link.getValue().getInitiatorSubscriberId());

        verify(onix).sendBpp(eq("on_confirm"), any(JsonNode.class));
    }

    @Test
    void disabledDoesNothing() throws Exception {
        props.setBppEnabled(false);
        bpp.handle("confirm", inbound("confirm"));
        verifyNoInteractions(onix, referralService, linkRepo);
    }

    @Test
    void publishResultSendsClosedWhenInbound() {
        when(linkRepo.findByCoordinationId("coord-in-1")).thenReturn(
                CcnReferralLink.builder().coordinationId("coord-in-1").direction(CcnReferralLink.INBOUND)
                        .lastPayload("{\"context\":{\"action\":\"confirm\"}}").build());
        bpp.publishResult("coord-in-1", "CLOSED");
        verify(onix).sendBpp(eq("on_status"), any(JsonNode.class));
        verify(linkRepo).updateState(eq("coord-in-1"), eq("CLOSED"), eq("publishResult"), anyLong());
    }
}
