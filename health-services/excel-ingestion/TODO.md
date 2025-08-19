# Excel Ingestion Service - TODO

## Configuration Improvements Needed

1. **Create Constants Configuration**
   - Move hardcoded strings like "microplan-ingestion" to constants
   - Create ProcessingConstants class for type definitions

2. **Sheet-Schema Mapping Configuration**
   - Replace hardcoded sheet name mappings with configurable approach
   - Create SheetSchemaConfig for different processing types

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