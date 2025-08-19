# Excel Ingestion Service - TODO

## Configuration Improvements

1. **✅ Create Constants Configuration** - COMPLETED
   - ✅ Moved hardcoded strings like "microplan-ingestion" to ProcessingConstants
   - ✅ Created ProcessingConstants class for type definitions

2. **✅ Sheet-Schema Mapping Configuration** - COMPLETED
   - ✅ Replaced hardcoded sheet name mappings with configurable SheetSchemaConfig
   - ✅ Created SheetSchemaConfig component for different processing types

3. **Update Schema Names**
   - Change schema names from simple names to prefixed names:
     - "facility" → "facility-microplan-ingestion"
     - "user" → "user-microplan-ingestion"
   - Update MDMS schema lookup accordingly

4. **Code Refactoring - Large File Analysis**
   - Check if any files in excel-ingestion service exceed 500 lines
   - Identify oversized files that need refactoring/splitting
   - Break down large classes into smaller, focused components
   - Consider extracting utility methods, creating separate service classes, or using composition patterns

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