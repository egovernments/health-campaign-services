package org.egov.referralmanagement.spice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.referralmanagement.spice.model.SpiceHousehold;
import org.egov.referralmanagement.spice.model.SpiceMember;
import org.egov.tracer.model.CustomException;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin live client for the Spice community-health backend.
 *
 * <p>Credentials and base URL are hardcoded per requirement. The auth token is obtained lazily,
 * cached in an instance field, and re-fetched automatically if a data call comes back 401.
 * Nothing is persisted — every call hits Spice live.</p>
 */
@Slf4j
@Component
public class SpiceApiClient {

    // ── hardcoded config (per requirement) ───────────────────────────────────
    private static final String BASE_URL = "https://spice-training-backend.sl.labsplatform.com/";
    private static final String USERNAME = "chw@spice.com";
    private static final String PASSWORD = "Spice123";
    private static final String HMAC_SALT = "spice_uat";
    private static final String CLIENT = "mob";
    private static final String APP_VERSION = "1.0.0";
    private static final String APP_TYPE = "COMMUNITY";
    private static final int APP_VERSION_CODE = 25;
    private static final String APP_VERSION_NAME = "1.1.0";
    private static final String DEVICE_ID = "hcm-spice-downsync";

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    // ── cached session (instance-scoped only) ─────────────────────────────────
    private String authToken;
    private String spiceTenantId;
    private Long userId;
    private String organizationId;

    /** Fetch households for a village, retrying once after re-login on 401. */
    public List<SpiceHousehold> getHouseholds(long villageId, Long lastSyncedTime) {
        String body = syncBody(villageId, lastSyncedTime);
        JsonNode arr = postWithAuthRetry("spice-service/household/list", body);
        return readList(arr, SpiceHousehold.class);
    }

    /** Fetch members (persons) for a village, retrying once after re-login on 401. */
    public List<SpiceMember> getMembers(long villageId, Long lastSyncedTime) {
        String body = syncBody(villageId, lastSyncedTime);
        JsonNode arr = postWithAuthRetry("spice-service/household/member/list", body);
        return readList(arr, SpiceMember.class);
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private JsonNode postWithAuthRetry(String path, String jsonBody) {
        ensureLoggedIn();
        try {
            return dataPost(path, jsonBody);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.info("Spice token rejected (401) — re-authenticating and retrying {}", path);
            this.authToken = null;
            ensureLoggedIn();
            return dataPost(path, jsonBody);
        }
    }

    private JsonNode dataPost(String path, String jsonBody) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("Authorization", authToken);
        h.set("client", CLIENT);
        h.set("tenantId", spiceTenantId);
        h.set("organizationId", organizationId);
        h.set("App-Version", APP_VERSION);
        ResponseEntity<String> resp = rest.exchange(
                BASE_URL + path, HttpMethod.POST, new HttpEntity<>(jsonBody, h), String.class);
        try {
            return mapper.readTree(resp.getBody() == null ? "[]" : resp.getBody());
        } catch (Exception ex) {
            throw new CustomException("SPICE_PARSE_ERROR", "Failed to parse Spice response from " + path);
        }
    }

    /** Logs in only if no valid cached token exists. */
    private synchronized void ensureLoggedIn() {
        if (authToken != null) return;
        log.info("Logging in to Spice as {}", USERNAME);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", USERNAME);
        form.add("password", hmacSha512(PASSWORD));

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.set("client", CLIENT);

        ResponseEntity<String> resp = rest.exchange(
                BASE_URL + "auth-service/session", HttpMethod.POST,
                new HttpEntity<>(form, h), String.class);

        this.authToken = resp.getHeaders().getFirst("Authorization");
        this.spiceTenantId = resp.getHeaders().getFirst("tenantid");
        if (authToken == null)
            throw new CustomException("SPICE_AUTH_FAILED", "No Authorization header returned by Spice login");

        try {
            JsonNode b = mapper.readTree(resp.getBody());
            this.userId = b.path("id").asLong();
            JsonNode orgs = b.path("organizationIds");
            this.organizationId = orgs.isArray() && orgs.size() > 0 ? orgs.get(0).asText() : null;
            if (spiceTenantId == null && b.hasNonNull("tenantId"))
                this.spiceTenantId = b.get("tenantId").asText();
        } catch (Exception ex) {
            throw new CustomException("SPICE_AUTH_PARSE_ERROR", "Failed to parse Spice login body");
        }
    }

    private String syncBody(long villageId, Long lastSyncedTime) {
        Map<String, Object> body = new HashMap<>();
        body.put("appType", APP_TYPE);
        body.put("appVersionCode", APP_VERSION_CODE);
        body.put("appVersionName", APP_VERSION_NAME);
        body.put("deviceId", DEVICE_ID);
        body.put("lastSyncTime", lastSyncedTime); // null = full pull
        body.put("memberIds", new ArrayList<>());
        body.put("userId", userId);
        body.put("villageIds", List.of(villageId));
        try {
            return mapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new CustomException("SPICE_BODY_ERROR", "Failed to build Spice request body");
        }
    }

    private <T> List<T> readList(JsonNode arr, Class<T> type) {
        List<T> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) return out;
        for (JsonNode n : arr) {
            out.add(mapper.convertValue(n, type));
        }
        return out;
    }

    private String hmacSha512(String plain) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(HMAC_SALT.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] sig = mac.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(sig.length * 2);
            for (byte b : sig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new CustomException("SPICE_HASH_ERROR", "Failed to HMAC-hash Spice password");
        }
    }
}
