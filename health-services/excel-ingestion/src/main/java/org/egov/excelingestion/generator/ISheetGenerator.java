package org.egov.excelingestion.generator;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.SheetGenerationConfig;

/**
 * Interface for sheet generators that create workbook directly
 */
public interface ISheetGenerator {
    
    /**
     * Generate sheet directly in the workbook
     * 
     * @param workbook The workbook to add the sheet to
     * @param sheetName The name of the sheet to create
     * @param config The sheet generation configuration
     * @param generateResource The generate resource request
     * @param requestInfo The request info
     * @param localizationMap Localization map for the sheet
     * @return Modified workbook with the new sheet
     */
    XSSFWorkbook generateSheet(XSSFWorkbook workbook, 
                              String sheetName, 
                              SheetGenerationConfig config,
                              GenerateResource generateResource, 
                              RequestInfo requestInfo,
                              java.util.Map<String, String> localizationMap);
}