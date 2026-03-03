package org.egov.healthnotification.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.util.RequestInfoUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with the Localization Service.
 * Used to fetch localized messages and templates based on locale and template codes.
 *
 * The localization API is called with the following parameters:
 * - locale: The locale code (e.g., en_NG, fr_NG)
 * - tenantId: The tenant ID (state level if configured)
 * - module: The module name configured in application.properties (egov.localization.notification.module)
 * - codes: The template code to fetch (e.g., HCM_ITN_POST_DISTRIBUTION_3DAY_SMS)
 */
@Service
@Slf4j
public class LocalizationService {

    private final ServiceRequestClient serviceRequestClient;
    private final HealthNotificationProperties properties;

    @Autowired
    public LocalizationService(ServiceRequestClient serviceRequestClient,
                               HealthNotificationProperties properties) {
        this.serviceRequestClient = serviceRequestClient;
        this.properties = properties;
    }

    /**
     * Fetches localized messages for multiple template codes.
     *
     * @param templateCodes List of template codes (e.g., HCM_ITN_POST_DISTRIBUTION_3DAY_SMS)
     * @param locale        The locale to fetch messages for (e.g., en_NG)
     * @param tenantId      The tenant ID
     * @return Map of templateCode to localized message
     */
    public Map<String, String> fetchLocalizationMessages(List<String> templateCodes, String locale, String tenantId) {
        log.info("Fetching localization messages for {} template codes, locale: {}, tenantId: {}",
                templateCodes.size(), locale, tenantId);

        Map<String, String> localizationMap = new HashMap<>();

        for (String templateCode : templateCodes) {
            try {
                String message = fetchLocalizationMessage(templateCode, locale, tenantId);
                localizationMap.put(templateCode, message);
                log.info("Successfully fetched localization for templateCode: {}", templateCode);
            } catch (Exception e) {
                log.error("Error fetching localization for templateCode: {}", templateCode, e);
                // Continue with other template codes even if one fails
            }
        }

        if (localizationMap.isEmpty()) {
            log.error("Failed to fetch any localization messages for templateCodes: {}", templateCodes);
            throw new CustomException("LOCALIZATION_FETCH_ERROR",
                    "Failed to fetch localization messages for the provided template codes");
        }

        return localizationMap;
    }

    /**
     * Fetches a single localized message for a template code.
     *
     * @param templateCode The template code
     * @param locale       The locale
     * @param tenantId     The tenant ID
     * @return The localized message
     */
    public String fetchLocalizationMessage(String templateCode, String locale, String tenantId) {
        log.info("Fetching localization message for templateCode: {}, locale: {}, tenantId: {}",
                templateCode, locale, tenantId);

        try {
            // Build localization request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("RequestInfo", RequestInfoUtil.buildSystemRequestInfo());

            // Build URI with query parameters
            StringBuilder uri = new StringBuilder();
            uri.append(properties.getLocalizationHost())
                    .append(properties.getLocalizationContextPath())
                    .append(properties.getLocalizationSearchEndpoint())
                    .append("?locale=").append(locale)
                    .append("&tenantId=").append(properties.getLocalizationStateLevel()
                            ? tenantId.split("\\.")[0]
                            : tenantId)
                    .append("&module=").append(properties.getLocalizationNotificationModule())
                    .append("&codes=").append(templateCode);

            log.debug("Localization request URI: {}", uri);

            // Fetch localization response
            JsonNode response = serviceRequestClient.fetchResult(uri, requestBody, JsonNode.class);

            // Extract message from response
            if (response != null && response.has("messages") && response.get("messages").isArray()) {
                JsonNode messagesArray = response.get("messages");
                if (messagesArray.size() > 0) {
                    JsonNode messageNode = messagesArray.get(0);
                    if (messageNode.has("message")) {
                        String message = messageNode.get("message").asText();
                        log.info("Successfully fetched localization message for templateCode: {}", templateCode);
                        return message;
                    }
                }
            }

            log.error("No localization message found for templateCode: {}, locale: {}", templateCode, locale);
            throw new CustomException("LOCALIZATION_MESSAGE_NOT_FOUND",
                    String.format("No localization message found for templateCode: %s, locale: %s",
                            templateCode, locale));

        } catch (HttpClientErrorException e) {
            log.error("HTTP error while fetching localization: {}", e.getMessage());
            throw new CustomException("HTTP_CLIENT_ERROR",
                    String.format("%s - %s", e.getMessage(), e.getResponseBodyAsString()));
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching localization for templateCode: {}", templateCode, e);
            throw new CustomException("LOCALIZATION_FETCH_ERROR",
                    "Error while fetching localization message for templateCode: " + templateCode);
        }
    }
}
