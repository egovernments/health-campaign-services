# Excel Ingestion Service - TODO Items

## Multi-Select Column Validations
1. Add min selection and max selection validation in multi-select columns
2. Add duplicate selection validation in multi-select columns
3. If required property is set for enum values or string but multi-select values are empty, validate the required property

## Code Improvements
4. Change `generationClass("org.egov.excelingestion.generator.BoundaryHierarchySheetGenerator")` to use class name only - it will find the class automatically