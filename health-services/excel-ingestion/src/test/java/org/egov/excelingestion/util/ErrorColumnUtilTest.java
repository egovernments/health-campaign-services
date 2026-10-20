package org.egov.excelingestion.util;

import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for ErrorColumnUtil - focusing on the 3 most important scenarios
 */
class ErrorColumnUtilTest {

    private ErrorColumnUtil errorColumnUtil;
    private Map<String, String> localizationMap;

    @BeforeEach
    void setUp() {
        errorColumnUtil = new ErrorColumnUtil();
        localizationMap = new HashMap<>();
    }

    @Test
    void testCreateErrorColumnDefs_ShouldCreateBothColumnsWithCorrectProperties() {
        // When
        List<ColumnDef> result = errorColumnUtil.createErrorColumnDefs(localizationMap);

        // Then
        assertEquals(2, result.size(), "Should create exactly 2 error columns");

        // Verify Status Column
        ColumnDef statusColumn = result.get(0);
        assertEquals(ValidationConstants.STATUS_COLUMN_NAME, statusColumn.getTechnicalName());
        assertEquals(ValidationConstants.STATUS_COLUMN_NAME, statusColumn.getName());
        assertEquals("string", statusColumn.getType());
        assertTrue(statusColumn.isShowInProcessed());
        assertFalse(statusColumn.isWrapText(), "Status column should not wrap text");
        assertEquals(20, statusColumn.getWidth());
        assertEquals("#FFFF00", statusColumn.getColorHex(), "Should have yellow background");
        assertTrue(statusColumn.isFreezeColumn(), "Status column should be frozen");
        assertEquals(9998, statusColumn.getOrderNumber());

        // Verify Error Details Column
        ColumnDef errorColumn = result.get(1);
        assertEquals(ValidationConstants.ERROR_DETAILS_COLUMN_NAME, errorColumn.getTechnicalName());
        assertEquals(ValidationConstants.ERROR_DETAILS_COLUMN_NAME, errorColumn.getName());
        assertEquals("string", errorColumn.getType());
        assertTrue(errorColumn.isShowInProcessed());
        assertTrue(errorColumn.isWrapText(), "Error details column should wrap text");
        assertEquals(40, errorColumn.getWidth());
        assertEquals("#FFFF00", errorColumn.getColorHex(), "Should have yellow background");
        assertTrue(errorColumn.isFreezeColumn(), "Error details column should be frozen");
        assertEquals(9999, errorColumn.getOrderNumber());
    }

    @Test
    void testCreateStatusColumnDef_ShouldCreateCorrectStatusColumn() {
        // When
        ColumnDef result = errorColumnUtil.createStatusColumnDef();

        // Then
        assertNotNull(result);
        assertEquals(ValidationConstants.STATUS_COLUMN_NAME, result.getTechnicalName());
        assertEquals(ValidationConstants.STATUS_COLUMN_NAME, result.getName());
        assertEquals("string", result.getType());
        assertTrue(result.isShowInProcessed());
        assertFalse(result.isWrapText(), "Status column should not wrap text");
        assertEquals(20, result.getWidth());
        assertEquals("#FFFF00", result.getColorHex());
        assertTrue(result.isFreezeColumn(), "Status column should be frozen");
        assertEquals(9998, result.getOrderNumber());
    }

    @Test
    void testCreateErrorDetailsColumnDef_ShouldCreateCorrectErrorColumn() {
        // When
        ColumnDef result = errorColumnUtil.createErrorDetailsColumnDef();

        // Then
        assertNotNull(result);
        assertEquals(ValidationConstants.ERROR_DETAILS_COLUMN_NAME, result.getTechnicalName());
        assertEquals(ValidationConstants.ERROR_DETAILS_COLUMN_NAME, result.getName());
        assertEquals("string", result.getType());
        assertTrue(result.isShowInProcessed());
        assertTrue(result.isWrapText(), "Error details column should wrap text");
        assertEquals(40, result.getWidth());
        assertEquals("#FFFF00", result.getColorHex());
        assertTrue(result.isFreezeColumn(), "Error details column should be frozen");
        assertEquals(9999, result.getOrderNumber());
    }
}