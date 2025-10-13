package org.egov.excelingestion.util;

import java.util.Map;

/**
 * Centralized utility class for handling localization messages
 * Provides methods to retrieve localized messages with fallback to default messages
 */
public final class LocalizationUtil {

    private LocalizationUtil() {
        // Private constructor to prevent instantiation
    }

    /**
     * Gets a localized message from the localization map
     * @param localizationMap Map containing localized messages
     * @param key The localization key
     * @param defaultMessage Default message if key not found
     * @return Localized message or default message
     */
    public static String getLocalizedMessage(Map<String, String> localizationMap, String key, String defaultMessage) {
        if (localizationMap != null && key != null && localizationMap.containsKey(key)) {
            return localizationMap.get(key);
        }
        return defaultMessage;
    }

    /**
     * Gets a localized message from the localization map with parameter substitution
     * Supports parameter placeholders like {0}, {1}, {2}, etc.
     * @param localizationMap Map containing localized messages
     * @param key The localization key
     * @param defaultMessage Default message if key not found
     * @param params Parameters to substitute in the message
     * @return Localized message with parameters substituted or default message
     */
    public static String getLocalizedMessage(Map<String, String> localizationMap, String key, String defaultMessage, String... params) {
        String message;
        if (localizationMap != null && key != null && localizationMap.containsKey(key)) {
            message = localizationMap.get(key);
            // Simple parameter replacement for {0}, {1}, etc.
            for (int i = 0; i < params.length; i++) {
                if (params[i] != null) {
                    message = message.replace("{" + i + "}", params[i]);
                }
            }
        } else {
            message = defaultMessage;
        }
        
        return cleanErrorMessage(message);
    }

    /**
     * Cleans up error messages by removing leading semicolons and extra whitespace
     * @param message The message to clean
     * @return Cleaned message
     */
    private static String cleanErrorMessage(String message) {
        if (message == null) {
            return "";
        }
        
        String cleaned = message.trim();
        
        // Remove leading semicolons
        while (cleaned.startsWith(";")) {
            cleaned = cleaned.substring(1).trim();
        }
        
        // Remove trailing semicolons
        while (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        
        return cleaned;
    }
}