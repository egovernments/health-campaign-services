# Excel Ingestion Service - TODO

## Task: Implement ExcelDataPopulator Function 🚀

**Objective**: Read design document and implement the `populateSheetWithData` function

### Implementation Requirements:

1. **Read Design Document**: Study `EXCEL_DATA_POPULATOR_DESIGN.md` thoroughly
2. **Create Utility Class**: Create `ExcelDataPopulator.java` in util package
3. **Implement Function**: 
   ```java
   public Workbook populateSheetWithData(
       String sheetName,
       List<ColumnDef> columnProperties, 
       List<Map<String, Object>> dataRows
   )
   ```

4. **Follow Design Principles**:
   - Keep it simple - reuse existing functions
   - Copy patterns from ExcelSchemaSheetCreator
   - Use ExcelStyleHelper and CellProtectionManager
   - Handle empty/null data gracefully (headers-only sheets)

5. **Implementation Steps**:
   - Create/get workbook internally
   - Remove existing sheet if present, create new one
   - Create headers (Row 0: technical, Row 1: localized)
   - Fill data rows (if data not empty/null)
   - Apply formatting, protection, and validation
   - Return completed workbook

**Priority**: HIGH - Core functionality implementation
**Files**: Create `ExcelDataPopulator.java`, integrate with existing utilities
**Note**: Follow design document exactly - reuse existing code patterns