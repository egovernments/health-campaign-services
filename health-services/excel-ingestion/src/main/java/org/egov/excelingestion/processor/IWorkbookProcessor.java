package org.egov.excelingestion.processor;

import org.apache.poi.ss.usermodel.Workbook;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.RequestInfo;

import java.util.Map;

/**
 * Interface for processors that work directly on workbooks
 * Returns processed workbook with validation results
 */
public interface IWorkbookProcessor {
    
    /**
     * Process workbook - validate and add error columns directly to workbook
     * 
     * @param workbook Original workbook
     * @param sheetName Name of sheet to process
     * @param resource Process resource containing metadata
     * @param requestInfo Request information
     * @param localizationMap Localization messages
     * @return Processed workbook with error columns added
     */
    Workbook processWorkbook(Workbook workbook, 
                           String sheetName,
                           ProcessResource resource,
                           RequestInfo requestInfo,
                           Map<String, String> localizationMap);
}