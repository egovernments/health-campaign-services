package org.egov.referralmanagement.ccn.consumer;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.referralmanagement.ccn.CcnReferralService;
import org.egov.referralmanagement.ccn.CcnReferralStatusService;
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
 * Status-change hook (HFReferral-only) — carries BOTH HCM flows out to SPICE:
 * <ul>
 *   <li><b>INBOUND</b> (SPICE→HCM): the HF worker accepts / rejects-with-reason / completes → push
 *       via the BPP {@code on_update} ({@link CcnBppService#publishResult}).</li>
 *   <li><b>OUTBOUND</b> (HCM→SPICE): the CHW cancels a referral they raised → push via the BAP
 *       {@code update} action ({@link CcnReferralService#sendOutboundUpdate}). Fire-and-store: SPICE
 *       may or may not act; its response is mirrored back and HCM does not re-act.</li>
 * </ul>
 *
 * <p>Listens on {@code *update-hfreferral-topic} under its own consumer group. For each updated
 * {@link HFReferral} with a {@code referralStatus} in the forwardable set and a matching
 * {@link CcnReferralLink}, it routes by direction. Updates made by our own backend (the SPICE→HCM
 * status mirror, {@code lastModifiedBy == systemUser}) are skipped so a mirror never bounces back.
 * Dormant unless {@code referralmanagement.ccn.bpp-enabled=true}.</p>
 */
@Slf4j
@Component
public class CcnHfReferralUpdateConsumer {

    private final CcnBppService bppService;
    private final CcnReferralService referralService;
    private final CcnReferralLinkRepository linkRepository;
    private final CcnReferralStatusService statusService;
    private final CcnProperties p;
    private final ObjectMapper objectMapper;

    public CcnHfReferralUpdateConsumer(CcnBppService bppService, CcnReferralService referralService,
                                       CcnReferralLinkRepository linkRepository,
                                       CcnReferralStatusService statusService, CcnProperties p,
                                       @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.bppService = bppService;
        this.referralService = referralService;
        this.linkRepository = linkRepository;
        this.statusService = statusService;
        this.p = p;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topicPattern = "${referralmanagement.ccn.hf-update-topic-pattern:.*update-hfreferral-topic}",
            groupId = "${referralmanagement.ccn.hf-update-consumer-group:referralmanagement-ccn-hfreferral-status}",
            containerFactory = "ccnKafkaListenerContainerFactory")
    public void onHfReferralUpdate(String message,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        if (!p.isBppEnabled()) {
            return;
        }
        try {
            for (HFReferral hf : parse(message)) {
                maybePushStatus(hf);
            }
        } catch (Exception exception) {
            // Isolated: log only. Never rethrow — must not disrupt the update/persist flow.
            log.error("CCN HFReferral status consumer error on topic {}: {}", topic, exception.getMessage());
            log.error("Trace: {}", ExceptionUtils.getStackTrace(exception));
        }
    }

    private void maybePushStatus(HFReferral hf) {
        String coordinationId = hf.getReferralCode();   // set to coordinationId on both in/out create
        if (coordinationId == null || coordinationId.isBlank()) {
            return;
        }
        // Skip our own mirror write (SPICE→HCM status reflection) — only genuine app/worker changes flow out.
        if (statusService.isSystemWrite(hf)) {
            return;
        }
        String status = statusService.readStatus(hf);
        if (!statusService.isForwardableStatus(status)) {
            return;   // no actionable status change (e.g. still RECEIVED)
        }
        String normalized = status.trim().toUpperCase();
        String reason = statusService.readReason(hf);   // e.g. rejection reason (nullable)
        CcnReferralLink link = linkRepository.findByCoordinationId(coordinationId, hf.getTenantId());
        if (link == null) {
            return;   // unrelated HFReferral — no coordination to push on
        }
        if (CcnReferralLink.INBOUND.equals(link.getDirection())) {
            // SPICE→HCM referral: worker accept / reject-with-reason / complete → BPP on_update.
            log.info("CCN HFReferral {} (coordinationId={}) inbound status={} → on_update to SPICE",
                    hf.getId(), coordinationId, normalized);
            bppService.publishResult(coordinationId, normalized, reason);
        } else if (CcnReferralLink.OUTBOUND.equals(link.getDirection())) {
            // HCM→SPICE referral the CHW raised: cancel → BAP update. Fire-and-store.
            log.info("CCN HFReferral {} (coordinationId={}) outbound status={} → update to SPICE",
                    hf.getId(), coordinationId, normalized);
            referralService.sendOutboundUpdate(link, normalized, reason);
        }
    }

    /** update() publishes a JSON array; tolerate a single object too. */
    public List<HFReferral> parse(String message) throws Exception {
        List<HFReferral> out = new ArrayList<>();
        JsonNode node = objectMapper.readTree(message);
        if (node.isArray()) {
            for (JsonNode n : node) out.add(objectMapper.convertValue(n, HFReferral.class));
        } else if (node.isObject()) {
            out.add(objectMapper.convertValue(node, HFReferral.class));
        }
        return out;
    }
}
