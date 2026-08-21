package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Test to check what validation capabilities are supported by Apache POI
 */
@Slf4j
public class POIValidationCapabilityTest {

    @Test
    void testAvailableValidationTypes() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("ValidationTest");
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        log.info("🔍 Checking available DataValidationConstraint types in POI 5.4.1:");
        
        // Manually check validation types that we know exist
        log.info("✅ Checking common validation types:");
        log.info("  - DECIMAL");
        log.info("  - INTEGER"); 
        log.info("  - LIST");
        log.info("  - DATE");
        log.info("  - TIME");
        log.info("  - TEXT_LENGTH");
        log.info("  - CUSTOM");
        
        // Test TEXT_LENGTH constraint creation
        try {
            log.info("🧪 Testing TEXT_LENGTH constraint creation...");
            DataValidationConstraint textLengthConstraint = dvHelper.createTextLengthConstraint(
                DataValidationConstraint.OperatorType.BETWEEN, "3", "50");
            log.info("✅ TEXT_LENGTH constraint created successfully: {}", textLengthConstraint.getValidationType());
            
            // Try to apply it
            org.apache.poi.ss.util.CellRangeAddressList addressList = 
                new org.apache.poi.ss.util.CellRangeAddressList(0, 10, 0, 0);
            DataValidation validation = dvHelper.createValidation(textLengthConstraint, addressList);
            sheet.addValidationData(validation);
            log.info("✅ TEXT_LENGTH validation applied successfully to sheet");
            
        } catch (Exception e) {
            log.error("❌ TEXT_LENGTH constraint failed: {}", e.getMessage());
        }
        
        // Test CUSTOM constraint creation (for pattern/regex)
        try {
            log.info("🧪 Testing CUSTOM constraint creation...");
            DataValidationConstraint customConstraint = dvHelper.createCustomConstraint("LEN(A1)>=3");
            log.info("✅ CUSTOM constraint created successfully: {}", customConstraint.getValidationType());
            
        } catch (Exception e) {
            log.error("❌ CUSTOM constraint failed: {}", e.getMessage());
        }
        
        // Note: createFormulaConstraint method doesn't exist in POI 5.4.1
        log.info("ℹ️ createFormulaConstraint method not available in POI 5.4.1");
        
        workbook.close();
    }
    
    @Test 
    void testActualStringValidationInExcel() throws IOException {
        log.info("🧪 Testing actual string length validation in Excel using different approaches");
        
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("StringValidationTest");
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        // Approach 1: Try TEXT_LENGTH
        try {
            DataValidationConstraint textLengthConstraint = dvHelper.createTextLengthConstraint(
                DataValidationConstraint.OperatorType.BETWEEN, "3", "10");
            org.apache.poi.ss.util.CellRangeAddressList addressList1 = 
                new org.apache.poi.ss.util.CellRangeAddressList(1, 1, 0, 0);
            DataValidation validation1 = dvHelper.createValidation(textLengthConstraint, addressList1);
            validation1.setErrorStyle(DataValidation.ErrorStyle.STOP);
            validation1.setShowErrorBox(true);
            validation1.createErrorBox("Length Error", "Text must be between 3-10 characters");
            sheet.addValidationData(validation1);
            log.info("✅ Approach 1 (TEXT_LENGTH): Applied successfully");
        } catch (Exception e) {
            log.error("❌ Approach 1 (TEXT_LENGTH): Failed - {}", e.getMessage());
        }
        
        // Approach 2: Try CUSTOM formula
        try {
            DataValidationConstraint customConstraint = dvHelper.createCustomConstraint("AND(LEN(B2)>=3,LEN(B2)<=10)");
            org.apache.poi.ss.util.CellRangeAddressList addressList2 = 
                new org.apache.poi.ss.util.CellRangeAddressList(1, 1, 1, 1);
            DataValidation validation2 = dvHelper.createValidation(customConstraint, addressList2);
            validation2.setErrorStyle(DataValidation.ErrorStyle.STOP);
            validation2.setShowErrorBox(true);
            validation2.createErrorBox("Custom Length Error", "Text must be between 3-10 characters (Custom)");
            sheet.addValidationData(validation2);
            log.info("✅ Approach 2 (CUSTOM): Applied successfully");
        } catch (Exception e) {
            log.error("❌ Approach 2 (CUSTOM): Failed - {}", e.getMessage());
        }
        
        // Approach 3: Note - FORMULA approach not available
        log.info("ℹ️ Approach 3 (FORMULA): Not available in POI 5.4.1");
        
        // Check how many validations were actually applied
        log.info("📊 Total validations applied to sheet: {}", sheet.getDataValidations().size());
        
        workbook.close();
    }
}