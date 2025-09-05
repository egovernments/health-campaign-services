# TODO - Template File Validation Warning Optimization

## ✅ Issue Resolved
In template files (generated files), validation warning tooltips appeared on every invalid cell. These should only appear on column headers (row 2 - shown header row). Other validation features remain:
- Keep conditional formatting (rose red color for invalid cells)
- Keep all other visual validation indicators  
- Only move warning tooltips from individual cells to column headers

## ✅ Tasks Completed
- [x] Modify validation tooltip/comment generation
- [x] Move cell comments from data cells to column headers (row 2)
- [x] Keep all other validation formatting unchanged
- [x] Test with template file generation

## ✅ Implementation
Modified `ExcelDataPopulator.java`:
1. **Updated `addCellComments()`** - Now adds comments to row 2 (shown header) instead of row 1
2. **Removed `addUnconditionalCellComments()`** - Eliminated individual data cell comments completely
3. **Updated pure visual validation** - Now calls `addCellComments()` for header-only tooltips
4. **Made tooltip messages localizable** - Added localization support with keys:
   - `HCM_VALIDATION_LABEL` (default: "Validation")
   - `HCM_VALIDATION_HIGHLIGHT_MESSAGE` (default: "Invalid entries will be highlighted in rose/red color")
   - `HCM_CONSOLE_TEAM` (default: "Console Team") - for comment author name
5. **Preserved all other formatting** - Conditional formatting (rose red colors) remains unchanged

## Previous Work ✅
- [x] Processed file validation fix completed
- [x] Sheet protection and cleanup working
- [x] Error columns functioning properly

## ✅ Final Result  
- Template files: validation colors + tooltips ONLY on column headers ✅
- Processed files: clean cells, errors only in columns, fully locked ✅