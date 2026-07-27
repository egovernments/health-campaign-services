package org.egov.referralmanagement.ccn.bpp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralRequest;
import org.egov.referralmanagement.ccn.client.CcnOnixClient;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.egov.referralmanagement.ccn.repository.CcnReferralLinkRepository;
import org.egov.referralmanagement.service.ReferralManagementService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Receive flow (UC3): SPICE (BAP) → HCM (BPP). Handles inbound discover/select/init/confirm/status/
 * update, dispatches on_* via the ONIX BPP caller, and on confirm creates a normal DIGIT
 * {@link Referral} in-process (validated) for the CHW. Writes/updates the INBOUND
 * {@link CcnReferralLink} row so it's tracked alongside our outbound referrals.
 */
@Slf4j
@Service
public class CcnBppService {

    private final CcnProperties p;
    private final ServiceCoordinationMapper mapper;
    private final CcnOnixClient onix;
    private final CcnReferralLinkRepository linkRepository;
    private final ReferralManagementService referralService;
    private final InboundProjectResolver projectResolver;
    private final ObjectMapper om;

    public CcnBppService(CcnProperties p, ServiceCoordinationMapper mapper, CcnOnixClient onix,
                         CcnReferralLinkRepository linkRepository, ReferralManagementService referralService,
                         InboundProjectResolver projectResolver,
                         @Qualifier("objectMapper") ObjectMapper om) {
        this.p = p;
        this.mapper = mapper;
        this.onix = onix;
        this.linkRepository = linkRepository;
        this.referralService = referralService;
        this.projectResolver = projectResolver;
        this.om = om;
    }

    /** Handle one inbound Beckn request from SPICE. Returns nothing — on_* dispatched async. */
    public void handle(String action, JsonNode body) {
        if (!p.isBppEnabled()) {
            log.debug("CCN BPP disabled; ignoring inbound {}", action);
            return;
        }
        String coordinationId = coordinationId(body);
        String bapId = body.at("/context/bapId").asText(null);
        String txn = body.at("/context/transactionId").asText(null);
        log.info("CCN BPP inbound {} coordinationId={} from bapId={}", action, coordinationId, bapId);

        switch (action) {
            case "discover" -> dispatch("on_discover", mapper.onDiscover(body));
            case "select"   -> { upsertInbound(coordinationId, txn, bapId, "DRAFT", "select", body, null); dispatch("on_select", mapper.onEcho("on_select", body, "DRAFT", "DRAFT")); }
            case "init"     -> { upsertInbound(coordinationId, txn, bapId, "DRAFT", "init", body, null); dispatch("on_init", mapper.onEcho("on_init", body, "DRAFT", "DRAFT")); }
            case "confirm"  -> onConfirm(coordinationId, txn, bapId, body);
            case "status"   -> dispatch("on_status", mapper.onEcho("on_status", body, currentState(coordinationId), "ACTIVE"));
            case "update"   -> { upsertInbound(coordinationId, txn, bapId, lifecycleState(body), "update", body, null); dispatch("on_update", mapper.onEcho("on_update", body, lifecycleState(body), "ACTIVE")); }
            default         -> log.warn("CCN BPP unhandled action {}", action);
        }
    }

    private void onConfirm(String coordinationId, String txn, String bapId, JsonNode body) {
        String createdReferralId = null;
        try {
            createdReferralId = createInboundReferral(coordinationId, body);   // in-process, validated
        } catch (Exception e) {
            log.error("CCN BPP failed to create inbound Referral for {}: {}", coordinationId, e.getMessage());
        }
        upsertInbound(coordinationId, txn, bapId, "ACTIVE", "confirm", body, createdReferralId);
        dispatch("on_confirm", mapper.onEcho("on_confirm", body, "ACTIVE", "ACTIVE"));
    }

    /** Create a normal DIGIT Referral for the CHW from the inbound coordination (validated create).
     *  Tenant is fixed for this node; the project is resolved per-referral from the SPICE patientId
     *  (SPICE_PATIENT_ID → synced ProjectBeneficiary → its projectId) rather than a static value. */
    private String createInboundReferral(String coordinationId, JsonNode body) {
        String spicePatientId = patientId(body);
        RequestInfo ri = RequestInfo.builder()
                .userInfo(User.builder().uuid("ccn-system").tenantId(p.getInboundTenantId()).type("SYSTEM").build())
                .build();
        InboundProjectResolver.Resolution r = projectResolver.resolve(spicePatientId, ri);
        Referral referral = Referral.builder()
                .tenantId(p.getInboundTenantId())
                .clientReferenceId(UUID.randomUUID().toString())
                .projectId(r.getProjectId())                                         // resolved from the patient's beneficiary
                .projectBeneficiaryId(r.getProjectBeneficiaryId())                   // real beneficiary link (null if unsynced)
                .projectBeneficiaryClientReferenceId(r.getProjectBeneficiaryClientReferenceId())
                .referrerId(body.at("/context/bapId").asText(null))                 // origin system
                .recipientType("STAFF")
                .reasons(List.of("INBOUND_" + serviceCategory(body)))
                .referralCode(coordinationId)                                        // cross-ref to the coordination
                .build();
        Referral created = referralService.create(ReferralRequest.builder().requestInfo(ri).referral(referral).build());
        log.info("CCN BPP created inbound Referral id={} for coordinationId={} (project={}, resolved={})",
                created.getId(), coordinationId, r.getProjectId(), r.isResolved());
        return created.getId();
    }

