package org.egov.referralmanagement.ccn.bpp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.egov.referralmanagement.ccn.CcnReferralStatusService;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Builds the BPP-side (receive flow) Beckn responses for UC3 (ServiceCoordination):
 * on_discover (HCM campaign field-capacity offer), on_select/on_init/on_confirm/on_status (echo the
 * contract with the new lifecycle), and the outbound update we publish when the CHW's task completes.
 */
@Component
public class ServiceCoordinationMapper {

    private final CcnProperties p;
    private final ObjectMapper om;
    private final CcnReferralStatusService statusService;

    public ServiceCoordinationMapper(CcnProperties p, @Qualifier("objectMapper") ObjectMapper om,
                                     CcnReferralStatusService statusService) {
        this.p = p;
        this.om = om;
        this.statusService = statusService;
    }

    /** Clone the inbound context, flip to the on_/response action, fresh messageId+timestamp.
     *  Force domain+version so ONIX's bppTxnCaller can match a routing rule (it routes by
     *  domain+version; a missing version yields "no routing rules found for domain health"). */
    private ObjectNode responseContext(JsonNode inbound, String action) {
        // Prefer the inbound context (carries SPICE's bapId/uri for routing back); if the stored
        // payload is missing/empty, synthesize one from config so the push never crashes and can
        // still route. The BAP is SPICE and the BPP is us (roles are the inbound perspective).
        JsonNode ctx = inbound == null ? null : inbound.path("context");
        ObjectNode c = (ctx != null && ctx.isObject()) ? ((ObjectNode) ctx).deepCopy() : om.createObjectNode();
        if (!c.hasNonNull("networkId")) c.put("networkId", p.getNetworkId());
        if (!c.hasNonNull("bppId")) c.put("bppId", p.getBapId());       // we are the BPP on inbound
        if (!c.hasNonNull("bppUri")) c.put("bppUri", p.getBapUri());
        if (!c.hasNonNull("transactionId")) c.put("transactionId", UUID.randomUUID().toString());  // ONIX requires it
        c.put("action", action);
        c.put("messageId", UUID.randomUUID().toString());
        c.put("timestamp", OffsetDateTime.now().toString());
        c.put("domain", p.getDomain());
        c.put("version", p.getVersion());
        return c;
    }

    /** on_discover — advertise HCM campaign field capacity as a ServiceCoordinationResource offer. */
    public ObjectNode onDiscover(JsonNode inbound) {
        ObjectNode root = om.createObjectNode();
        root.set("context", responseContext(inbound, "on_discover"));
        ObjectNode provider = root.putObject("message").putObject("catalog").putArray("providers").addObject();
        provider.put("id", p.getBapId());
        provider.putObject("descriptor").put("name", "DIGIT HCM — campaign field capacity");
        ObjectNode offer = provider.putArray("offers").addObject();
        offer.put("id", p.getCampaignOfferId());
        ObjectNode res = provider.putArray("resources").addObject();
        res.put("id", p.getCampaignResourceId());
        ObjectNode ra = res.putObject("resourceAttributes");
        ra.put("@context", "https://schema.beckn.io/ServiceCoordinationResource/v2.1/context.jsonld");
        ra.put("@type", "scres:ServiceCoordinationResource");
        ObjectNode sc = ra.putObject("coordinationScope").putArray("targetServiceTypes").addObject();
        sc.put("@context", p.getCodedValueCtx());
        sc.put("@type", "ServiceCategory");
        sc.put("code", "FIELD_DATA_COLLECTION");
        sc.put("display", "Field data collection");
        ra.put("acceptanceMode", "AUTO_ACCEPT");
        return root;
    }

    /** on_select / on_init / on_confirm / on_status — echo the contract with the new lifecycle state. */
    public ObjectNode onEcho(String onAction, JsonNode inbound, String lifecycleState, String statusCode) {
        ObjectNode root = om.createObjectNode();
        root.set("context", responseContext(inbound, onAction));
        ObjectNode contract = inbound.at("/message/contract").isMissingNode()
                ? om.createObjectNode() : inbound.at("/message/contract").deepCopy();
        // CCN displays the referral from contract.status.code, so it carries the REAL outcome
        // (DRAFT/ACTIVE/CANCELLED/COMPLETE). Callers pass the already-mapped wire code.
        contract.putObject("status").put("code", statusCode);
        // Commitment descriptor.code has its own enum (DRAFT/ACTIVE/CLOSED): force every echoed
        // commitment to the wire-valid descriptor for this status (terminal → CLOSED).
        String commitDescriptor = statusService.commitmentDescriptorForWire(statusCode);
        JsonNode commitments = contract.get("commitments");
        if (commitments != null && commitments.isArray()) {
            for (JsonNode cm : commitments) {
                if (cm.isObject())
                    ((ObjectNode) cm).putObject("status").putObject("descriptor").put("code", commitDescriptor);
            }
        }
        // Always carry the current lifecycle state so every echo/poll reflects the real outcome
        // (accepted/rejected/cancelled/completed). A plain `status` poll from CCN arrives with only
        // {id, commitments} and NO contractAttributes — previously we set lifecycleState only when the
        // inbound already had that block, so on_status silently dropped the state. Ensure the block
        // exists and populate coordinationId + lifecycleState.
        if (lifecycleState != null && !lifecycleState.isBlank()) {
            ObjectNode ca = contract.has("contractAttributes") && contract.get("contractAttributes").isObject()
                    ? (ObjectNode) contract.get("contractAttributes")
                    : contract.putObject("contractAttributes");
            ca.put("lifecycleState", lifecycleState);
            if (!ca.hasNonNull("coordinationId")) {
                String cid = contract.path("id").asText(null);
                if (cid != null && !cid.isBlank()) ca.put("coordinationId", cid);
            }
        }
        root.putObject("message").set("contract", contract);
        return root;
    }

