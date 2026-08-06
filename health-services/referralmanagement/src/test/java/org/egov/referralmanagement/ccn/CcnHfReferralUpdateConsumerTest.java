package org.egov.referralmanagement.ccn;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.referralmanagement.ccn.CcnReferralService;
import org.egov.referralmanagement.ccn.bpp.CcnBppService;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.ccn.consumer.CcnHfReferralUpdateConsumer;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.egov.referralmanagement.ccn.repository.CcnReferralLinkRepository;
import org.egov.referralmanagement.repository.HFReferralRepository;
import org.egov.referralmanagement.service.HFReferralService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Inbound status-back: HF worker accept/reject/resolve on an inbound HFReferral → push to SPICE. */
class CcnHfReferralUpdateConsumerTest {

    private CcnBppService bppService;
    private CcnReferralService referralService;
    private CcnReferralLinkRepository linkRepo;
    private CcnProperties props;
    private final ObjectMapper om = new ObjectMapper();
    private CcnHfReferralUpdateConsumer consumer;

    @BeforeEach
    void setup() {
        props = new CcnProperties();
        props.setBppEnabled(true);
        bppService = mock(CcnBppService.class);
        referralService = mock(CcnReferralService.class);
        linkRepo = mock(CcnReferralLinkRepository.class);
        CcnReferralStatusService statusService = new CcnReferralStatusService(
                props, mock(HFReferralService.class), mock(HFReferralRepository.class), linkRepo);
        consumer = new CcnHfReferralUpdateConsumer(bppService, referralService, linkRepo, statusService, props, om);
    }

    /** Build an update message. modifiedBy=null → treated as a genuine app/worker change. */
    private String message(String referralCode, String status, String reason, String direction, String modifiedBy) throws Exception {
        List<Field> fields = new java.util.ArrayList<>();
        fields.add(Field.builder().key("referralStatus").value(status).build());
        if (reason != null) fields.add(Field.builder().key("referralStatusReason").value(reason).build());
        HFReferral hf = HFReferral.builder()
                .id("hf-1").tenantId("sl").referralCode(referralCode)
                .auditDetails(AuditDetails.builder().lastModifiedBy(modifiedBy).build())
                .additionalFields(AdditionalFields.builder().schema("HFReferral").version(1).fields(fields).build())
                .build();
        if (direction != null) {
            when(linkRepo.findByCoordinationId(eq(referralCode), any())).thenReturn(
                    CcnReferralLink.builder().coordinationId(referralCode).direction(direction).build());
        }
        return om.writeValueAsString(List.of(hf));
    }

    @Test
    void acceptedInboundIsPushedBack() throws Exception {
        consumer.onHfReferralUpdate(message("coord-1", "ACCEPTED", null, CcnReferralLink.INBOUND, "field-user"), "sl-update-hfreferral-topic");
        verify(bppService).publishResult("coord-1", "ACCEPTED", null);
        verify(referralService, never()).sendOutboundUpdate(any(), any(), any());
    }

    @Test
    void rejectedInboundForwardsTheReason() throws Exception {
        consumer.onHfReferralUpdate(message("coord-1", "REJECTED", "wrong facility", CcnReferralLink.INBOUND, "field-user"), "sl-update-hfreferral-topic");
        verify(bppService).publishResult("coord-1", "REJECTED", "wrong facility");
    }

    @Test
    void cancelledOutboundFiresBapUpdate() throws Exception {
        // CHW cancels a referral they raised (OUTBOUND) → BAP update, not the BPP path
        consumer.onHfReferralUpdate(message("coord-1", "CANCELLED", "duplicate", CcnReferralLink.OUTBOUND, "field-user"), "sl-update-hfreferral-topic");
        verify(referralService).sendOutboundUpdate(any(), eq("CANCELLED"), eq("duplicate"));
        verify(bppService, never()).publishResult(any(), any(), any());
    }

    @Test
    void mirrorWriteFromSpiceIsNotBouncedBack() throws Exception {
        // our own status mirror (lastModifiedBy=ccn-system) must NOT be pushed back to SPICE
        consumer.onHfReferralUpdate(message("coord-1", "COMPLETED", null, CcnReferralLink.OUTBOUND, "ccn-system"), "sl-update-hfreferral-topic");
        verify(referralService, never()).sendOutboundUpdate(any(), any(), any());
        verify(bppService, never()).publishResult(any(), any(), any());
    }

    @Test
    void receivedStatusIsNotPushed() throws Exception {
        consumer.onHfReferralUpdate(message("coord-1", "RECEIVED", null, CcnReferralLink.INBOUND, "field-user"), "sl-update-hfreferral-topic");
        verify(bppService, never()).publishResult(any(), any(), any());
        verify(referralService, never()).sendOutboundUpdate(any(), any(), any());
    }

    @Test
    void disabledDoesNothing() throws Exception {
        props.setBppEnabled(false);
        consumer.onHfReferralUpdate(message("coord-1", "ACCEPTED", null, CcnReferralLink.INBOUND, "field-user"), "sl-update-hfreferral-topic");
        verify(bppService, never()).publishResult(any(), any(), any());
    }

    @Test
    void malformedMessageIsSwallowed() {
        assertDoesNotThrow(() -> consumer.onHfReferralUpdate("{ not json ]", "sl-update-hfreferral-topic"));
        verify(bppService, never()).publishResult(any(), any(), any());
    }
}