    /** Called by HCM when the CHW completes the field task — publishes the result to SPICE. */
    public void publishResult(String coordinationId, String lifecycleState) {
        CcnReferralLink link = linkRepository.findByCoordinationId(coordinationId);
        if (link == null || !CcnReferralLink.INBOUND.equals(link.getDirection())) {
            log.warn("CCN BPP publishResult: no inbound coordination {}", coordinationId);
            return;
        }
        JsonNode lastInbound = parse(link.getLastPayload());
        dispatch("on_status", mapper.statusUpdate(lastInbound, coordinationId, lifecycleState));
        linkRepository.updateState(coordinationId, lifecycleState, "publishResult", System.currentTimeMillis());
    }

    private void dispatch(String onAction, JsonNode payload) {
        try {
            onix.sendBpp(onAction, payload);
        } catch (Exception e) {
            log.error("CCN BPP dispatch {} failed: {}", onAction, e.getMessage());
        }
    }

    private void upsertInbound(String coordinationId, String txn, String bapId, String lifecycle,
                               String action, JsonNode body, String createdReferralId) {
        long now = System.currentTimeMillis();
        CcnReferralLink existing = linkRepository.findByCoordinationId(coordinationId);
        CcnReferralLink link = CcnReferralLink.builder()
                .coordinationId(coordinationId)
                .transactionId(txn)
                .hfReferralId(createdReferralId != null ? createdReferralId : (existing != null ? existing.getHfReferralId() : null))
                .beneficiaryId(patientId(body))
                .lifecycleState(lifecycle)
                .lastAction(action)
                .direction(CcnReferralLink.INBOUND)
                .localRole("BPP")
                .initiatorSubscriberId(bapId)
                .counterpartySubscriberId(p.getBapId())
                .contractType(contractType(body))
                .serviceCategory(serviceCategory(body))
                .lastPayload(body.toString())
                .tenantId(p.getInboundTenantId())
                .createdTime(existing != null ? existing.getCreatedTime() : now)
                .lastModifiedTime(now)
                .build();
        linkRepository.save(link);
    }

    // ── small extractors ──
    private String coordinationId(JsonNode b) {
        String c = b.at("/message/contract/contractAttributes/coordinationId").asText(null);
        return c != null && !c.isBlank() ? c : b.at("/message/contract/id").asText(null);
    }
    private String lifecycleState(JsonNode b) {
        String s = b.at("/message/contract/contractAttributes/lifecycleState").asText(null);
        return s != null ? s : "ACTIVE";
    }
    private String serviceCategory(JsonNode b) {
        String s = b.at("/message/contract/contractAttributes/targetCriteria/serviceCategory/code").asText(null);
        return s != null ? s : "FIELD_DATA_COLLECTION";
    }
    private String contractType(JsonNode b) {
        String t = b.at("/message/contract/contractAttributes/@type").asText("");
        return t.contains("HealthReferral") ? "HealthReferral" : "ServiceCoordination";
    }
    /** Extract the SPICE patientId from the PATIENT participant. Prefer the healthId whose
     *  system is SPICE_PATIENT_ID (the id we share on the network and sync from household/member);
     *  fall back to the first healthId only if that system isn't present. */
    private String patientId(JsonNode b) {
        for (JsonNode part : b.at("/message/contract/participants")) {
            if ("PATIENT".equals(part.at("/participantAttributes/participantRole").asText())) {
                JsonNode healthIds = part.at("/participantAttributes/healthIds");
                for (JsonNode hid : healthIds) {
                    if ("SPICE_PATIENT_ID".equals(hid.at("/system").asText())) {
                        return hid.at("/value").asText(null);
                    }
                }
                return healthIds.at("/0/value").asText(null);
            }
        }
        return null;
    }
    private String currentState(String coordinationId) {
        CcnReferralLink l = linkRepository.findByCoordinationId(coordinationId);
        return l != null && l.getLifecycleState() != null ? l.getLifecycleState() : "ACTIVE";
    }
    private JsonNode parse(String s) {
        try { return s == null ? om.createObjectNode() : om.readTree(s); }
        catch (Exception e) { return om.createObjectNode(); }
    }
}
