package org.egov.facility.spice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.facility.spice.model.SpiceFacility;
import org.egov.facility.spice.model.SpiceFacilityPage;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Live client for Spice's admin facility listing ({@code admin-service/healthfacility/list}).
 *
 * <p>Admin (client=web) login: the token is returned base64-encoded in the {@code AuthCookie}
 * set-cookie, not the Authorization header. Token cached in an instance field, re-fetched on 401.</p>
 */
@Slf4j
@Component
public class SpiceFacilityClient {

    private static final String BASE_URL = "https://spice-training-backend.sl.labsplatform.com/";
    private static final String USERNAME = "merc.benz@spice.mdt";
    private static final String PASSWORD = "Spice123";
    private static final String HMAC_SALT = "spice_uat";
    private static final int COUNTRY_ID = 1;

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private String authToken;
    private String spiceTenantId;

    /** List facilities filtered by chiefdom/district, with skip/limit (Spice paginates here). */
    public SpiceFacilityPage listFacilities(List<Integer> chiefdomIds, List<Integer> districtIds,
                                            int skip, int limit) {
        String body = searchBody(chiefdomIds, districtIds, skip, limit);
        JsonNode root = postWithAuthRetry("admin-service/healthfacility/list", body);
        List<SpiceFacility> facilities = new ArrayList<>();
        JsonNode arr = root.path("entityList");
        if (arr.isArray())
            for (JsonNode n : arr) facilities.add(mapper.convertValue(n, SpiceFacility.class));
        long total = root.path("totalCount").asLong(facilities.size());
        return new SpiceFacilityPage(facilities, total);
    }

    private JsonNode postWithAuthRetry(String path, String jsonBody) {
        ensureLoggedIn();
        try {
            return dataPost(path, jsonBody);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.info("Spice admin token rejected (401) — re-authenticating and retrying {}", path);
            this.authToken = null;
            ensureLoggedIn();
            return dataPost(path, jsonBody);
        }
    }

    private JsonNode dataPost(String path, String jsonBody) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("Authorization", authToken);
        h.set("client", "web");
        h.set("tenantId", spiceTenantId);
        ResponseEntity<String> resp = rest.exchange(
                BASE_URL + path, HttpMethod.POST, new HttpEntity<>(jsonBody, h), String.class);
        try {
            return mapper.readTree(resp.getBody() == null ? "{}" : resp.getBody());
        } catch (Exception ex) {
            throw new CustomException("SPICE_PARSE_ERROR", "Failed to parse Spice response from " + path);
        }
    }

    private synchronized void ensureLoggedIn() {
        if (authToken != null) return;
        log.info("Logging in to Spice (admin) as {}", USERNAME);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", USERNAME);
        form.add("password", hmacSha512(PASSWORD));

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.set("client", "web");

        ResponseEntity<String> resp = rest.exchange(
                BASE_URL + "auth-service/session", HttpMethod.POST,
                new HttpEntity<>(form, h), String.class);

        this.spiceTenantId = resp.getHeaders().getFirst("tenantid");
        this.authToken = extractAuthCookieToken(resp.getHeaders());
        if (authToken == null)
            throw new CustomException("SPICE_AUTH_FAILED", "No AuthCookie returned by Spice admin login");
    }

    /** Admin token = base64-decoded value of the AuthCookie set-cookie ("Bearer ey...."). */
    private String extractAuthCookieToken(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies == null) return null;
        for (String c : cookies) {
            if (c.startsWith("AuthCookie=")) {
                String b64 = c.substring("AuthCookie=".length());
                int semi = b64.indexOf(';');
                if (semi >= 0) b64 = b64.substring(0, semi);
                try {
                    return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException ex) {
                    return null;
                }
            }
        }
        return null;
    }

    private String searchBody(List<Integer> chiefdomIds, List<Integer> districtIds, int skip, int limit) {
        Map<String, Object> body = new HashMap<>();
        body.put("limit", limit);
        body.put("skip", skip);
        body.put("countryId", COUNTRY_ID);
        body.put("tenantIds", new ArrayList<>());
        body.put("healthFacilityTypes", new ArrayList<>());
        body.put("districtIds", districtIds == null ? new ArrayList<>() : districtIds);
        body.put("chiefdomIds", chiefdomIds == null ? new ArrayList<>() : chiefdomIds);
        body.put("userBased", false);
        body.put("searchTerm", "");
        body.put("includesDisabled", true);
        try {
            return mapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new CustomException("SPICE_BODY_ERROR", "Failed to build Spice facility request body");
        }
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
