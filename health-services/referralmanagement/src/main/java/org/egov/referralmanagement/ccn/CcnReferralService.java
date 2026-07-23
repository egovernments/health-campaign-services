package org.egov.referralmanagement.ccn;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.referralmanagement.ccn.client.CcnOnixClient;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.egov.referralmanagement.ccn.repository.CcnReferralLinkRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates forwarding one HFReferral to SPICE via ONIX: select -> init -> confirm.
 *
 * <p>Beckn is async — these POSTs return ACK; SPICE's real responses (on_confirm etc.) arrive at the
 * ONIX receiver ({@code /beckn}) and are handled by {@code CcnCallbackController}. We persist a
 * {@link CcnReferralLink} keyed by coordinationId so a later callback can be correlated back to the
 * originating HFReferral (Option A: correlation table only; the shared HFReferral is untouched).</p>
 */
@Slf4j
@Service
public class CcnReferralService {

    private final CcnProperties p;
    private final HealthReferralMapper mapper;
    private final CcnOnixClient onix;
    private final CcnReferralLinkRepository linkRepository;

    public CcnReferralService(CcnProperties p, HealthReferralMapper mapper, CcnOnixClient onix,
                              CcnReferralLinkRepository linkRepository) {
        this.p = p;
        this.mapper = mapper;
        this.onix = onix;
        this.linkRepository = linkRepository;
    }

    /** Forward a single referral. Safe to call per HFReferral from the consumer. */
    public void forward(HFReferral referral) {
        if (!p.isEnabled()) {
            log.debug("CCN forwarding disabled; skipping referral {}", referral.getId());
            return;
        }
        String transactionId = UUID.randomUUID().toString();
        String coordinationId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        log.info("CCN forwarding HFReferral id={} beneficiary={} -> SPICE (txn={})",
                referral.getId(), referral.getBeneficiaryId(), transactionId);

        // Persist the correlation row up-front so an async on_confirm can always find it.
        CcnReferralLink link = CcnReferralLink.builder()
                .coordinationId(coordinationId)
                .transactionId(transactionId)
                .hfReferralId(referral.getId())
                .hfReferralClientReferenceId(referral.getClientReferenceId())
                .beneficiaryId(referral.getBeneficiaryId())
                .lifecycleState("INITIATED")
                .lastAction("forward")
                .tenantId(referral.getTenantId())
                .createdTime(now)
                .lastModifiedTime(now)
                .build();
        try {
            linkRepository.save(link);
        } catch (Exception e) {
            log.error("CCN could not persist referral link for coordinationId={}: {}", coordinationId, e.getMessage());
        }

        try {
            JsonNode ackSelect = onix.send("select", mapper.select(referral, transactionId, coordinationId));
            log.info("CCN select ack: {}", ackSelect);

            JsonNode ackInit = onix.send("init", mapper.init(referral, transactionId, coordinationId));
            log.info("CCN init ack: {}", ackInit);

            JsonNode ackConfirm = onix.send("confirm", mapper.confirm(referral, transactionId, coordinationId));
            log.info("CCN confirm ack: {} (coordinationId={})", ackConfirm, coordinationId);

            // Sent successfully; the definitive lifecycle comes back async via on_confirm.
            safeUpdate(coordinationId, "SENT", "confirm");
        } catch (Exception e) {
            // Isolated flow: never break the referral create path. Log and record the failure.
            log.error("CCN forwarding failed for HFReferral id={}: {}", referral.getId(), e.getMessage());
            safeUpdate(coordinationId, "SEND_FAILED", "forward");
        }
    }

    private void safeUpdate(String coordinationId, String state, String action) {
        try {
            linkRepository.updateState(coordinationId, state, action, System.currentTimeMillis());
        } catch (Exception e) {
            log.error("CCN could not update referral link {}: {}", coordinationId, e.getMessage());
        }
    }
}
