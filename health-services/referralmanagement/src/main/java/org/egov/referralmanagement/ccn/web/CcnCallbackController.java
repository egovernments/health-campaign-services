package org.egov.referralmanagement.ccn.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.referralmanagement.ccn.CcnReferralStatusService;
import org.egov.referralmanagement.ccn.repository.CcnReferralLinkRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives SPICE/CC async Beckn callbacks (on_select / on_init / on_confirm / on_status / on_update)
 * forwarded by the ONIX BAP receiver, and records the lifecycle on the {@code ccn_referral_link} row
 * keyed by coordinationId.
 *
 * <p>Option A: correlation table only — this does NOT call the HFReferral update API and does not
 * touch the shared HFReferral record. Every handler returns the standard Beckn ACK synchronously.</p>
 *
 * <p>Wire the ONIX bapTxnReceiver routing to forward on_* here, e.g.
 * {@code http://referralmanagement:8080/referralmanagement/ccn/on_confirm}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/ccn")
public class CcnCallbackController {

    private final CcnReferralLinkRepository linkRepository;
    private final CcnReferralStatusService statusService;
    private final ObjectMapper objectMapper;

    public CcnCallbackController(CcnReferralLinkRepository linkRepository,
                                 CcnReferralStatusService statusService,
                                 @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.linkRepository = linkRepository;
        this.statusService = statusService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/on_confirm")
    public ResponseEntity<JsonNode> onConfirm(@RequestBody String body) {
        return handle("on_confirm", body);
    }

    @PostMapping("/on_status")
    public ResponseEntity<JsonNode> onStatus(@RequestBody String body) {
        return handle("on_status", body);
    }

    @PostMapping("/on_update")
    public ResponseEntity<JsonNode> onUpdate(@RequestBody String body) {
        return handle("on_update", body);
    }

    // on_select / on_init carry no lifecycle change worth recording — ACK only.
    @PostMapping({"/on_select", "/on_init"})
    public ResponseEntity<JsonNode> onEarly(@RequestBody String body) {
        return ResponseEntity.ok(ack());
    }

    private ResponseEntity<JsonNode> handle(String action, String body) {
        try {
            JsonNode payload = objectMapper.readTree(body);
            JsonNode contract = payload.at("/message/contract");
            String coordinationId = firstNonBlank(
                    contract.at("/contractAttributes/coordinationId").asText(null),
                    contract.at("/id").asText(null));
            // Prefer the wire contract.status.code (CCN/SPICE convey the real state here), mapped to our
            // referralStatus vocabulary; fall back to contractAttributes.lifecycleState.
            String mappedFromWire = statusService.referralStatusForWire(contract.at("/status/code").asText(null));
            String lifecycleState = mappedFromWire != null ? mappedFromWire
                    : contract.at("/contractAttributes/lifecycleState").asText(null);

            if (coordinationId == null) {
                log.warn("CCN {} callback without coordinationId; ignoring", action);
                return ResponseEntity.ok(ack());
            }
            String state = lifecycleState != null ? lifecycleState : action.toUpperCase();
            // Outbound callback carries no tenant — pass null so the repo fans out over configured tenants.
            int rows = linkRepository.updateState(coordinationId, state, action, System.currentTimeMillis(), null);
            if (rows == 0) {
                log.warn("CCN {} for unknown coordinationId={} (no link row updated)", action, coordinationId);
            } else {
                log.info("CCN {} recorded: coordinationId={} lifecycle={}", action, coordinationId, state);
            }
            // Mirror a SPICE-reported status onto the linked OUTBOUND HFReferral so the CHW sees the
            // outcome next cycle (Accepted/Rejected/Completed/Cancelled). No-op for non-mirrored states
            // and for inbound coordinations. HCM sends nothing back on outbound — it only reflects.
            statusService.mirrorFromSpice(coordinationId, state);
            // If this callback follows an HCM-initiated update on this coordination, record SPICE's
            // follow-up state (no-op unless post_update_ack was already set). Fan out over tenants (null).
            linkRepository.updatePostUpdateState(coordinationId, state, null);
            return ResponseEntity.ok(ack());
        } catch (Exception e) {
            log.error("CCN {} callback error: {}", action, e.getMessage());
            return ResponseEntity.ok(nack(e.getMessage()));
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private JsonNode ack() {
        return objectMapper.createObjectNode().set("message",
                objectMapper.createObjectNode().set("ack",
                        objectMapper.createObjectNode().put("status", "ACK")));
    }

    private JsonNode nack(String msg) {
        var root = objectMapper.createObjectNode();
        root.putObject("message").putObject("ack").put("status", "NACK");
        root.putObject("error").put("message", msg);
        return root;
    }
}
