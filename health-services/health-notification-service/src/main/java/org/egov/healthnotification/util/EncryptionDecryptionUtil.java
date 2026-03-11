package org.egov.healthnotification.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for encryption/decryption using direct API calls to egov-enc-service.
 * Does NOT rely on enc-client's MDMS policy loading.
 */
@Slf4j
@Component
public class EncryptionDecryptionUtil {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HealthNotificationProperties properties;

    @Autowired
    private ServiceRequestClient serviceRequestClient;

    /**
     * Encrypts ScheduledNotification objects by calling egov-enc-service directly.
     * Encrypts specific fields: mobileNumber and contextData.
     */
    public List<ScheduledNotification> encryptObject(List<ScheduledNotification> notifications, String key) {
        try {
            if (notifications == null || notifications.isEmpty()) {
                return notifications;
            }

            log.info("Encrypting {} notifications using direct API call", notifications.size());

            // Convert to JsonNode for field extraction
            ArrayNode notificationsArray = objectMapper.valueToTree(notifications);
            ArrayNode encryptedArray = objectMapper.createArrayNode();

            // Encrypt each notification
            for (JsonNode notificationNode : notificationsArray) {
                ObjectNode encrypted = encryptNotificationFields((ObjectNode) notificationNode);
                encryptedArray.add(encrypted);
            }

            // Convert back to List<ScheduledNotification>
            List<ScheduledNotification> result = new ArrayList<>();
            for (JsonNode node : encryptedArray) {
                result.add(objectMapper.treeToValue(node, ScheduledNotification.class));
            }

            log.info("Successfully encrypted {} notifications", result.size());
            return result;

        } catch (Exception e) {
            log.error("Error occurred while encrypting", e);
            throw new CustomException(Constants.ERROR_ENCRYPTION_FAILED, Constants.MSG_ENCRYPTION_ERROR + e.getMessage());
        }
    }

    /**
     * Encrypts specific fields in a ScheduledNotification node.
     */
    private ObjectNode encryptNotificationFields(ObjectNode notification) throws Exception {
        // Encrypt mobileNumber field
        if (notification.has(Constants.FIELD_MOBILE_NUMBER) && !notification.get(Constants.FIELD_MOBILE_NUMBER).isNull()) {
            String mobileNumber = notification.get(Constants.FIELD_MOBILE_NUMBER).asText();
            String encryptedMobile = encryptValue(mobileNumber, Constants.ENCRYPTION_KEY_SCHEDULED_NOTIFICATION);
            notification.put(Constants.FIELD_MOBILE_NUMBER, encryptedMobile);
        }

        // Encrypt contextData field (it's a Map/Object)
        // Store encrypted string in a special field and set contextData to null
        if (notification.has("contextData") && !notification.get("contextData").isNull()) {
            String contextDataStr = objectMapper.writeValueAsString(notification.get("contextData"));
            String encryptedContext = encryptValue(contextDataStr, Constants.ENCRYPTION_KEY_SCHEDULED_NOTIFICATION);
            // Store encrypted string as plain text value
            notification.put("contextData", encryptedContext);
        }

        return notification;
    }

    /**
     * Encrypts a single value by calling egov-enc-service API.
     */
    private String encryptValue(String plaintext, String type) throws Exception {
        // Build encryption request
        ObjectNode encReqObject = objectMapper.createObjectNode();
        encReqObject.put("tenantId", properties.getStateLevelTenantId());
        encReqObject.put("type", Constants.ENCRYPTION_TYPE_NORMAL);
        encReqObject.put("value", plaintext);

        ArrayNode encryptionRequests = objectMapper.createArrayNode();
        encryptionRequests.add(encReqObject);

        ObjectNode request = objectMapper.createObjectNode();
        request.set("encryptionRequests", encryptionRequests);

        // Call encryption service
        StringBuilder uri = new StringBuilder();
        uri.append(properties.getEncryptionServiceHost())
                .append(properties.getEncryptionEndpoint());

        JsonNode response = serviceRequestClient.fetchResult(uri, request, JsonNode.class);

        // Extract encrypted value from response
        if (response != null && response.isArray() && response.size() > 0) {
            return response.get(0).asText();
        }

        throw new CustomException(Constants.ERROR_ENCRYPTION_NULL, Constants.MSG_ENCRYPTION_NULL);
    }

