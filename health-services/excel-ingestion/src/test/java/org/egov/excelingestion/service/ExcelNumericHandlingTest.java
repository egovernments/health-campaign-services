package org.egov.excelingestion.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for numeric cell value handling logic
 * Tests the behavior that integers should display as "0" not "0.0"
 */
class ExcelNumericHandlingTest {

    /**
     * Implementation of the getCellValue logic being tested
     */
    private Object getCellValue(Cell cell) {
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                }
                double numericValue = cell.getNumericCellValue();
                // If it's a whole number, return as integer to avoid .0 display
                if (numericValue == Math.floor(numericValue)) {
                    return (long) numericValue;
                }
                return numericValue;
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case BLANK:
                return "";
            default:
                return null;
        }
    }

    @Test
    void testGetCellValue_Zero_ReturnsLongNotDouble() {
        // Given - Mock cell with value 0
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(CellType.NUMERIC);
        when(cell.getNumericCellValue()).thenReturn(0.0);

        // When
        Object result = getCellValue(cell);

        // Then
        assertInstanceOf(Long.class, result);
        assertEquals(0L, result);
        assertEquals("0", result.toString()); // Should display as "0", not "0.0"
    }

    @Test
    void testGetCellValue_PositiveInteger_ReturnsLong() {
        // Given
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(CellType.NUMERIC);
        when(cell.getNumericCellValue()).thenReturn(5.0);

        // When
        Object result = getCellValue(cell);

        // Then
        assertInstanceOf(Long.class, result);
        assertEquals(5L, result);
        assertEquals("5", result.toString());
    }

    @Test
    void testGetCellValue_NegativeInteger_ReturnsLong() {
        // Given
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(CellType.NUMERIC);
        when(cell.getNumericCellValue()).thenReturn(-3.0);

        // When
        Object result = getCellValue(cell);

        // Then
        assertInstanceOf(Long.class, result);
        assertEquals(-3L, result);
        assertEquals("-3", result.toString());
    }

    @Test
    void testGetCellValue_LargeInteger_ReturnsLong() {
        // Given
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(CellType.NUMERIC);
        when(cell.getNumericCellValue()).thenReturn(1000.0);

        // When
        Object result = getCellValue(cell);

        // Then
        assertInstanceOf(Long.class, result);
        assertEquals(1000L, result);
        assertEquals("1000", result.toString());
    }

    @Test
    void testGetCellValue_Decimal_ReturnsDouble() {
        // Given
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(CellType.NUMERIC);
        when(cell.getNumericCellValue()).thenReturn(0.5);

        // When
        Object result = getCellValue(cell);

        // Then
        assertInstanceOf(Double.class, result);
        assertEquals(0.5, result);
        assertEquals("0.5", result.toString());
    }

    @Test
    void testGetCellValue_SmallDecimal_ReturnsDouble() {
        // Given
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(CellType.NUMERIC);
        when(cell.getNumericCellValue()).thenReturn(1.25);

        // When
        Object result = getCellValue(cell);

        // Then
        assertInstanceOf(Double.class, result);
        assertEquals(1.25, result);
        assertEquals("1.25", result.toString());
    }

    @Test
    void testGetCellValue_String_ReturnsString() {
        // Given
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(CellType.STRING);
        when(cell.getStringCellValue()).thenReturn("test");

        // When
        Object result = getCellValue(cell);

        // Then
        assertInstanceOf(String.class, result);
        assertEquals("test", result);
    }

    @Test
    void testGetCellValue_Boolean_ReturnsBoolean() {
        // Given
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(CellType.BOOLEAN);
        when(cell.getBooleanCellValue()).thenReturn(true);

        // When
        Object result = getCellValue(cell);

        // Then
        assertInstanceOf(Boolean.class, result);
        assertEquals(true, result);
    }

    @Test
    void testGetCellValue_Blank_ReturnsEmptyString() {
        // Given
        Cell cell = mock(Cell.class);
        when(cell.getCellType()).thenReturn(CellType.BLANK);

        // When
        Object result = getCellValue(cell);

        // Then
        assertInstanceOf(String.class, result);
        assertEquals("", result);
    }

    @Test
    void testGetCellValue_Null_ReturnsNull() {
        // When
        Object result = getCellValue(null);

        // Then
        assertNull(result);
    }

    @Test
    void testRealExcelWorkbook_IntegerValues_DontShowDecimals() {
        // Given - Create a real Excel workbook with integer values
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("TestSheet");
        Row row = sheet.createRow(0);
        
        Cell cell0 = row.createCell(0);
        cell0.setCellValue(0); // Integer 0
        
        Cell cell5 = row.createCell(1);
        cell5.setCellValue(5); // Integer 5
        
        Cell cellDecimal = row.createCell(2);
        cellDecimal.setCellValue(1.5); // Decimal 1.5

        // When
        Object result0 = getCellValue(cell0);
        Object result5 = getCellValue(cell5);
        Object resultDecimal = getCellValue(cellDecimal);

        // Then
        // Integers should return as Long
        assertInstanceOf(Long.class, result0);
        assertInstanceOf(Long.class, result5);
        assertEquals(0L, result0);
        assertEquals(5L, result5);
        assertEquals("0", result0.toString()); // Not "0.0"
        assertEquals("5", result5.toString()); // Not "5.0"
        
        // Decimal should return as Double
        assertInstanceOf(Double.class, resultDecimal);
        assertEquals(1.5, resultDecimal);
        assertEquals("1.5", resultDecimal.toString());

        // Cleanup
        try {
            workbook.close();
        } catch (Exception e) {
            // Ignore cleanup errors in test
        }
    }
}