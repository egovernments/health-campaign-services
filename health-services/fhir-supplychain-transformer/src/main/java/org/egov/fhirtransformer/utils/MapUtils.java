package org.egov.fhirtransformer.utils;

import org.egov.fhirtransformer.common.Constants;
import java.util.*;

/**
 * Utility methods for safely extracting typed values from a Map.
 */
public final class MapUtils {

    /**
     * Retrieves a String value from the map.
     * Returns null if the map, key, or value is null.
     */
    public static String getString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Retrieves a Long value from the map.
     * Works with Number and String types.
     */
    public static Long getLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Long ? (Long) value : null;
    }

    /**
     * 
     * @param newIds
     * @param existingIds
     * @return
     */
    public static HashMap<String, List<String>> splitNewAndExistingIDS(List<String> newIds, List<String> existingIds) {

        HashMap<String,List<String>> newAndExistingIds = new HashMap<>();

        List<String> filteredNewIds = new ArrayList<>(newIds);
        filteredNewIds.removeAll(existingIds);

        newIds.removeAll(existingIds);
        newAndExistingIds.put(Constants.EXISTING_IDS, existingIds);
        newAndExistingIds.put(Constants.NEW_IDS, filteredNewIds);
        return newAndExistingIds;
    }

}
