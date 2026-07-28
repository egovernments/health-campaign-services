package org.egov.referralmanagement.ccn.consumer;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.referralmanagement.ccn.bpp.CcnBppService;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.egov.referralmanagement.ccn.repository.CcnReferralLinkRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Inbound completion hook (UC3, BPP side): when a CHW completes an inbound referral, publish the
 * result back to SPICE.
 *
 * <p>Listens on the update-Referral topic ({@code update-referral-topic}) under its own consumer
 * group (pub-sub fan-out; never steals from the persister). For each updated {@link Referral} that
 * (a) is linked to an INBOUND coordination — its {@code referralCode} matches an INBOUND
 * {@link CcnReferralLink} — and (b) carries the completion sentinel in {@code reasons}, it calls
 * {@link CcnBppService#publishResult} to send {@code on_status CLOSED} to the originating SPICE BAP.</p>
 *
 * <p>Dormant unless {@code referralmanagement.ccn.bpp-enabled=true}. Outbound referrals and
 * non-completion updates are ignored.</p>
 */
@Slf4j
@Component
public class CcnReferralUpdateConsumer {

    private final CcnBppService bppService;
    private final CcnReferralLinkRepository linkRepository;
    private final CcnProperties p;
    private final ObjectMapper objectMapper;

    public CcnReferralUpdateConsumer(CcnBppService bppService, CcnReferralLinkRepository linkRepository,
                                     CcnProperties p, @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.bppService = bppService;
        this.linkRepository = linkRepository;
        this.p = p;
        this.objectMapper = objectMapper;
    }

    // topicPattern (not topics): central-instance publishes to tenant-prefixed topics
    // (e.g. sierraleone-update-referral-topic), so match any *update-referral-topic.
    @KafkaListener(
            topicPattern = "${referralmanagement.ccn.update-topic-pattern:.*update-referral-topic}",
            groupId = "${referralmanagement.ccn.update-consumer-group}",
            containerFactory = "ccnKafkaListenerContainerFactory")
    public void onReferralUpdate(String message,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        if (!p.isBppEnabled()) {
            return;
        }
        try {
            for (Referral referral : parse(message)) {
                maybePublishCompletion(referral);
            }
        } catch (Exception exception) {
            // Isolated: log only. Never rethrow — must not disrupt the update/persist flow.
            log.error("CCN completion consumer error on topic {}: {}", topic, exception.getMessage());
            log.error("Trace: {}", ExceptionUtils.getStackTrace(exception));
        }
    }

    private void maybePublishCompletion(Referral referral) {
        String coordinationId = referral.getReferralCode();   // set to coordinationId on inbound create
        if (coordinationId == null || coordinationId.isBlank()) {
            return;
        }
        if (!isCompleted(referral)) {
            return;
        }
        CcnReferralLink link = linkRepository.findByCoordinationId(coordinationId, referral.getTenantId());
        if (link == null || !CcnReferralLink.INBOUND.equals(link.getDirection())) {
            return;   // outbound or unrelated referral — not ours to close on the network
        }
        log.info("CCN completion: inbound referral {} (coordinationId={}) completed → publishing {} to SPICE",
                referral.getId(), coordinationId, p.getClosedState());
        bppService.publishResult(coordinationId, p.getClosedState());
    }

    private boolean isCompleted(Referral referral) {
        return referral.getReasons() != null && referral.getReasons().contains(p.getCompleteReason());
    }

    /** update() publishes a JSON array; tolerate a single object too. */
    public List<Referral> parse(String message) throws Exception {
        List<Referral> out = new ArrayList<>();
        JsonNode node = objectMapper.readTree(message);
        if (node.isArray()) {
            for (JsonNode n : node) out.add(objectMapper.convertValue(n, Referral.class));
        } else if (node.isObject()) {
            out.add(objectMapper.convertValue(node, Referral.class));
        }
        return out;
    }
}
