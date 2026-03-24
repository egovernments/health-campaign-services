package org.egov.excelingestion.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.util.CellProtectionManager;
import org.egov.excelingestion.web.models.ValidationColumnInfo;
import org.egov.excelingestion.web.models.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for error column handling in ValidationService
 * Tests that existing error columns are reused and not duplicated
 */
@ExtendWith(MockitoExtension.class)
class ValidationServiceErrorColumnTest {

    @Mock
    private ExcelIngestionConfig config;
    @Mock
    private CellProtectionManager cellProtectionManager;

    private ValidationService validationService;
    private Workbook workbook;

    @BeforeEach
    void setUp() {
        validationService = new ValidationService(config, cellProtectionManager);
        workbook = new XSSFWorkbook();
    }

    @Test
    void testAddValidationColumns_WithNoExistingColumns_ShouldAddNewColumns() {
        // Given: Fresh sheet with data columns
        Sheet sheet = workbook.createSheet("TestSheet");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Name");
        headerRow.createCell(1).setCellValue("Age");
        headerRow.createCell(2).setCellValue("City");

        Row visibleRow = sheet.createRow(1);
        visibleRow.createCell(0).setCellValue("Name");
        visibleRow.createCell(1).setCellValue("Age");
        visibleRow.createCell(2).setCellValue("City");

        // When: Add validation columns
        ValidationColumnInfo columnInfo = validationService.addValidationColumns(sheet);

        // Then: New columns should be added at the end
        assertNotNull(columnInfo);
        assertEquals(3, columnInfo.getStatusColumnIndex()); // After City (index 2)
        assertEquals(4, columnInfo.getErrorColumnIndex()); // After Status (index 3)

        // Verify headers are set correctly
        assertEquals(ValidationConstants.STATUS_COLUMN_NAME, 
                    sheet.getRow(0).getCell(3).getStringCellValue());
        assertEquals(ValidationConstants.ERROR_DETAILS_COLUMN_NAME, 
                    sheet.getRow(0).getCell(4).getStringCellValue());
    }

    @Test
    void testAddValidationColumns_WithExistingStatusColumn_ShouldReuseAndAddError() {
        // Given: Sheet with existing status column
        Sheet sheet = workbook.createSheet("TestSheet");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Name");
        headerRow.createCell(1).setCellValue("Age");
        headerRow.createCell(2).setCellValue(ValidationConstants.STATUS_COLUMN_NAME); // Existing status

        Row visibleRow = sheet.createRow(1);
        visibleRow.createCell(0).setCellValue("Name");
        visibleRow.createCell(1).setCellValue("Age");
        visibleRow.createCell(2).setCellValue("Status");

        // When: Add validation columns
        ValidationColumnInfo columnInfo = validationService.addValidationColumns(sheet);

        // Then: Should reuse existing status column and add error column
        assertNotNull(columnInfo);
        assertEquals(2, columnInfo.getStatusColumnIndex()); // Reuse existing at index 2
        assertEquals(3, columnInfo.getErrorColumnIndex()); // Add error at index 3

        // Verify no new status column was created
        assertEquals(ValidationConstants.STATUS_COLUMN_NAME, 
                    sheet.getRow(0).getCell(2).getStringCellValue());
        assertEquals(ValidationConstants.ERROR_DETAILS_COLUMN_NAME, 
                    sheet.getRow(0).getCell(3).getStringCellValue());
    }

    @Test
    void testAddValidationColumns_WithExistingErrorColumn_ShouldReuseAndAddStatus() {
        // Given: Sheet with existing error column
        Sheet sheet = workbook.createSheet("TestSheet");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Name");
        headerRow.createCell(1).setCellValue("Age");
        headerRow.createCell(2).setCellValue(ValidationConstants.ERROR_DETAILS_COLUMN_NAME); // Existing error

        Row visibleRow = sheet.createRow(1);
        visibleRow.createCell(0).setCellValue("Name");
        visibleRow.createCell(1).setCellValue("Age");
        visibleRow.createCell(2).setCellValue("Error Details");

        // When: Add validation columns
        ValidationColumnInfo columnInfo = validationService.addValidationColumns(sheet);

        // Then: Should add status column and reuse existing error column
        assertNotNull(columnInfo);
        assertEquals(3, columnInfo.getStatusColumnIndex()); // Add status at index 3
        assertEquals(2, columnInfo.getErrorColumnIndex()); // Reuse existing at index 2

        // Verify correct columns
        assertEquals(ValidationConstants.STATUS_COLUMN_NAME, 
                    sheet.getRow(0).getCell(3).getStringCellValue());
        assertEquals(ValidationConstants.ERROR_DETAILS_COLUMN_NAME, 
                    sheet.getRow(0).getCell(2).getStringCellValue());
    }

