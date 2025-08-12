# **High-Priority Refactoring Opportunities**

## 🔥 1. Code Duplication

* **RequestInfo Conversion** → 25+ identical lines duplicated in multiple processors.
* **API Payload Creation** → Same payload-building logic repeated across 3 classes.
* **Boundary Data Processing** → Complex business logic intertwined with Excel generation code.

## 🔒 2. Security & Configuration

* **Hardcoded Password** → `"passwordhere"` appears in 6+ places.
* **Magic Numbers** → Row limit (`5000`), column width (`40 * 256`), zoom (`70`) hardcoded.
* **Locale Fallback** → `"en_MZ"` hardcoded in multiple files.

## 🏗️ 3. Service Extraction

* **MDMS Integration** → Schema-fetching logic embedded inside processors.
* **Boundary Data Processing** → Business rules mixed with Excel creation code.
* **Excel Security** → Protection logic scattered across multiple classes.

---

# **Medium-Priority Refactoring Opportunities**

## 🎨 4. Missing Common Utilities

* **Excel Formatting** → Column widths, freeze panes, and styling logic repeated.
* **Name Validation** → Multiple variations of `makeNameValid()` across the codebase.
* **Sheet Name Handling** → 31-character limit logic repeated in multiple places.
* **Data Validation** → Dropdown creation logic duplicated in different processors.

## 🔧 5. Pattern & Structure Improvements

* **Locale Extraction** → Same `msgId` parsing logic repeated in multiple places.
* **Error Handling** → Similar try-catch API call patterns across services.
* **Interface Contracts** → `IGenerateProcessor` missing a `generateExcel()` signature for consistency.

---

# **Refactoring Impact Summary**

| Category         | Current Issues       | After Refactoring     | Benefit                |
| ---------------- | -------------------- | --------------------- | ---------------------- |
| Code Duplication | 200+ duplicate lines | Centralized utilities | \~60% less duplication |
| Configuration    | 15+ hardcoded values | Config-driven         | Easier customization   |
| Security         | Hardcoded passwords  | Configurable secrets  | Improved security      |
| Maintainability  | Logic scattered      | Service-oriented      | Easier to change       |
| Testing          | Tightly coupled      | Separated concerns    | Better testability     |

---

# **Recommended Refactoring Plan**

## **Phase 1** (Week 1–2)

1. Create **`RequestInfoConverter`** utility.
2. Create **`ApiPayloadBuilder`** service for building API request payloads.
3. Move all hardcoded values to **`ExcelIngestionConfig`**.
4. Create **`ExcelSecurityManager`** to centralize sheet protection logic.

## **Phase 2** (Week 3–4)

5. Extract **`MdmsSchemaService`** for MDMS schema fetching.
6. Create **`BoundaryDataProcessor`** to handle boundary-related business logic separately.
7. Implement **`ExcelFormattingUtil`** for styling, column widths, and freeze panes.
8. Create **`ExcelValidationService`** for dropdowns and data validations.

---
