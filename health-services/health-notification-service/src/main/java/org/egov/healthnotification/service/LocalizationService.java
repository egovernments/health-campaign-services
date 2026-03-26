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

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    // Static cache to store localization messages: Key format = "tenantId_locale_code"
    private static final Map<String, String> localizationMessageCache = new ConcurrentHashMap<>();

    @Autowired
    public LocalizationService(ServiceRequestClient serviceRequestClient,
                               HealthNotificationProperties properties) {
        this.serviceRequestClient = serviceRequestClient;
        this.properties = properties;
    }

    /**
     * Initializes the localization cache on service startup.
     * Loads all messages for configured locales and modules.
     */
    @PostConstruct
    public void initializeLocalizationCache() {
        try {
            log.info("Initializing localization message cache on service startup...");

            List<String> supportedLocales = properties.getSupportedLocales();
            String module = properties.getLocalizationNotificationModule();
            String tenantId = properties.getStateLevelTenantId();

            if (supportedLocales == null || supportedLocales.isEmpty()) {
                log.warn("No supported locales configured. Skipping localization cache initialization.");
                return;
            }

            loadAndCacheLocalizationMessages(supportedLocales, module, tenantId);

            log.info("Localization message cache initialized successfully with {} entries",
                    localizationMessageCache.size());

        } catch (Exception e) {
            log.error("Error initializing localization cache on startup. " +
                    "Messages will be fetched on-demand from API.", e);
            // Don't throw exception - allow service to start even if cache initialization fails
        }
    }

    /**
     * Loads all localization messages for the given locales and caches them.
     * This method fetches all messages for the specified module and locales,
     * then stores them in the static cache for quick retrieval.
     *
     * @param locales  List of locales to fetch messages for (e.g., ["en_NG", "fr_NG"])
     * @param module   The module name (e.g., "health-notification-sms")
     * @param tenantId The tenant ID
     */
    public void loadAndCacheLocalizationMessages(List<String> locales, String module, String tenantId) {
        log.info("Loading and caching localization messages for locales: {}, module: {}, tenantId: {}",
                locales, module, tenantId);

        for (String locale : locales) {
            try {
                fetchAndCacheMessagesForLocale(locale, module, tenantId);
                log.info("Successfully loaded and cached messages for locale: {}", locale);
            } catch (Exception e) {
                log.error("Error loading localization messages for locale: {}", locale, e);
                // Continue with other locales even if one fails
            }
        }
    }

    /**
     * Fetches all localization messages for a specific locale and caches them.
     *
     * @param locale   The locale to fetch messages for
     * @param module   The module name
     * @param tenantId The tenant ID
     */
    private void fetchAndCacheMessagesForLocale(String locale, String module, String tenantId) {
        try {
            // Build localization request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("RequestInfo", RequestInfoUtil.buildSystemRequestInfo());

            // Build URI with query parameters (without codes parameter to fetch all)
            StringBuilder uri = new StringBuilder();
            uri.append(properties.getLocalizationHost())
                    .append(properties.getLocalizationContextPath())
                    .append(properties.getLocalizationSearchEndpoint())
                    .append("?locale=").append(locale)
                    .append("&tenantId=").append(properties.getLocalizationStateLevel()
                            ? tenantId.split("\\.")[0]
                            : tenantId)
                    .append("&module=").append(module);

            log.debug("Fetching all localization messages from URI: {}", uri);

            // Fetch localization response
            JsonNode response = serviceRequestClient.fetchResult(uri, requestBody, JsonNode.class);

            // Extract and cache messages from response
            if (response != null && response.has("messages") && response.get("messages").isArray()) {
                JsonNode messagesArray = response.get("messages");
                int cachedCount = 0;

                for (JsonNode messageNode : messagesArray) {
                    if (messageNode.has("code") && messageNode.has("message")) {
                        String code = messageNode.get("code").asText();
                        String message = messageNode.get("message").asText();

                        // Cache key format: tenantId_locale_code
                        String cacheKey = buildCacheKey(tenantId, locale, code);
                        localizationMessageCache.put(cacheKey, message);
                        cachedCount++;
                    }
                }

                log.info("Cached {} localization messages for locale: {}, tenantId: {}",
                        cachedCount, locale, tenantId);
            } else {
                log.warn("No localization messages found in response for locale: {}", locale);
            }

        } catch (Exception e) {
            log.error("Error fetching and caching localization messages for locale: {}", locale, e);
            throw new CustomException("LOCALIZATION_CACHE_ERROR",
                    "Error while fetching and caching localization messages for locale: " + locale);
        }
    }

    /**
     * Gets a localized message from cache. If not found in cache, fetches from API and caches it.
     *
     * @param templateCode The template code
     * @param locale       The locale
     * @param tenantId     The tenant ID
     * @return The localized message
     */
    public String getMessageTemplate(String templateCode, String locale, String tenantId) {
        String cacheKey = buildCacheKey(tenantId, locale, templateCode);

        // Try to get from cache first
        if (localizationMessageCache.containsKey(cacheKey)) {
            log.debug("Fetching message from cache for key: {}", cacheKey);
            return localizationMessageCache.get(cacheKey);
        }

        // If not in cache, fetch from API
        log.info("Message not found in cache for key: {}, fetching from API", cacheKey);
        String message = fetchLocalizationMessage(templateCode, locale, tenantId);

        // Cache the fetched message
        localizationMessageCache.put(cacheKey, message);

        return message;
    }

    /**
     * Builds a cache key for storing/retrieving localization messages.
     * Format: tenantId_locale_code
     *
     * @param tenantId     The tenant ID
     * @param locale       The locale
     * @param templateCode The template code
     * @return The cache key
     */
    private String buildCacheKey(String tenantId, String locale, String templateCode) {
        return tenantId + "_" + locale + "_" + templateCode;
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