    /**
     * Decrypts ScheduledNotification objects by calling egov-enc-service directly.
     * Decrypts only the encrypted fields (mobileNumber and contextData).
     */
    public List<ScheduledNotification> decryptObject(List<ScheduledNotification> notifications,
                                                     String key,
                                                     RequestInfo requestInfo) {
        try {
            if (notifications == null || notifications.isEmpty()) {
                return notifications;
            }

            log.info("Decrypting {} notifications using direct API call", notifications.size());

            // Build RequestInfo if not provided
            if (requestInfo == null || requestInfo.getUserInfo() == null) {
                User userInfo = User.builder()
                        .uuid(Constants.DEFAULT_USER_UUID)
                        .type(Constants.DEFAULT_USER_TYPE)
                        .build();
                requestInfo = RequestInfo.builder().userInfo(userInfo).build();
            }

            // Enrich user info
            final User enrichedUserInfo = getEnrichedAndCopiedUserInfo(requestInfo.getUserInfo());
            requestInfo.setUserInfo(enrichedUserInfo);

            // Decrypt each notification's fields individually
            List<ScheduledNotification> result = new ArrayList<>();
            for (ScheduledNotification notification : notifications) {
                try {
                    decryptNotificationFields(notification, requestInfo);
                    result.add(notification);
                } catch (Exception e) {
                    log.error("Failed to decrypt notification id={}: {}", notification.getId(), e.getMessage(), e);
                    // Add notification as-is if decryption fails
                    result.add(notification);
                }
            }

            log.info("Successfully decrypted {} notifications", result.size());
            return result;

        } catch (Exception e) {
            log.error("Error occurred while decrypting", e);
            throw new CustomException(Constants.ERROR_DECRYPTION_FAILED,
                    Constants.MSG_DECRYPTION_ERROR + e.getMessage());
        }
    }

    /**
     * Decrypts specific fields in a ScheduledNotification object.
     */
    private void decryptNotificationFields(ScheduledNotification notification, RequestInfo requestInfo) throws Exception {
        // Decrypt mobileNumber field
        if (notification.getMobileNumber() != null && isCipherText(notification.getMobileNumber())) {
            String decryptedMobile = callDecryptionService(notification.getMobileNumber(), requestInfo);
            notification.setMobileNumber(decryptedMobile);
        }

        // Decrypt contextData field
        Object contextData = notification.getContextData();
        if (contextData != null && contextData instanceof String) {
            String contextDataStr = (String) contextData;
            if (isCipherText(contextDataStr)) {
                String decryptedContext = callDecryptionService(contextDataStr, requestInfo);
                // Parse decrypted JSON string back to Map
                notification.setContextData(objectMapper.readValue(decryptedContext,
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {}));
            }
        }
    }

    /**
     * Calls the decryption service with a single encrypted value.
     * The service expects a JSON structure containing the encrypted value.
     */
    private String callDecryptionService(String encryptedValue, RequestInfo requestInfo) throws Exception {
        // Call decryption service
        StringBuilder uri = new StringBuilder();
        uri.append(properties.getEncryptionServiceHost())
                .append(properties.getDecryptionEndpoint());

        log.debug("Calling decryption API for encrypted value: {}...",
                encryptedValue.length() > 20 ? encryptedValue.substring(0, 20) + "..." : encryptedValue);

        // Wrap the encrypted string in a JSON array (the service processes JSON recursively)
        ArrayNode requestArray = objectMapper.createArrayNode();
        requestArray.add(encryptedValue);

        // The decryption endpoint expects valid JSON
        JsonNode response = serviceRequestClient.fetchResult(uri, requestArray, JsonNode.class);

        if (response == null) {
            throw new CustomException(Constants.ERROR_DECRYPTION_NULL, Constants.MSG_DECRYPTION_NULL);
        }

        // Response should be an array with the decrypted value
        if (response.isArray() && response.size() > 0) {
            JsonNode firstElement = response.get(0);
            if (firstElement.isTextual()) {
                return firstElement.asText();
            } else {
                // If response is an object/array, convert to JSON string
                return objectMapper.writeValueAsString(firstElement);
            }
        } else if (response.isTextual()) {
            return response.asText();
        } else {
            // Fallback: convert entire response to JSON string
            return objectMapper.writeValueAsString(response);
        }
    }

    /**
     * Checks if text is cipher text (format: prefix|base64data).
     */
    private boolean isCipherText(String text) {
        if (text != null && text.contains(Constants.CIPHER_TEXT_SEPARATOR)) {
            String[] parts = text.split("\\" + Constants.CIPHER_TEXT_SEPARATOR);
            if (parts.length == 2) {
                String base64Data = parts[1];
                return base64Data.length() % 4 == 0 || base64Data.endsWith("=");
            }
        }
        return false;
    }

    /**
     * Enriches and copies user info for audit purposes.
     */
    private User getEnrichedAndCopiedUserInfo(User userInfo) {
        List<Role> newRoleList = new ArrayList<>();
        if (userInfo.getRoles() != null) {
            for (Role role : userInfo.getRoles()) {
                Role newRole = Role.builder()
                        .code(role.getCode())
                        .name(role.getName())
                        .id(role.getId())
                        .build();
                newRoleList.add(newRole);
            }
        }

        if (newRoleList.stream()
                .filter(role -> (role.getCode() != null) && (userInfo.getType() != null)
                        && role.getCode().equalsIgnoreCase(userInfo.getType()))
                .count() == 0) {
            Role roleFromType = Role.builder()
                    .code(userInfo.getType())
                    .name(userInfo.getType())
                    .build();
            newRoleList.add(roleFromType);
        }

        User newUserInfo = User.builder()
                .id(userInfo.getId())
                .userName(userInfo.getUserName())
                .name(userInfo.getName())
                .type(userInfo.getType())
                .mobileNumber(userInfo.getMobileNumber())
                .emailId(userInfo.getEmailId())
                .roles(newRoleList)
                .tenantId(userInfo.getTenantId())
                .uuid(userInfo.getUuid())
                .build();
        return newUserInfo;
    }
}
