package org.egov.excelingestion.processor;

import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.SheetGenerationResult;

import java.util.List;
import java.util.Map;

/**
 * Interface for sheet data processors that can validate and modify data
 */
public interface ISheetDataProcessor {
    
    /**
     * Process sheet data - validate and add error information
     * Returns both column definitions and processed data for Excel population
     * 
     * @param originalData Original sheet data
     * @param resource Process resource containing metadata
     * @param requestInfo Request information
     * @param localizationMap Localization messages
     * @return SheetGenerationResult with column definitions and processed data
     */
    SheetGenerationResult processSheetData(List<Map<String, Object>> originalData,
                                          ProcessResource resource,
                                          RequestInfo requestInfo,
                                          Map<String, String> localizationMap);
}