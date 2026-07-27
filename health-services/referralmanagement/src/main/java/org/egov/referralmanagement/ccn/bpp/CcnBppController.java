package org.egov.referralmanagement.ccn.bpp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * BPP receiver for the downward receive flow (SPICE → HCM, UC3). ONIX's bppTxnReceiver forwards the
 * inbound Beckn requests here. Each returns the synchronous Beckn ACK; the on_* response is dispatched
 * asynchronously by {@link CcnBppService} via the ONIX BPP caller.
 *
 * <p>Wire ONIX bppTxnReceiver routing to {@code http://referralmanagement:8080/referralmanagement/ccn/bpp/<action>}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/ccn/bpp")
public class CcnBppController {

    private final CcnBppService bppService;
    private final ObjectMapper om;

    public CcnBppController(CcnBppService bppService, @Qualifier("objectMapper") ObjectMapper om) {
        this.bppService = bppService;
        this.om = om;
    }

    @PostMapping("/{action}")
    public ResponseEntity<JsonNode> receive(@PathVariable String action, @RequestBody String body) {
        try {
            bppService.handle(action, om.readTree(body));
            return ResponseEntity.ok(ack());
        } catch (Exception e) {
            log.error("CCN BPP {} error: {}", action, e.getMessage());
            return ResponseEntity.ok(nack(e.getMessage()));
        }
    }

    private JsonNode ack() {
        return om.createObjectNode().set("message",
                om.createObjectNode().set("ack", om.createObjectNode().put("status", "ACK")));
    }

    private JsonNode nack(String msg) {
        var root = om.createObjectNode();
        root.putObject("message").putObject("ack").put("status", "NACK");
        root.putObject("error").put("message", msg);
        return root;
    }
}
