package org.egov.referralmanagement.ccn.bpp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    public ServiceCoordinationMapper(CcnProperties p, @Qualifier("objectMapper") ObjectMapper om) {
        this.p = p;
        this.om = om;
    }

    /** Clone the inbound context, flip to the on_/response action, fresh messageId+timestamp. */
    private ObjectNode responseContext(JsonNode inbound, String action) {
        ObjectNode c = inbound.path("context").deepCopy();
        c.put("action", action);
        c.put("messageId", UUID.randomUUID().toString());
        c.put("timestamp", OffsetDateTime.now().toString());
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
        contract.putObject("status").put("code", statusCode);
        if (contract.has("contractAttributes")) {
            ((ObjectNode) contract.get("contractAttributes")).put("lifecycleState", lifecycleState);
        }
        root.putObject("message").set("contract", contract);
        return root;
    }

    /** Outbound update published when the CHW completes the task (lifecycleState -> given state). */
    public ObjectNode statusUpdate(JsonNode lastInbound, String coordinationId, String lifecycleState) {
        ObjectNode root = om.createObjectNode();
        root.set("context", responseContext(lastInbound, "on_status"));
        ObjectNode contract = root.putObject("message").putObject("contract");
        contract.put("id", coordinationId);
        contract.putObject("status").put("code", "CLOSED".equals(lifecycleState) ? "COMPLETE" : "ACTIVE");
        ObjectNode ca = contract.putObject("contractAttributes");
        ca.put("@context", p.getServiceCoordinationCtx());
        ca.put("@type", "scoord:ServiceCoordination");
        ca.put("coordinationId", coordinationId);
        ca.put("lifecycleState", lifecycleState);
        return root;
    }
}
