package org.egov.referralmanagement.ccn;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralRequest;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.egov.referralmanagement.ccn.repository.CcnReferralLinkRepository;
import org.egov.referralmanagement.repository.HFReferralRepository;
import org.egov.referralmanagement.service.HFReferralService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Referral-status lifecycle helper (HFReferral-only; the normal Referral flow is not used by the app).
 * The status lives in {@code HFReferral.additionalFields[referralStatus]} (no schema migration) so it
 * downsyncs to the CHW app.
 *
 * <p>Two responsibilities:</p>
 * <ul>
 *   <li><b>Outbound mirror</b> — when SPICE reports a lifecycle state on an OUTBOUND referral
 *       (on_status/on_update callback), stamp it onto the linked HFReferral's {@code referralStatus}
 *       so the CHW sees the outcome on the next cycle downsync. HCM sends nothing back (just waits).</li>
 *   <li><b>Config parsing</b> — the mirrored-states set, the forwardable-statuses set (inbound
 *       accept/reject/resolve), and the referralStatus → Beckn status.code map. All config-driven so
 *       the exact SPICE vocabulary can be plugged in once confirmed (placeholders:
 *       Accepted/Rejected/Completed/Cancelled).</li>
 * </ul>
 */
@Slf4j
@Service
public class CcnReferralStatusService {

    private final CcnProperties p;
    private final HFReferralService hfReferralService;
    private final HFReferralRepository hfReferralRepository;
    private final CcnReferralLinkRepository linkRepository;

    public CcnReferralStatusService(CcnProperties p, HFReferralService hfReferralService,
                                    HFReferralRepository hfReferralRepository,
                                    CcnReferralLinkRepository linkRepository) {
        this.p = p;
        this.hfReferralService = hfReferralService;
        this.hfReferralRepository = hfReferralRepository;
        this.linkRepository = linkRepository;
    }

    // ── outbound mirror (SPICE → HCM) ──────────────────────────────────────────────────────────

    /**
     * Mirror a SPICE-reported lifecycle state onto the linked HFReferral's referralStatus, so the
     * device/app displays whatever state the network reports — for BOTH directions:
     *   - OUTBOUND: SPICE reports the outcome on a referral the CHW raised (via on_status callback).
     *   - INBOUND:  SPICE sends an update on a referral it referred to HCM (via the BPP update handler).
     * No-op unless the state is in the configured mirrored set and the link has a linked HFReferral.
     * Written as the system user so the update consumer skips it (never bounces back to SPICE).
     * Fully isolated — any failure is logged, never rethrown (must not break the callback ACK).
     */
    public void mirrorFromSpice(String coordinationId, String spiceState) {
        if (coordinationId == null || spiceState == null || !isMirroredState(spiceState)) {
            return;
        }
        try {
            // Callback may carry no tenant — pass null so the repo fans out over configured tenants.
            CcnReferralLink link = linkRepository.findByCoordinationId(coordinationId, null);
            if (link == null || link.getHfReferralId() == null || link.getHfReferralId().isBlank()) {
                return;   // unknown / no linked HFReferral to stamp
            }
            HFReferral hf = fetchById(link.getHfReferralId(), link.getTenantId());
            if (hf == null) {
                log.warn("CCN status mirror: HFReferral {} not found for coordinationId={}",
                        link.getHfReferralId(), coordinationId);
                return;
            }
            String status = spiceState.trim().toUpperCase();
            writeStatus(hf, status);
            // Bump the CLIENT audit so the field app's offline store re-downloads this change. The app
            // upserts a synced record only when clientLastModifiedTime is NEWER than its local copy; the
            // server audit alone isn't enough, so without this the mirrored status never surfaces in the app.
            long now = System.currentTimeMillis();
            AuditDetails clientAudit = hf.getClientAuditDetails() != null
                    ? hf.getClientAuditDetails() : AuditDetails.builder().build();
            clientAudit.setLastModifiedTime(now);
            clientAudit.setLastModifiedBy(p.getSystemUser());
            hf.setClientAuditDetails(clientAudit);
            hfReferralService.update(HFReferralRequest.builder()
                    .requestInfo(systemRequestInfo(link.getTenantId()))
                    .hfReferral(hf)
                    .build());
            log.info("CCN status mirror: stamped referralStatus={} on HFReferral {} (coordinationId={})",
                    status, hf.getId(), coordinationId);
        } catch (Exception e) {
            log.error("CCN status mirror failed for coordinationId={}: {}", coordinationId, e.getMessage());
        }
    }

    private HFReferral fetchById(String id, String tenantId) throws Exception {
        var response = hfReferralRepository.findById(tenantId, List.of(id), "id", Boolean.FALSE);
        List<HFReferral> rows = response != null ? response.getResponse() : null;
        return (rows != null && !rows.isEmpty()) ? rows.get(0) : null;
    }

    // ── additionalFields[referralStatus] read/write ───────────────────────────────────────────

