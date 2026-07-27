package org.egov.referralmanagement.ccn.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Posts a Beckn action payload to the ONIX BAP caller. ONIX signs it, resolves the BPP from the
 * registry, and forwards it to SPICE. This client does NO signing — that is ONIX's job (Option A).
 *
 * <p>The action is appended to the caller base URL, e.g.
 * {@code http://onix-bap-hcm:8080/bap/caller/select}. Confirm exact contract in ONIX CONFIG.md.</p>
 */
@Slf4j
@Component
public class CcnOnixClient {

    private final CcnProperties p;
    private final RestTemplate restTemplate = new RestTemplate();

    public CcnOnixClient(CcnProperties p) {
        this.p = p;
    }

    /** POST one Beckn action to the ONIX BAP caller (outbound HCM->SPICE). */
    public JsonNode send(String action, JsonNode payload) {
        return sendTo(p.getOnixCallerUrl(), action, payload);
    }

    /** POST an on_* / update callback to the ONIX BPP caller (inbound receive flow). */
    public JsonNode sendBpp(String action, JsonNode payload) {
        return sendTo(p.getOnixBppCallerUrl(), action, payload);
    }

    /** POST one Beckn action to a given ONIX caller base. Returns ONIX's synchronous ACK/NACK body. */
    public JsonNode sendTo(String base, String action, JsonNode payload) {
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("ONIX caller url is not configured");
        }
        String url = base.endsWith("/") ? base + action : base + "/" + action;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(payload, headers), JsonNode.class);
            log.info("CCN {} -> ONIX {} : {}", action, url, resp.getStatusCode());
            return resp.getBody();
        } catch (Exception e) {
            log.error("CCN {} -> ONIX {} failed: {}", action, url, e.getMessage());
            throw e;
        }
    }
}
