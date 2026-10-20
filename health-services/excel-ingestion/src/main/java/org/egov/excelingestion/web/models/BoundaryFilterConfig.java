package org.egov.excelingestion.web.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Configuration for boundary-based user filtering in sheet generation.
 *
 * Modes:
 * - ANCESTOR_AND_SELF: include users at register locality or any ancestor boundary. Used for Marker/Approver sheets.
 * - LEVEL_RANGE: include users at register locality down to a configured deepest boundary type.
 *   levelConfig maps { registerBoundaryType → deepestAllowedBoundaryType }.
 *   If no entry for the register boundary type, only the register locality itself is included.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoundaryFilterConfig {

    /**
     * Filter mode: ANCESTOR_AND_SELF | LEVEL_RANGE
     */
    private String mode;

    /**
     * Used only for LEVEL_RANGE mode.
     * Maps register boundary type to the deepest user boundary type to include.
     * Example: { "DISTRICT": "VILLAGE", "PROVINCE": "DISTRICT" }
     */
    private Map<String, String> levelConfig;
}