    /** Current referralStatus on an HFReferral (additionalFields), or null. */
    public String readStatus(HFReferral hf) {
        if (hf == null || hf.getAdditionalFields() == null || hf.getAdditionalFields().getFields() == null) {
            return null;
        }
        String key = p.getReferralStatusKey();
        return hf.getAdditionalFields().getFields().stream()
                .filter(f -> f != null && key.equals(f.getKey()))
                .map(Field::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst().orElse(null);
    }

    /** Free-text reason the worker attached to the status change (e.g. rejection reason), or null. */
    public String readReason(HFReferral hf) {
        if (hf == null || hf.getAdditionalFields() == null || hf.getAdditionalFields().getFields() == null) {
            return null;
        }
        String key = p.getReferralStatusReasonKey();
        return hf.getAdditionalFields().getFields().stream()
                .filter(f -> f != null && key.equals(f.getKey()))
                .map(Field::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst().orElse(null);
    }

    /** True when the HFReferral's last write was made by our own backend (system-user) — e.g. the
     *  SPICE→HCM status mirror. The status consumer skips push-back for these so a mirror never bounces. */
    public boolean isSystemWrite(HFReferral hf) {
        String user = hf != null && hf.getAuditDetails() != null ? hf.getAuditDetails().getLastModifiedBy() : null;
        return p.getSystemUser() != null && p.getSystemUser().equals(user);
    }

    /** Set/replace the referralStatus additionalField (creates the AdditionalFields container if absent). */
    public void writeStatus(HFReferral hf, String status) {
        String key = p.getReferralStatusKey();
        if (hf.getAdditionalFields() == null) {
            hf.setAdditionalFields(AdditionalFields.builder()
                    .schema("HFReferral").version(1).fields(new ArrayList<>()).build());
        }
        if (hf.getAdditionalFields().getFields() == null) {
            hf.getAdditionalFields().setFields(new ArrayList<>());
        }
        List<Field> fields = hf.getAdditionalFields().getFields();
        fields.removeIf(f -> f != null && key.equals(f.getKey()));
        fields.add(Field.builder().key(key).value(status).build());
    }

    // ── config-driven vocabulary ───────────────────────────────────────────────────────────────

    /** True when SPICE's state should be mirrored onto an outbound HFReferral. */
    public boolean isMirroredState(String state) {
        return state != null && csvToSet(p.getMirroredStates()).contains(state.trim().toUpperCase());
    }

    /** True when a referralStatus set on an inbound HFReferral must be pushed back to SPICE. */
    public boolean isForwardableStatus(String status) {
        return status != null && csvToSet(p.getForwardableStatuses()).contains(status.trim().toUpperCase());
    }

    /** CCN {@code contractAttributes.lifecycleState} for a given HCM referralStatus. Reject AND cancel
     *  both map to CANCELLED (per SPICE); unmapped values pass through unchanged. */
    public String ccnLifecycleFor(String referralStatus) {
        if (referralStatus == null || referralStatus.isBlank()) return referralStatus;
        String s = referralStatus.trim().toUpperCase();
        return csvToMap(p.getOutboundLifecycleMap()).getOrDefault(s, s);
    }

    /** Wire {@code contract.status.code} (enum DRAFT/ACTIVE/CANCELLED/COMPLETE) for a given HCM
     *  referralStatus or CCN lifecycle value. Unmapped → the configured default {@code wireStatusCode}. */
    public String wireStatusCodeFor(String status) {
        if (status == null || status.isBlank()) return p.getWireStatusCode();
        return csvToMap(p.getOutboundStatusCodeMap())
                .getOrDefault(status.trim().toUpperCase(), p.getWireStatusCode());
    }

    /** Commitment {@code descriptor.code} (enum DRAFT/ACTIVE/CLOSED) for a wire status.code:
     *  terminal (CANCELLED/COMPLETE) → CLOSED; DRAFT → DRAFT; everything else → ACTIVE. */
    public String commitmentDescriptorForWire(String wireStatusCode) {
        if (wireStatusCode == null || wireStatusCode.isBlank()) return "ACTIVE";
        String w = wireStatusCode.trim().toUpperCase();
        if (csvToSet(p.getTerminalWireStatusCodes()).contains(w)) return "CLOSED";
        return "DRAFT".equals(w) ? "DRAFT" : "ACTIVE";
    }

    /** Inbound: HCM referralStatus for a wire {@code contract.status.code}, or null if unmapped. */
    public String referralStatusForWire(String wireStatusCode) {
        if (wireStatusCode == null || wireStatusCode.isBlank()) return null;
        return csvToMap(p.getInboundStatusCodeMap()).get(wireStatusCode.trim().toUpperCase());
    }

    private static Set<String> csvToSet(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    private static Map<String, String> csvToMap(String csv) {
        Map<String, String> out = new LinkedHashMap<>();
        if (csv == null || csv.isBlank()) return out;
        for (String pair : csv.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2 && !kv[0].isBlank() && !kv[1].isBlank()) {
                out.put(kv[0].trim().toUpperCase(), kv[1].trim().toUpperCase());
            }
        }
        return out;
    }

    private RequestInfo systemRequestInfo(String tenantId) {
        return RequestInfo.builder()
                .userInfo(User.builder().uuid(p.getSystemUser()).tenantId(tenantId).type("SYSTEM").build())
                .build();
    }
}
