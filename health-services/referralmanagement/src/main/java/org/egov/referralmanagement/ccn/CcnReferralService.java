package org.egov.referralmanagement.ccn;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.referralmanagement.ccn.client.CcnOnixClient;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates forwarding one HFReferral to SPICE via ONIX: select -> init -> confirm.
 *
 * <p>Beckn is async — these POSTs return ACK; SPICE's real responses (on_confirm etc.) arrive at the
 * ONIX receiver ({@code /beckn}). We generate the ids so a later callback correlation can find the
 * originating HFReferral.</p>
 */
@Slf4j
@Service
public class CcnReferralService {

    private final CcnProperties p;
    private final HealthReferralMapper mapper;
    private final CcnOnixClient onix;

    public CcnReferralService(CcnProperties p, HealthReferralMapper mapper, CcnOnixClient onix) {
        this.p = p;
        this.mapper = mapper;
        this.onix = onix;
    }

    /** Forward a single referral. Safe to call per HFReferral from the consumer. */
    public void forward(HFReferral referral) {
        if (!p.isEnabled()) {
            log.debug("CCN forwarding disabled; skipping referral {}", referral.getId());
            return;
        }
        String transactionId = UUID.randomUUID().toString();
        String coordinationId = UUID.randomUUID().toString();
        log.info("CCN forwarding HFReferral id={} beneficiary={} -> SPICE (txn={})",
                referral.getId(), referral.getBeneficiaryId(), transactionId);

        try {
            JsonNode ackSelect = onix.send("select", mapper.select(referral, transactionId, coordinationId));
            log.info("CCN select ack: {}", ackSelect);

            JsonNode ackInit = onix.send("init", mapper.init(referral, transactionId, coordinationId));
            log.info("CCN init ack: {}", ackInit);

            JsonNode ackConfirm = onix.send("confirm", mapper.confirm(referral, transactionId, coordinationId));
            log.info("CCN confirm ack: {} (coordinationId={})", ackConfirm, coordinationId);

            // TODO: persist (referral.id, transactionId, coordinationId) for callback correlation.
        } catch (Exception e) {
            // Isolated flow: never break the referral create path. Log and move on.
            log.error("CCN forwarding failed for HFReferral id={}: {}", referral.getId(), e.getMessage());
        }
    }
}
