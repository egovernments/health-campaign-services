package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the target-sheet schema properties dynamically from a campaign's configured delivery
 * resources, mirroring the structure of the static "target-&lt;projectType&gt;" MDMS schema
 * (properties.numberProperties). Used by both the boundary sheet generator (template columns) and
 * the boundary target processor (upload validation) so the two sides always derive the same columns.
 */
@Component
@Slf4j
public class DynamicTargetSchemaUtil {

    private static final String TARGET_COLUMN_PREFIX = "HCM_ADMIN_CONSOLE_TARGET_";
    private static final String TARGET_PREFIX_LOCALIZATION_KEY = "HCM_ADMIN_CONSOLE_TARGET";
    private static final String TARGET_PREFIX_DEFAULT = "Target";

    /**
     * Build schema properties (same shape as the static target schema's "properties" object) with
     * one number column per configured delivery resource, in configuration order.
     */
    public Map<String, Object> buildTargetSchemaProperties(List<String> resourceNames, String projectType) {
        List<Map<String, Object>> numberProperties = new ArrayList<>();
        int order = 1;
        for (String resourceName : resourceNames) {
            Map<String, Object> prop = new HashMap<>();
            prop.put("name", buildTargetColumnCode(projectType, resourceName));
            prop.put("type", "number");
            prop.put("color", "#93c47d");
            prop.put("minimum", 1);
            prop.put("maximum", 100000000);
            prop.put("isRequired", true);
            prop.put("multipleOf", 1);
            prop.put("description", projectType + " " + resourceName);
            prop.put("orderNumber", order++);
            prop.put("unFreezeColumnTillData", true);
            numberProperties.add(prop);
        }
        Map<String, Object> properties = new HashMap<>();
        properties.put("numberProperties", numberProperties);
        return properties;
    }

    /**
     * Stable, locale-independent column code for a resource, e.g.
     * "HCM_ADMIN_CONSOLE_TARGET_CO-DELIVERY_VITAMIN_A_SUPPLEMENT".
     */
    public String buildTargetColumnCode(String projectType, String resourceName) {
        String sanitized = resourceName.trim().toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return TARGET_COLUMN_PREFIX + projectType + "_" + sanitized;
    }

    /**
     * Inject readable header labels for the dynamic column codes into the localization map (e.g.
     * "Target (Vitamin A Supplement)"), since dynamically derived codes have no entries in the
     * localization service. Values are deterministic from the key, so putIfAbsent on a shared map
     * is safe.
     */
    public void addHeaderLocalizations(List<String> resourceNames, String projectType,
                                       Map<String, String> localizationMap) {
        if (localizationMap == null) {
            return;
        }
        String localizedTargetPrefix = LocalizationUtil.getLocalizedMessage(
                localizationMap, TARGET_PREFIX_LOCALIZATION_KEY, TARGET_PREFIX_DEFAULT);
        try {
            for (String resourceName : resourceNames) {
                localizationMap.putIfAbsent(buildTargetColumnCode(projectType, resourceName),
                        localizedTargetPrefix + " (" + resourceName + ")");
            }
        } catch (UnsupportedOperationException e) {
            log.warn("Localization map is immutable; dynamic target headers will show raw column codes");
        }
    }
}
