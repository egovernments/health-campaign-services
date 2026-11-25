package org.egov.common.pii;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Calls enc-service directly to seal and open the bulk Kafka envelope.
 *
 * <p>The envelope is transport protection, not a read of a person's record, so it does not go
 * through enc-client's policy and audit. This client has its own connect/read timeouts;
 * the RestTemplate enc-client uses is shared with every other outbound call of the service,
 * so its timeouts cannot be tightened for enc-service alone.</p>
 */
@Component
@ConditionalOnProperty(name = "egov.pii.encryption.enabled", havingValue = "true")
public class EnvelopeEncClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EncCircuitBreaker circuitBreaker;
    private final String encryptUrl;
    private final String decryptUrl;
    private final String stateTenantId;

    public EnvelopeEncClient(PiiEncryptionProperties properties,
                             @Qualifier("objectMapper") ObjectMapper objectMapper,
                             EncCircuitBreaker circuitBreaker,
                             @Value("${egov.enc.host}") String encHost,
                             @Value("${egov.enc.encrypt.endpoint}") String encryptPath,
                             @Value("${egov.enc.decrypt.endpoint}") String decryptPath,
                             @Value("${state.level.tenant.id}") String stateTenantId) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getEncConnectTimeoutMs());
        factory.setReadTimeout(properties.getEncReadTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = objectMapper;
        this.circuitBreaker = circuitBreaker;
        this.encryptUrl = encHost + encryptPath;
        this.decryptUrl = encHost + decryptPath;
        this.stateTenantId = stateTenantId;
    }

    public String encrypt(String plain) {
        Map<String, Object> body = Map.of("encryptionRequests",
                List.of(Map.of("tenantId", stateTenantId, "type", "Normal", "value", plain)));
        return circuitBreaker.call("seal a bulk envelope", () -> {
            try {
                JsonNode res = restTemplate.postForObject(encryptUrl, body, JsonNode.class);
                if (res == null || !res.isArray() || !res.get(0).isTextual()) {
                    throw new CustomException(EncCircuitBreaker.ENC_UNAVAILABLE, "enc-service returned no cipher text");
                }
                return res.get(0).asText();
            } catch (CustomException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new CustomException(EncCircuitBreaker.ENC_UNAVAILABLE,
                        "enc-service encrypt failed: " + e.getClass().getSimpleName());
            }
        });
    }

    public String decrypt(String cipher) {
        if (!CipherText.isCipher(cipher)) {
            // Never send a non-cipher value to decrypt.
            throw new CustomException("INDIVIDUAL_BULK_ENVELOPE_INVALID", "bulk envelope payload is not cipher text");
        }
        return circuitBreaker.call("open a bulk envelope", () -> {
            try {
                // Wrapped in an object on purpose: for a bare top-level value enc-service returns the
                // raw decrypted text (a JSON payload comes back parsed), while values inside an
                // object come back as the exact original string.
                String json = objectMapper.writeValueAsString(Map.of("p", cipher));
                JsonNode res = restTemplate.postForObject(decryptUrl, new HttpEntity<>(json, jsonHeaders()), JsonNode.class);
                JsonNode plain = res == null ? null : res.get("p");
                if (plain == null || !plain.isTextual()) {
                    throw new CustomException(EncCircuitBreaker.ENC_UNAVAILABLE, "enc-service returned no plain text");
                }
                return plain.asText();
            } catch (CustomException e) {
                throw e;
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                if (status == 502 || status == 503 || status == 504) {
                    throw new CustomException(EncCircuitBreaker.ENC_UNAVAILABLE, "enc-service decrypt failed: HTTP " + status);
                }
                // enc-service answered (it returns 500 for an unknown key id or damaged cipher text): it is up
                throw new CustomException(EncCircuitBreaker.ENC_REJECTED,
                        "enc-service could not open the bulk envelope: HTTP " + status);
            } catch (Exception e) {
                throw new CustomException(EncCircuitBreaker.ENC_UNAVAILABLE,
                        "enc-service decrypt failed: " + e.getClass().getSimpleName());
            }
        });
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
