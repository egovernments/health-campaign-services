package org.egov.common.pii;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.encryption.EncryptionService;
import org.egov.tracer.model.CustomException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generic enc-client calls for PII values. The field mapping of a service stays in the service; this class only
 * encrypts a list of plain values and decrypts value nodes, both through the enc-service circuit breaker.
 *
 * <p>Opt-in: the bean exists only when {@code egov.pii.encryption.enabled=true} and enc-client is on the class path
 * (it is an optional dependency of this library, so the service declares it).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "egov.pii.encryption.enabled", havingValue = "true")
@ConditionalOnClass(name = "org.egov.encryption.EncryptionService")
public class EncryptionDecryptionUtil {
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final PiiEncryptionProperties properties;
    private final EncCircuitBreaker circuitBreaker;

    public EncryptionDecryptionUtil(EncryptionService encryptionService, ObjectMapper objectMapper,
                                    PiiEncryptionProperties properties, EncCircuitBreaker circuitBreaker) {
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Encrypts plain values in one enc-service call (state-level tenant, enc-client default type).
     * Returns the cipher texts in the same order. Goes through the circuit breaker.
     *
     * <p>enc-client's encryptValue(List, ...) cannot be used: it reads the returned list of cipher
     * strings as a list of lists and always throws (enc-client 2.9.0, EncryptionServiceImpl.encryptValue
     * + ConvertClass.convertTo). So the values go through encryptJson as [{"value": ...}, ...]
     * with an MDMS SecurityPolicy whose only attribute is "value".</p>
     *
     * @param model name of that MDMS SecurityPolicy (model) for the encrypt
     */
    public List<String> encryptValues(List<String> plainValues, String model) {
        if (plainValues == null || plainValues.isEmpty()) {
            return Collections.emptyList();
        }
        String tenantId = properties.getStateLevelTenantId();
        ArrayNode nodes = objectMapper.createArrayNode();
        plainValues.forEach(v -> nodes.addObject().put("value", v));
        try {
            JsonNode result = circuitBreaker.call("encrypt PII", () -> {
                try {
                    return encryptionService.encryptJson(nodes, model, tenantId);
                } catch (IOException e) {
                    throw new CustomException("INDIVIDUAL_ENCRYPTION_TRANSPORT_ERROR", e.getClass().getSimpleName());
                }
            });
            if (result == null || !result.isArray() || result.size() != plainValues.size()) {
                throw new CustomException("INDIVIDUAL_ENCRYPTION_NULL_RESULT",
                        String.format("egov-enc-service returned an unexpected result for %d plain values (tenantId=%s)",
                                plainValues.size(), tenantId));
            }
            List<String> ciphers = new ArrayList<>(result.size());
            for (JsonNode node : result) {
                String cipher = node.path("value").asText(null);
                if (!CipherText.isCipher(cipher)) {
                    // never store a value that did not come back encrypted
                    throw new CustomException("INDIVIDUAL_ENCRYPTION_NULL_RESULT",
                            "egov-enc-service did not encrypt every value. Check the SecurityPolicy "
                                    + model + " in MDMS.");
                }
                ciphers.add(cipher);
            }
            return ciphers;
        } catch (CustomException e) {
            log.error("Error occurred while encrypting: {} {}", e.getCode(), e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.error("Error occurred while encrypting", e);
            throw new CustomException("INDIVIDUAL_ENCRYPTION_ERROR",
                    "Encryption failed: " + e.getClass().getSimpleName());
        }
    }

    /**
     * Decrypts an array of {"ownerId": ..., "value": cipher} nodes through enc-client, so the
     * decrypt is checked against the MDMS SecurityPolicy and written to the audit topic.
     * Only real cipher values may be in the array (enc-service rejects plain values).
     *
     * @param model   name of the MDMS SecurityPolicy (model) for the decrypt
     * @param purpose the purpose enc-client writes to the audit topic (for example searchIndividual)
     */
    public JsonNode decryptValueNodes(ArrayNode nodes, String model, RequestInfo requestInfo, String purpose) {
        if (nodes == null || nodes.isEmpty()) {
            return nodes;
        }
        if (requestInfo == null || requestInfo.getUserInfo() == null) {
            User userInfo = User.builder().uuid("no uuid").type("EMPLOYEE").build();
            requestInfo = RequestInfo.builder().userInfo(userInfo).build();
        }
        // same as the old decryptObject: the user's type is added as a role for the policy check
        final RequestInfo decryptRequestInfo = requestInfo;
        decryptRequestInfo.setUserInfo(getEncrichedAndCopiedUserInfo(requestInfo.getUserInfo()));
        try {
            JsonNode result = circuitBreaker.call("decrypt PII", () -> {
                try {
                    return encryptionService.decryptJson(decryptRequestInfo, nodes, model, purpose);
                } catch (IOException e) {
                    throw new CustomException("INDIVIDUAL_DECRYPTION_TRANSPORT_ERROR", e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            });
            if (result == null || !result.isArray() || result.size() != nodes.size()) {
                throw new CustomException("INDIVIDUAL_DECRYPTION_NULL_RESULT",
                        "egov-enc-service returned an unexpected decrypt result for model " + model
                                + ". Check that the SecurityPolicy exists in MDMS.");
            }
            return result;
        } catch (CustomException e) {
            log.error("Error occurred while decrypting: {} {}", e.getCode(), e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.error("Error occurred while decrypting", e);
            throw new CustomException("INDIVIDUAL_DECRYPTION_ERROR", "Decryption failed: " + e.getClass().getSimpleName());
        }
    }

    private User getEncrichedAndCopiedUserInfo(User userInfo) {
        List<Role> newRoleList = new ArrayList<>();
        if (userInfo.getRoles() != null) {
            for (Role role : userInfo.getRoles()) {
                Role newRole = Role.builder().code(role.getCode()).name(role.getName()).id(role.getId()).build();
                newRoleList.add(newRole);
            }
        }

        if (newRoleList.stream().filter(role -> (role.getCode() != null) && (userInfo.getType() != null) && role.getCode().equalsIgnoreCase(userInfo.getType())).count() == 0) {
            Role roleFromtype = Role.builder().code(userInfo.getType()).name(userInfo.getType()).build();
            newRoleList.add(roleFromtype);
        }

        User newuserInfo = User.builder().id(userInfo.getId()).userName(userInfo.getUserName()).name(userInfo.getName())
                .type(userInfo.getType()).mobileNumber(userInfo.getMobileNumber()).emailId(userInfo.getEmailId())
                .roles(newRoleList).tenantId(userInfo.getTenantId()).uuid(userInfo.getUuid()).build();
        return newuserInfo;
    }

}
