package org.egov.referralmanagement.ccn;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.referralmanagement.ccn.client.CcnOnixClient;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.egov.referralmanagement.ccn.repository.CcnReferralLinkRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Forwards one DIGIT {@link Referral} (normal referral flow) to SPICE via ONIX/CC:
 * select → init → confirm → status. Non-sensitive: only the SPICE patientId (from the referral's
 * beneficiary/individual id) + coded downward intent cross the network.
 *
 * <p>Persists a {@link CcnReferralLink} keyed by coordinationId so the async on_confirm/on_status
 * callbacks can be correlated back to the originating referral (Option A — correlation table only).</p>
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

    public void forward(Referral referral) {
        if (!p.isEnabled()) {
            log.debug("CCN forwarding disabled; skipping referral {}", referral.getId());
            return;
        }
        String spicePatientId = HealthReferralMapper.spicePatientId(referral);
        if (spicePatientId == null || spicePatientId.isBlank()) {
            log.warn("CCN skip referral {} — no SPICE patientId on beneficiary id", referral.getId());
            return;
        }
        String transactionId = UUID.randomUUID().toString();
        String coordinationId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        log.info("CCN forwarding Referral id={} spicePatientId={} -> SPICE (txn={})",
                referral.getId(), spicePatientId, transactionId);

        CcnReferralLink link = CcnReferralLink.builder()
                .coordinationId(coordinationId)
                .transactionId(transactionId)
                .hfReferralId(referral.getId())                              // originating referral id
                .hfReferralClientReferenceId(referral.getClientReferenceId())
                .beneficiaryId(spicePatientId)                               // the SPICE patientId sent
                .lifecycleState("INITIATED")
                .lastAction("forward")
                .direction(CcnReferralLink.OUTBOUND)
                .localRole("BAP")
                .initiatorSubscriberId(p.getBapId())
                .counterpartySubscriberId(p.getBppId())
                .contractType("HealthReferral")
                .serviceCategory("CONSULTATION")
                .tenantId(referral.getTenantId())
                .createdTime(now)
                .lastModifiedTime(now)
                .build();
        try {
            linkRepository.save(link);
        } catch (Exception e) {
            log.error("CCN could not persist referral link {}: {}", coordinationId, e.getMessage());
        }

        try {
            log.info("CCN select ack: {}", onix.send("select", mapper.select(referral, transactionId, coordinationId, spicePatientId)));
            log.info("CCN init ack: {}", onix.send("init", mapper.init(referral, transactionId, coordinationId, spicePatientId)));
            log.info("CCN confirm ack: {} (coordinationId={})", onix.send("confirm", mapper.confirm(referral, transactionId, coordinationId, spicePatientId)), coordinationId);
            safeUpdate(coordinationId, "SENT", "confirm", referral.getTenantId());
            // query the CC for the T2/SPICE state; the definitive answer arrives async via on_status
            log.info("CCN status ack: {}", onix.send("status", mapper.status(referral, transactionId, coordinationId, spicePatientId)));
        } catch (Exception e) {
            log.error("CCN forwarding failed for Referral id={}: {}", referral.getId(), e.getMessage());
            safeUpdate(coordinationId, "SEND_FAILED", "forward", referral.getTenantId());
        }
    }

    private void safeUpdate(String coordinationId, String state, String action, String tenantId) {
        try {
            linkRepository.updateState(coordinationId, state, action, System.currentTimeMillis(), tenantId);
        } catch (Exception e) {
            log.error("CCN could not update referral link {}: {}", coordinationId, e.getMessage());
        }
    }
}