    @Test
    void testAddValidationColumns_WithBothExistingColumns_ShouldReuseBoth() {
        // Given: Sheet with both existing validation columns
        Sheet sheet = workbook.createSheet("TestSheet");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Name");
        headerRow.createCell(1).setCellValue("Age");
        headerRow.createCell(2).setCellValue(ValidationConstants.STATUS_COLUMN_NAME); // Existing status
        headerRow.createCell(3).setCellValue(ValidationConstants.ERROR_DETAILS_COLUMN_NAME); // Existing error

        Row visibleRow = sheet.createRow(1);
        visibleRow.createCell(0).setCellValue("Name");
        visibleRow.createCell(1).setCellValue("Age");
        visibleRow.createCell(2).setCellValue("Status");
        visibleRow.createCell(3).setCellValue("Error Details");

        // When: Add validation columns
        ValidationColumnInfo columnInfo = validationService.addValidationColumns(sheet);

        // Then: Should reuse both existing columns
        assertNotNull(columnInfo);
        assertEquals(2, columnInfo.getStatusColumnIndex()); // Reuse existing at index 2
        assertEquals(3, columnInfo.getErrorColumnIndex()); // Reuse existing at index 3

        // Verify no new columns were added (should still be 4 columns total)
        assertEquals(4, headerRow.getLastCellNum());
        assertEquals(ValidationConstants.STATUS_COLUMN_NAME, 
                    sheet.getRow(0).getCell(2).getStringCellValue());
        assertEquals(ValidationConstants.ERROR_DETAILS_COLUMN_NAME, 
                    sheet.getRow(0).getCell(3).getStringCellValue());
    }

    @Test
    void testAddValidationColumns_WithExistingColumnsAtDifferentPositions_ShouldReuse() {
        // Given: Sheet with validation columns at different positions
        Sheet sheet = workbook.createSheet("TestSheet");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Name");
        headerRow.createCell(1).setCellValue(ValidationConstants.ERROR_DETAILS_COLUMN_NAME); // Error at position 1
        headerRow.createCell(2).setCellValue("Age");
        headerRow.createCell(3).setCellValue("City");
        headerRow.createCell(4).setCellValue(ValidationConstants.STATUS_COLUMN_NAME); // Status at position 4

        Row visibleRow = sheet.createRow(1);
        visibleRow.createCell(0).setCellValue("Name");
        visibleRow.createCell(1).setCellValue("Error Details");
        visibleRow.createCell(2).setCellValue("Age");
        visibleRow.createCell(3).setCellValue("City");
        visibleRow.createCell(4).setCellValue("Status");

        // When: Add validation columns
        ValidationColumnInfo columnInfo = validationService.addValidationColumns(sheet);

        // Then: Should find and reuse existing columns regardless of position
        assertNotNull(columnInfo);
        assertEquals(4, columnInfo.getStatusColumnIndex()); // Found at index 4
        assertEquals(1, columnInfo.getErrorColumnIndex()); // Found at index 1

        // Verify no new columns were added
        assertEquals(5, headerRow.getLastCellNum());
    }

    @Test
    void testAddValidationColumns_WithLocalization_ShouldUseLocalizedHeaders() {
        // Given: Fresh sheet and localization map
        Sheet sheet = workbook.createSheet("TestSheet");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Name");

        Map<String, String> localizationMap = new HashMap<>();
        localizationMap.put(ValidationConstants.STATUS_COLUMN_NAME, "स्थिति");
        localizationMap.put(ValidationConstants.ERROR_DETAILS_COLUMN_NAME, "त्रुटि विवरण");

        // When: Add validation columns with localization
        ValidationColumnInfo columnInfo = validationService.addValidationColumns(sheet, localizationMap);

        // Then: Technical headers should remain English, visible headers should be localized
        assertNotNull(columnInfo);
        assertEquals(1, columnInfo.getStatusColumnIndex());
        assertEquals(2, columnInfo.getErrorColumnIndex());

        // Row 0 (technical headers) - English
        assertEquals(ValidationConstants.STATUS_COLUMN_NAME, 
                    sheet.getRow(0).getCell(1).getStringCellValue());
        assertEquals(ValidationConstants.ERROR_DETAILS_COLUMN_NAME, 
                    sheet.getRow(0).getCell(2).getStringCellValue());

        // Row 1 (visible headers) - Localized
        assertEquals("स्थिति", 
                    sheet.getRow(1).getCell(1).getStringCellValue());
        assertEquals("त्रुटि विवरण", 
                    sheet.getRow(1).getCell(2).getStringCellValue());
    }

