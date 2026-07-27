package org.egov.referralmanagement.ccn;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.referralmanagement.ccn.bpp.CcnBppService;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.ccn.consumer.CcnReferralUpdateConsumer;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.egov.referralmanagement.ccn.repository.CcnReferralLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

/** Inbound completion hook: update to an INBOUND referral with the sentinel reason → publishResult. */
class CcnCompletionConsumerTest {

    private CcnBppService bpp;
    private CcnReferralLinkRepository linkRepo;
    private CcnProperties props;
    private CcnReferralUpdateConsumer consumer;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setup() {
        props = new CcnProperties();
        props.setBppEnabled(true);
        bpp = mock(CcnBppService.class);
        linkRepo = mock(CcnReferralLinkRepository.class);
        consumer = new CcnReferralUpdateConsumer(bpp, linkRepo, props, om);
    }

    private String referralArray(String referralCode, String... reasons) {
        StringBuilder rs = new StringBuilder();
        for (int i = 0; i < reasons.length; i++) {
            if (i > 0) rs.append(",");
            rs.append("\"").append(reasons[i]).append("\"");
        }
        return "[{\"id\":\"ref-1\",\"referralCode\":\"" + referralCode + "\",\"reasons\":[" + rs + "]}]";
    }

    @Test
    void completedInboundPublishesClosed() {
        when(linkRepo.findByCoordinationId("coord-in-1")).thenReturn(
                CcnReferralLink.builder().coordinationId("coord-in-1").direction(CcnReferralLink.INBOUND).build());
        consumer.onReferralUpdate(referralArray("coord-in-1", "TASK_COMPLETE"), "update-referral-topic");
        verify(bpp).publishResult("coord-in-1", "CLOSED");
    }

    @Test
    void nonCompletionUpdateIgnored() {
        consumer.onReferralUpdate(referralArray("coord-in-1", "REVIEW"), "update-referral-topic");
        verifyNoInteractions(bpp);
    }

    @Test
    void outboundReferralIgnored() {
        when(linkRepo.findByCoordinationId("coord-out-1")).thenReturn(
                CcnReferralLink.builder().coordinationId("coord-out-1").direction(CcnReferralLink.OUTBOUND).build());
        consumer.onReferralUpdate(referralArray("coord-out-1", "TASK_COMPLETE"), "update-referral-topic");
        verify(bpp, never()).publishResult(anyString(), anyString());
    }

    @Test
    void disabledDoesNothing() {
        props.setBppEnabled(false);
        consumer.onReferralUpdate(referralArray("coord-in-1", "TASK_COMPLETE"), "update-referral-topic");
        verifyNoInteractions(bpp, linkRepo);
    }
}
