# Important Generic Excel Utility Functions

## 📊 Core Excel Operations

### ExcelUtil
- **`getCellValueAsString(Cell cell)`** - Safely extract any cell value as string, handles formulas
- **`findActualLastRowWithData(Sheet sheet)`** - Find real last row with data using binary search + caching
- **`convertSheetToMapListCached(fileId, sheetName, sheet)`** - Convert sheet to List<Map> with caching

## 🌐 Localization  
### LocalizationUtil
- **`getLocalizedMessage(localizationMap, key, defaultMessage)`** - Get localized text with fallback
- **`getLocalizedMessage(localizationMap, key, defaultMessage, params...)`** - With parameter substitution {0}, {1}

## 🎨 Excel Styling
### ExcelStyleHelper
- **`createHeaderStyle(workbook, colorHex)`** - Header style with color, bold, center align
- **`createDataCellStyle(workbook, wrapText)`** - Data cell style with optional text wrap
- **`createLockedCellStyle(workbook)`** / **`createUnlockedCellStyle(workbook)`** - Protection styles

## 🛡️ Sheet Protection
### CellProtectionManager  
- **`applySheetProtection(workbook, sheet, password)`** - Password protect sheet
- **`applyCellProtection(workbook, sheet, columns)`** - Lock/unlock cells based on column config

## 📊 Data Population
### ExcelDataPopulator
- **`populateSheetWithData(sheetName, columns, data)`** - Create workbook with data
- **`populateSheetWithData(workbook, sheetName, columns, data, localizationMap)`** - Add to existing workbook

## 🗂️ Error Handling
### ErrorColumnUtil
- **`createErrorColumnDefs(localizationMap)`** - Create status + error details columns
- **`createStatusColumnDef()`** - Status column definition
- **`createErrorDetailsColumnDef()`** - Error details column definition

## 🌍 Boundary Operations
### BoundaryUtil
- **`getEnrichedBoundaryCodesFromCampaign(processId, referenceId, tenantId, hierarchyType, requestInfo)`** - Get valid boundary codes
- **`getLowestLevelBoundaryCodesFromCampaign(...)`** - Get only lowest level boundaries

### BoundaryColumnUtil
- **`addBoundaryColumnsToSheet(workbook, sheetName, localizationMap, tenantId, hierarchyType, requestInfo)`** - Add boundary columns with dropdowns

## 📈 Data Enrichment  
### EnrichmentUtil
- **`enrichRowCountInAdditionalDetails(resource, rowCount)`** - Add row count to resource metadata
- **`enrichErrorAndStatusInAdditionalDetails(resource, validationErrors)`** - Add error info to resource

## 🔄 Request Utils
### RequestInfoConverter
- **`extractLocale(requestInfo)`** - Get locale from request context