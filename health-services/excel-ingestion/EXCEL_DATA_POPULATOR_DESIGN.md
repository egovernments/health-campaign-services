# Excel Data Populator - Design Specification

## Function Signature

```java
public Workbook populateSheetWithData(
    String sheetName,
    List<ColumnDef> columnProperties,
    List<Map<String, Object>> dataRows
)
```

## Parameters

- **sheetName** - Sheet name to create/replace (workbook created/fetched internally)
- **columnProperties** - Column definitions with formatting rules (name used for localization lookup)
- **dataRows** - Data as Map<unlocalised_code, value>

## Behavior (Simple Flow)

1. **Create/Get Workbook**: `new XSSFWorkbook()`
2. **Create/Replace Sheet**: Remove if exists, create new with sheetName
3. **Create Headers**: 
   - Use existing `createHeaderRows()` pattern from ExcelSchemaSheetCreator
   - Row 0: technical names, Row 1: localized names
4. **Fill Data** (if not empty/null):
   - Simple loop: for each dataRow → create Excel row → fill cells
5. **Apply Formatting**:
   - Reuse `excelStyleHelper.createCustomHeaderStyle()`
   - Reuse column width, text wrap logic from existing code
6. **Apply Protection**: 
   - Reuse `cellProtectionManager.applyCellProtection()`
7. **Apply Validation**: 
   - Reuse existing dropdown creation logic

## Usage Example

```java
List<ColumnDef> columns = Arrays.asList(
    ColumnDef.builder()
        .name("facility_name")  // Row 0 (hidden), Row 1 shows localized version
        .colorHex("#FFE4B5")
        .width(300)
        .build(),
    ColumnDef.builder()
        .name("facility_type")  // Row 0 (hidden), Row 1 shows localized version
        .enumValues(Arrays.asList("PRIMARY", "SECONDARY"))
        .build()
);

// Example 1: With data
List<Map<String, Object>> data = Arrays.asList(
    Map.of("facility_name", "Hospital A", "facility_type", "PRIMARY"),
    Map.of("facility_name", "Clinic B", "facility_type", "SECONDARY")
);
populateSheetWithData("Facilities", columns, data);

// Example 2: Empty/null data - creates headers-only sheet
populateSheetWithData("Empty Facilities", columns, null);
populateSheetWithData("Template", columns, new ArrayList<>());
```

## Integration Points (Reuse Existing Functions)

- **ExcelStyleHelper.createCustomHeaderStyle()** - Reuse for header styling
- **ExcelStyleHelper.createDataCellStyle()** - Reuse for data cell formatting
- **CellProtectionManager.applyCellProtection()** - Reuse for protection rules
- **ExcelSchemaSheetCreator patterns** - Reuse header creation, validation logic
- **DataValidationHelper** - Reuse existing dropdown creation methods
- **Column width/formatting logic** - Copy from existing ExcelSchemaSheetCreator methods

## Implementation Notes

- **Keep it Simple**: Don't reinvent - copy/reuse existing patterns from ExcelSchemaSheetCreator
- **Reuse Methods**: Call existing helper methods instead of writing new ones
- **Simple Loop**: Basic for-each loop for data population, no complex logic
- **Copy Validation Logic**: Reuse enum dropdown creation from existing code
- **Error Handling**: Basic null checks, continue on errors, log issues