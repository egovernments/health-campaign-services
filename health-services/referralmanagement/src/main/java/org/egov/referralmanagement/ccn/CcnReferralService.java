package org.egov.referralmanagement.ccn;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
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
    private final CcnIdentityResolver identityResolver;

    public CcnReferralService(CcnProperties p, HealthReferralMapper mapper, CcnOnixClient onix,
                              CcnReferralLinkRepository linkRepository, CcnIdentityResolver identityResolver) {
        this.p = p;
        this.mapper = mapper;
        this.onix = onix;
        this.linkRepository = linkRepository;
        this.identityResolver = identityResolver;
    }

    /**
     * Forward an app-created {@link HFReferral} (the entity the mobile "Refer" screen produces) to SPICE.
     * The HFReferral's {@code beneficiaryId} is already the individual's canonical id (what the app sends
     * as ABHA), so we stamp it straight into {@code additionalFields[abhaId]} and reuse
     * {@link #forward(Referral)} — no project-beneficiary → individual resolution needed.
     */
    public void forwardHfReferral(HFReferral hf) {
        if (!p.isEnabled()) {
            log.debug("CCN forwarding disabled; skipping HFReferral {}", hf.getId());
            return;
        }
        // Loop guard: an HFReferral WE created from an inbound SPICE coordination carries the inbound
        // marker in additionalFields — never forward it back to SPICE (would bounce infinitely).
        if (hasInboundMarker(hf)) {
            log.debug("CCN skip re-forward of inbound-originated HFReferral {}", hf.getId());
            return;
        }
        String abhaId = hf.getBeneficiaryId();
        if (abhaId == null || abhaId.isBlank()) {
            log.warn("CCN skip HFReferral {} — no beneficiaryId to send as {}", hf.getId(), p.getPatientHealthIdSystem());
            return;
        }
        Referral referral = Referral.builder()
                .clientReferenceId(hf.getClientReferenceId())
                .tenantId(hf.getTenantId())
                .projectId(hf.getProjectId())
                .projectBeneficiaryClientReferenceId(abhaId)
                .referrerId(hf.getAuditDetails() != null ? hf.getAuditDetails().getCreatedBy() : null)
                .recipientType("FACILITY")
                .recipientId(hf.getProjectFacilityId())
                .reasons(hf.getSymptom() != null && !hf.getSymptom().isBlank()
                        ? java.util.List.of(hf.getSymptom()) : java.util.List.of("REFERRAL"))
                .referralCode(hf.getReferralCode())
                .build();
        referral.setId(hf.getId());                          // originating hf_referral id -> ccn_referral_link.hf_referral_id
        identityResolver.writeAbhaField(referral, abhaId);   // short-circuit ABHA resolution to the app-sent id
        forward(referral);
    }

    public void forward(Referral referral) {
        if (!p.isEnabled()) {
            log.debug("CCN forwarding disabled; skipping referral {}", referral.getId());
            return;
        }
        // Don't re-forward a referral that WE created from an inbound (SPICE→HCM) coordination —
        // that would bounce it straight back to SPICE. Inbound ones are tagged INBOUND_<category>.
        if (referral.getReasons() != null
                && referral.getReasons().stream().anyMatch(r -> r != null && r.startsWith("INBOUND_"))) {
            log.debug("CCN skip re-forward of inbound-originated referral {}", referral.getId());
            return;
        }
        // Canonical patient identity = the individual's UNIQUE_BENEFICIARY_ID (idgen, from the
        // beneficiary id-pool). Resolved from additionalFields[abhaId] or via project-beneficiary →
        // individual lookup (and cached back onto the referral). We no longer send the
        // projectBeneficiaryClientReferenceId as the ABHA value.
        RequestInfo ri = systemRequestInfo(referral.getTenantId());
        String spicePatientId = identityResolver.resolveOutboundAbha(referral, ri);
        if (spicePatientId == null || spicePatientId.isBlank()) {
            log.warn("CCN skip referral {} — could not resolve canonical {} identity",
                    referral.getId(), p.getPatientIdentifierType());
            return;
        }
        // Resolve + stamp the patient display name (non-PII per integration rule) so the mapper can
        // include it as the PATIENT participant descriptor.name, visible in CCN/SPICE.
        String patientName = identityResolver.resolveOutboundName(referral, ri);
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

    /** True when the HFReferral was created by our inbound (SPICE→HCM) flow — tagged with the marker. */
    private boolean hasInboundMarker(HFReferral hf) {
        if (hf == null || hf.getAdditionalFields() == null || hf.getAdditionalFields().getFields() == null) return false;
        String key = p.getInboundHfMarkerKey();
        return hf.getAdditionalFields().getFields().stream()
                .anyMatch(f -> f != null && key.equals(f.getKey()) && "true".equalsIgnoreCase(f.getValue()));
    }

    private RequestInfo systemRequestInfo(String tenantId) {
        return RequestInfo.builder()
                .userInfo(User.builder().uuid("ccn-system").tenantId(tenantId).type("SYSTEM").build())
                .build();
    }

    private void safeUpdate(String coordinationId, String state, String action, String tenantId) {
        try {
            linkRepository.updateState(coordinationId, state, action, System.currentTimeMillis(), tenantId);
        } catch (Exception e) {
            log.error("CCN could not update referral link {}: {}", coordinationId, e.getMessage());
        }
    }
}
