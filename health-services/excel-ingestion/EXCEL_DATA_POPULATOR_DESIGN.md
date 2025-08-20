# Excel Data Populator - Design Specification

## Function Signature

```java
public Workbook populateSheetWithData(
    Workbook workbook,
    String sheetName,
    List<ColumnDef> columnProperties,
    List<Map<String, Object>> dataRows
)
```

## Parameters

- **workbook** - Target Excel workbook
- **sheetName** - Sheet name to create/replace
- **columnProperties** - Column definitions with formatting rules (name used for localization lookup)
- **dataRows** - Data as Map<unlocalised_code, value>

## Behavior

1. **Sheet Management**: If sheet exists → clear data, create new sheet
2. **Header Creation**: Row 0 (hidden technical names), Row 1 (localized headers from name field)
3. **Data Population**: Map data using unlocalised codes, fill rows starting from Row 2
4. **Formatting**: Apply colors, widths, text wrap, prefixes from column properties
5. **Protection**: Apply freeze rules, cell locking, sheet protection
6. **Validation**: Add dropdowns for enum columns

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

List<Map<String, Object>> data = Arrays.asList(
    Map.of("facility_name", "Hospital A", "facility_type", "PRIMARY"),
    Map.of("facility_name", "Clinic B", "facility_type", "SECONDARY")
);

populateSheetWithData(workbook, "Facilities", columns, data);
```

## Integration Points

- **ExcelStyleHelper** - Header and cell styling
- **CellProtectionManager** - Apply protection rules
- **DataValidationHelper** - Dropdown validations

## Error Handling

- Validate parameters (null checks, matching sizes)
- Handle data type mismatches gracefully
- Log errors but continue processing where possible