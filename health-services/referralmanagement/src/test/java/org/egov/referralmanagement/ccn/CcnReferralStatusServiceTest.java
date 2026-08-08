package org.egov.referralmanagement.ccn;

import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralRequest;
import org.egov.common.models.core.SearchResponse;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.egov.referralmanagement.ccn.repository.CcnReferralLinkRepository;
import org.egov.referralmanagement.repository.HFReferralRepository;
import org.egov.referralmanagement.service.HFReferralService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for the HFReferral referral-status helper (mirror + config vocabulary). */
class CcnReferralStatusServiceTest {

    private CcnProperties props;
    private HFReferralService hfReferralService;
    private HFReferralRepository hfReferralRepository;
    private CcnReferralLinkRepository linkRepo;
    private CcnReferralStatusService svc;

    @BeforeEach
    void setup() {
        props = new CcnProperties();
        hfReferralService = mock(HFReferralService.class);
        hfReferralRepository = mock(HFReferralRepository.class);
        linkRepo = mock(CcnReferralLinkRepository.class);
        svc = new CcnReferralStatusService(props, hfReferralService, hfReferralRepository, linkRepo);
    }

    @Test
    void configVocabularyParsesDefaults() {
        assertTrue(svc.isMirroredState("COMPLETED"));
        assertTrue(svc.isMirroredState("cancelled"));           // case-insensitive
        assertFalse(svc.isMirroredState("SOMETHING_ELSE"));
        assertTrue(svc.isForwardableStatus("ACCEPTED"));
        assertFalse(svc.isForwardableStatus("RECEIVED"));       // initial state is not pushed back
        // ccnLifecycleFor: reject & cancel both -> CANCELLED; resolve -> COMPLETED; unknown passes through
        assertEquals("CANCELLED", svc.ccnLifecycleFor("REJECTED"));
        assertEquals("CANCELLED", svc.ccnLifecycleFor("CANCELLED"));
        assertEquals("ACCEPTED", svc.ccnLifecycleFor("ACCEPTED"));
        assertEquals("COMPLETED", svc.ccnLifecycleFor("RESOLVED"));
        assertEquals("UNKNOWN", svc.ccnLifecycleFor("UNKNOWN"));  // passthrough
    }

    @Test
    void writeStatusReplacesExistingField() {
        HFReferral hf = HFReferral.builder()
                .additionalFields(AdditionalFields.builder().schema("HFReferral").version(1)
                        .fields(new java.util.ArrayList<>(List.of(
                                Field.builder().key("referralStatus").value("RECEIVED").build(),
                                Field.builder().key("gender").value("FEMALE").build())))
                        .build())
                .build();
        svc.writeStatus(hf, "ACCEPTED");
        assertEquals("ACCEPTED", svc.readStatus(hf));
        long count = hf.getAdditionalFields().getFields().stream()
                .filter(f -> "referralStatus".equals(f.getKey())).count();
        assertEquals(1, count, "must replace, not duplicate");
        assertTrue(hf.getAdditionalFields().getFields().stream().anyMatch(f -> "gender".equals(f.getKey())));
    }

    @Test
    void mirrorStampsStatusOnOutboundHfReferral() throws Exception {
        when(linkRepo.findByCoordinationId(eq("coord-1"), any())).thenReturn(
                CcnReferralLink.builder().coordinationId("coord-1").direction(CcnReferralLink.OUTBOUND)
                        .hfReferralId("hf-1").tenantId("sl").build());
        SearchResponse<HFReferral> resp = SearchResponse.<HFReferral>builder()
                .response(List.of(HFReferral.builder().id("hf-1").tenantId("sl").build())).build();
        when(hfReferralRepository.findById(eq("sl"), eq(List.of("hf-1")), eq("id"), eq(Boolean.FALSE)))
                .thenReturn(resp);

        svc.mirrorFromSpice("coord-1", "COMPLETED");

        ArgumentCaptor<HFReferralRequest> cap = ArgumentCaptor.forClass(HFReferralRequest.class);
        verify(hfReferralService).update(cap.capture());
        assertEquals("COMPLETED", svc.readStatus(cap.getValue().getHfReferral()));
    }

    @Test
    void mirrorSkipsInboundCoordination() {
        when(linkRepo.findByCoordinationId(eq("coord-2"), any())).thenReturn(
                CcnReferralLink.builder().coordinationId("coord-2").direction(CcnReferralLink.INBOUND)
                        .hfReferralId("hf-2").tenantId("sl").build());
        svc.mirrorFromSpice("coord-2", "COMPLETED");
        verify(hfReferralService, never()).update(any());
    }

    @Test
    void mirrorSkipsNonMirroredState() {
        svc.mirrorFromSpice("coord-3", "DRAFT");
        verifyNoInteractions(linkRepo);
        verify(hfReferralService, never()).update(any());
    }
}
