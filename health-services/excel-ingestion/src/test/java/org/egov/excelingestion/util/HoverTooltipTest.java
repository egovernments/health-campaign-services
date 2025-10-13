package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test to verify hover tooltips work with hybrid validation approach
 */
@Slf4j
class HoverTooltipTest {

    @Test
    void testHoverTooltipsWithHybridValidation() throws IOException {
        log.info("🧪 Testing hover tooltips with hybrid validation (WARNING + conditional formatting)");
        
        // Set up ExcelDataPopulator
        ExcelIngestionConfig config = new ExcelIngestionConfig();
        config.setExcelRowLimit(1000);
        
        ExcelStyleHelper styleHelper = new ExcelStyleHelper();
        CellProtectionManager protectionManager = new CellProtectionManager(config, styleHelper);
        ExcelDataPopulator populator = new ExcelDataPopulator(config, styleHelper, protectionManager);
        
        // Create test columns
        List<ColumnDef> columns = Arrays.asList(
            createNumberColumn("age", 18.0, 65.0),          // Number with range
            createTextColumn("name", 2, 50),                // Text with length
            createPureNumberColumn("score")                 // Pure numeric validation (no range)
        );
        
        // Create localization map
        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put("HCM_VALIDATION_NUMBER_BETWEEN", "मान %s और %s के बीच होना चाहिए");
        localizationMap.put("HCM_VALIDATION_TEXT_LENGTH_BETWEEN", "पाठ की लंबाई %s और %s वर्णों के बीच होनी चाहिए");
        localizationMap.put("HCM_VALIDATION_NUMBER_ONLY", "केवल संख्या होनी चाहिए"); // Hindi: Must be number only
        localizationMap.put("HCM_CONSOLE_TEAM", "कंसोल टीम"); // Hindi: Console Team
        
        // Generate Excel workbook
        Workbook workbook = populator.populateSheetWithData("HoverTooltipTest", columns, null, localizationMap);
        
        // Get sheet and check validations
        Sheet sheet = workbook.getSheetAt(0);
        List<? extends DataValidation> validations = sheet.getDataValidations();
        
        log.info("Found {} data validations", validations.size());
        
        // Should have WARNING style validations for tooltips
        int warningCount = 0;
        for (DataValidation validation : validations) {
            int styleValue = validation.getErrorStyle();
            if (styleValue == DataValidation.ErrorStyle.WARNING) {
                warningCount++;
                log.info("WARNING validation found with error box text: {}", validation.getErrorBoxText());
            }
        }
        
        log.info("Found {} WARNING style validations", warningCount);
        
        // Save Excel file for manual testing
        try (FileOutputStream fos = new FileOutputStream("/tmp/hover_tooltip_test.xlsx")) {
            workbook.write(fos);
            log.info("✅ Excel file saved to /tmp/hover_tooltip_test.xlsx");
            log.info("   Manual test instructions:");
            log.info("   1. Open the Excel file");
            log.info("   2. Enter invalid data:");
            log.info("      - Age: Enter 100 (outside 18-65 range)");
            log.info("      - Name: Enter 'A' (less than 2 characters)");
            log.info("      - Score: Enter 'ABC' (non-numeric data)");
            log.info("   3. Expected behavior:");
            log.info("      - Data entry should be allowed (no blocking popup)");
            log.info("      - Cell should turn rose/red color after entry");
            log.info("      - Hovering over the cell should show localized tooltip with error message");
        }
        
        workbook.close();
        
        log.info("✅ Hover tooltip test completed - manual verification required");
    }
    
    // Helper methods
    
    private ColumnDef createNumberColumn(String name, Double min, Double max) {
        ColumnDef column = new ColumnDef();
        column.setName(name);
        column.setType("number");
        column.setMinimum(min);
        column.setMaximum(max);
        return column;
    }
    
    private ColumnDef createTextColumn(String name, Integer minLength, Integer maxLength) {
        ColumnDef column = new ColumnDef();
        column.setName(name);
        column.setType("string");
        column.setMinLength(minLength);
        column.setMaxLength(maxLength);
        return column;
    }
    
    private ColumnDef createPureNumberColumn(String name) {
        ColumnDef column = new ColumnDef();
        column.setName(name);
        column.setType("number");
        // No min/max - pure numeric validation only
        return column;
    }
}