    /** Legacy helper — dispatch as on_status with the given lifecycleState (verbatim). */
    public ObjectNode statusUpdate(JsonNode lastInbound, String coordinationId, String ccnLifecycleState) {
        return statusUpdate(lastInbound, coordinationId, ccnLifecycleState, "on_status", null);
    }

    /**
     * HCM-initiated status push back to SPICE (inbound accept / reject / cancel / complete). The verified
     * CCN-accepted shape is: {@code status.code} = a wire code CCN allows (only DRAFT/ACTIVE pass its
     * contradictory enums, so config forces ACTIVE); required {@code commitments}; and
     * {@code contractAttributes} carrying the REAL outcome in {@code lifecycleState} (e.g. CANCELLED),
     * the {@code statusReason}, and a required {@code targetCriteria.serviceCategory} in the allowed set.
     *
     * @param ccnLifecycleState the business outcome to send (already mapped: reject & cancel -> CANCELLED)
     */
    public ObjectNode statusUpdate(JsonNode lastInbound, String coordinationId, String ccnLifecycleState,
                                   String onAction, String reason) {
        ObjectNode root = om.createObjectNode();
        root.set("context", responseContext(lastInbound, onAction));
        ObjectNode contract = root.putObject("message").putObject("contract");
        contract.put("id", coordinationId);
        // CCN displays from contract.status.code — put the mapped real outcome here (CANCELLED/COMPLETE/…).
        String wireStatus = statusService.wireStatusCodeFor(ccnLifecycleState);
        contract.putObject("status").put("code", wireStatus);
        addCommitments(contract, statusService.commitmentDescriptorForWire(wireStatus));
        ObjectNode ca = contract.putObject("contractAttributes");
        ca.put("@context", p.getServiceCoordinationCtx());
        ca.put("@type", "scoord:ServiceCoordination");
        ca.put("coordinationId", coordinationId);
        ca.put("lifecycleState", ccnLifecycleState);                        // REAL outcome (CANCELLED/ACCEPTED/COMPLETED)
        if (reason != null && !reason.isBlank()) {
            ca.put("statusReason", reason);                                 // reason rides here, NOT under status
        }
        addTargetCriteria(ca, lastInbound);                                // required by CCN
        return root;
    }

    /** Required commitments — always built from config with the given commitment descriptor code
     *  (enum DRAFT/ACTIVE/CLOSED) and the configured resource/offer ids (verified accepted by CCN). */
    private void addCommitments(ObjectNode contract, String commitmentDescriptor) {
        ObjectNode cm = contract.putArray("commitments").addObject();
        cm.put("id", "commitment-" + UUID.randomUUID().toString().substring(0, 8));
        cm.putObject("status").putObject("descriptor").put("code", commitmentDescriptor);
        ObjectNode res = cm.putArray("resources").addObject();
        res.put("id", p.getResourceId());
        res.putObject("quantity").put("count", 1);
        ObjectNode offer = cm.putObject("offer");
        offer.put("id", p.getOfferId());
        offer.putArray("resourceIds").add(p.getResourceId());
    }

    /** Required targetCriteria — always built with a full coded serviceCategory (@context/@type/code)
     *  in the allowed set. (Echoing the inbound is unsafe: it may carry a disallowed code or omit
     *  @context, both of which CCN rejects — verified live.) */
    private void addTargetCriteria(ObjectNode ca, JsonNode lastInbound) {
        ObjectNode tc = ca.putObject("targetCriteria");
        ObjectNode sc = tc.putObject("serviceCategory");
        sc.put("@context", p.getCodedValueCtx());
        sc.put("@type", "ServiceCategory");
        sc.put("code", p.getWireServiceCategory());
        sc.put("display", "Consultation");
        tc.put("consultationModality", "IN_PERSON");
    }
}
