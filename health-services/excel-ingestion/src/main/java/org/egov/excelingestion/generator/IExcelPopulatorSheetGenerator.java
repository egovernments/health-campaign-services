package org.egov.excelingestion.generator;

import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.egov.excelingestion.web.models.SheetGenerationResult;

/**
 * Interface for sheet generators that return ExcelPopulator input
 */
public interface IExcelPopulatorSheetGenerator {
    
    /**
     * Generate sheet data for ExcelPopulator
     * 
     * @param config The sheet generation configuration
     * @param generateResource The generate resource request
     * @param requestInfo The request info
     * @param localizationMap Localization map for the sheet
     * @return SheetGenerationResult containing columnDefs and data
     */
    SheetGenerationResult generateSheetData(SheetGenerationConfig config,
                                           GenerateResource generateResource, 
                                           RequestInfo requestInfo,
                                           java.util.Map<String, String> localizationMap);
}