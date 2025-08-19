# Excel Ingestion Service - TODO

## Configuration Improvements

1. **✅ Create Constants Configuration** - COMPLETED
   - ✅ Moved hardcoded strings like "microplan-ingestion" to ProcessingConstants
   - ✅ Created ProcessingConstants class for type definitions

2. **✅ Sheet-Schema Mapping Configuration** - COMPLETED
   - ✅ Replaced hardcoded sheet name mappings with configurable SheetSchemaConfig
   - ✅ Created SheetSchemaConfig component for different processing types

3. **✅ Update Schema Names** - COMPLETED
   - ✅ Changed schema names from simple names to prefixed names:
     - "facility" → "facility-microplan-ingestion"
     - "user" → "user-microplan-ingestion"
   - ✅ Updated MDMS schema lookup accordingly

4. **✅ Code Refactoring - Large File Analysis** - COMPLETED
   - ✅ Checked files exceeding 500 lines - Found 6 files
   - ✅ Identified oversized files that need refactoring:
     - **MicroplanProcessor.java** (742 lines) - Main processor with multiple responsibilities
     - **SchemaValidationService.java** (460 lines) - Complex validation logic
     - **ExcelSchemaSheetCreator.java** (458 lines) - Excel sheet creation utilities  
     - **CampaignConfigSheetCreator.java** (409 lines) - Campaign config sheet utilities
     - **HierarchyExcelGenerateProcessor.java** (368 lines) - Hierarchy generation logic
     - **BoundaryHierarchySheetCreator.java** (356 lines) - Boundary sheet utilities
   
   **✅ Refactoring Recommendations - COMPLETED:**
   - ✅ Replaced MDMS fetching methods in MicroplanProcessor with direct calls to MDMSService.searchMDMS()
     - ✅ fetchSchemaFromMDMS() → replaced with direct MDMSService.searchMDMS() calls using title filter
     - ✅ fetchCampaignConfigFromMDMS() → replaced with direct MDMSService.searchMDMS() calls using sheetName filter
   - ✅ Created ExcelStyleHelper utility class for Excel styling and formatting
     - ✅ Extracted createBoundaryHeaderStyle() into reusable ExcelStyleHelper.createHeaderStyle()
     - ✅ Added additional styling methods for different cell types (data, numeric, bordered, locked)
     - ✅ Updated MicroplanProcessor to use ExcelStyleHelper

5. **Dependency Injection Cleanup**
   - Remove @Autowired annotations from everywhere in excel-ingestion service
   - Replace all field injection with constructor injection only
   - Ensure all dependencies are properly injected through constructors
   - Follow Spring best practices for dependency injection

6. **Localization Service Integration**
   - Check if process API flow is properly calling localization service
   - Verify localization map is being created and used throughout the process
   - Ensure localized sheet names and field names are properly handled
   - Validate that validation errors use localized messages
   - Confirm localization is passed through the entire validation chain