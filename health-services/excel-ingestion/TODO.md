# Excel Ingestion Service - TODO

## Task: Dynamic Data Population Function Design =Ê

**Objective**: Create comprehensive design document for a function that dynamically populates Excel sheets with data using column properties and formatting

### Design Requirements:

#### Core Function Design:
- **Function Name**: `populateSheetWithData`
- **Input Parameters**:
  - `columnProperties` - List of ColumnDef objects with all schema properties
  - `columnHeaders` - Pre-localized column headers (String array)
  - `dataRows` - Array of Map<String, Object> where key = unlocalised column code, value = data
  - `sheetName` - Target sheet name to create/update
  - `workbook` - Target Excel workbook

#### Function Behavior Design:
1. **Sheet Management Logic**: 
   - Check if sheet exists ’ clear all data if found
   - Create new sheet with specified name
   - No localization processing (headers already localized)

2. **Data Population Strategy**:
   - Map column properties by unlocalised technical codes
   - Iterate through data rows and populate cells
   - Apply column formatting from properties:
     - Colors, widths, text wrapping, prefixes
     - Hide/show columns, cell protection rules
     - Data validation and dropdown constraints

3. **Complete Formatting Application**:
   - Header styling with colors and fonts
   - Column width and text wrapping settings
   - Cell protection and sheet protection
   - Multiselect column handling and formulas
   - Auto-height adjustment where needed

### Design Document Requirements:
1. **Create comprehensive design specification** in `EXCEL_DATA_POPULATOR_DESIGN.md`
2. **Include detailed function signatures** with parameter descriptions
3. **Document complete behavior flow** with step-by-step logic
4. **Provide multiple usage examples** with different data scenarios
5. **Show integration points** with existing ExcelStyleHelper, CellProtectionManager
6. **Include error handling scenarios** and edge cases
7. **Add data flow diagrams** and architecture overview
8. **Document performance considerations** and optimization strategies

### Expected Usage Examples to Document:
```java
// Example 1: Basic facility data
List<Map<String, Object>> facilityData = Arrays.asList(
    Map.of("facility_name", "Hospital A", "facility_type", "PRIMARY", "location_code", "LOC001"),
    Map.of("facility_name", "Clinic B", "facility_type", "SECONDARY", "location_code", "LOC002")
);

populateSheetWithData(workbook, "Facilities Data", columnProperties, headers, facilityData);

// Example 2: Mixed data types with protection
// Example 3: Multiselect columns with formulas
// Example 4: Large datasets with performance optimization
```

**Priority**: HIGH - Essential design phase for dynamic data population functionality
**Deliverable**: `EXCEL_DATA_POPULATOR_DESIGN.md` - Complete design specification document
**Note**: **NO CODE IMPLEMENTATION** - Design documentation and specification only