    @Test
    void testProcessValidationErrors_WithExistingColumns_ShouldFillErrors() {
        // Given: Sheet with existing validation columns and data
        Sheet sheet = workbook.createSheet("TestSheet");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Name");
        headerRow.createCell(1).setCellValue(ValidationConstants.STATUS_COLUMN_NAME);
        headerRow.createCell(2).setCellValue(ValidationConstants.ERROR_DETAILS_COLUMN_NAME);

        // Add data rows
        Row dataRow1 = sheet.createRow(2); // Row number 3 (1-based)
        dataRow1.createCell(0).setCellValue("John");

        Row dataRow2 = sheet.createRow(3); // Row number 4 (1-based)
        dataRow2.createCell(0).setCellValue("Jane");

        ValidationColumnInfo columnInfo = new ValidationColumnInfo(1, 2);

        // Create validation errors
        List<ValidationError> errors = Arrays.asList(
            createValidationError(3, "FAILED", "Name is invalid"),
            createValidationError(4, "FAILED", "Required field missing")
        );

        // When: Process validation errors
        validationService.processValidationErrors(sheet, errors, columnInfo, null);

        // Then: Errors should be filled in existing columns
        // Row 2 (0-based) = Row 3 (1-based)
        Row row3 = sheet.getRow(2);
        assertNotNull(row3.getCell(1)); // Status column
        assertNotNull(row3.getCell(2)); // Error column
        assertEquals("Name is invalid", row3.getCell(2).getStringCellValue());

        // Row 3 (0-based) = Row 4 (1-based) 
        Row row4 = sheet.getRow(3);
        assertNotNull(row4.getCell(1)); // Status column
        assertNotNull(row4.getCell(2)); // Error column
        assertEquals("Required field missing", row4.getCell(2).getStringCellValue());
    }

    @Test
    void testMultipleCallsAddValidationColumns_ShouldNotDuplicateColumns() {
        // Given: Fresh sheet
        Sheet sheet = workbook.createSheet("TestSheet");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Name");
        headerRow.createCell(1).setCellValue("Age");

        Row visibleRow = sheet.createRow(1);
        visibleRow.createCell(0).setCellValue("Name");
        visibleRow.createCell(1).setCellValue("Age");

        // When: Call addValidationColumns multiple times
        ValidationColumnInfo columnInfo1 = validationService.addValidationColumns(sheet);
        ValidationColumnInfo columnInfo2 = validationService.addValidationColumns(sheet);
        ValidationColumnInfo columnInfo3 = validationService.addValidationColumns(sheet);

        // Then: Should return same column indices and not add duplicates
        assertEquals(columnInfo1.getStatusColumnIndex(), columnInfo2.getStatusColumnIndex());
        assertEquals(columnInfo1.getErrorColumnIndex(), columnInfo2.getErrorColumnIndex());
        assertEquals(columnInfo2.getStatusColumnIndex(), columnInfo3.getStatusColumnIndex());
        assertEquals(columnInfo2.getErrorColumnIndex(), columnInfo3.getErrorColumnIndex());

        // Should only have 4 columns total (Name, Age, Status, Error)
        assertEquals(4, headerRow.getLastCellNum());
    }

    @Test
    void testAddValidationColumns_WithEmptySheet_ShouldCreateHeadersAndColumns() {
        // Given: Completely empty sheet
        Sheet sheet = workbook.createSheet("TestSheet");

        // When: Add validation columns
        ValidationColumnInfo columnInfo = validationService.addValidationColumns(sheet);

        // Then: Should create header row and add validation columns
        assertNotNull(columnInfo);
        assertEquals(1, columnInfo.getStatusColumnIndex()); // After finding last data column (0)
        assertEquals(2, columnInfo.getErrorColumnIndex()); // After status column

        // Verify headers were created
        Row headerRow = sheet.getRow(0);
        assertNotNull(headerRow);
        assertEquals(ValidationConstants.STATUS_COLUMN_NAME, 
                    headerRow.getCell(1).getStringCellValue());
        assertEquals(ValidationConstants.ERROR_DETAILS_COLUMN_NAME, 
                    headerRow.getCell(2).getStringCellValue());

        // Verify visible row was created
        Row visibleRow = sheet.getRow(1);
        assertNotNull(visibleRow);
        assertNotNull(visibleRow.getCell(1));
        assertNotNull(visibleRow.getCell(2));
    }

    // Helper method
    private ValidationError createValidationError(int rowNumber, String status, String errorDetails) {
        ValidationError error = new ValidationError();
        error.setRowNumber(rowNumber);
        error.setStatus(status);
        error.setErrorDetails(errorDetails);
        return error;
    }
}