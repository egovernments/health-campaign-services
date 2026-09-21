package org.egov.excelingestion.web.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration for processor generation containing list of sheets to generate
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessorGenerationConfig {
    
    /**
     * List of sheets to generate for this processor
     */
    private List<SheetGenerationConfig> sheets;
    
    /**
     * Whether to apply workbook-level protection
     */
    private boolean applyWorkbookProtection;
    
    /**
     * Password for sheet/workbook protection
     */
    private String protectionPassword;
    
    /**
     * Zoom level for all sheets (e.g., 60 for 60%)
     */
    private Integer zoomLevel;
}