# **High-Priority Refactoring Opportunities**

## 🔒 1. Security & Configuration

* **Hardcoded Password** → `"passwordhere"` appears in 6+ places.
* **Magic Numbers** → Row limit (`5000`), column width (`40 * 256`), zoom (`70`) hardcoded.
* **Locale Fallback** → `"en_MZ"` hardcoded in multiple files.

## 🏗️ 2. Service Extraction

* **MDMS Integration** → Schema-fetching logic embedded inside processors.
* **Boundary Data Processing** → Business rules mixed with Excel creation code.
* **Excel Security** → Protection logic scattered across multiple classes.

---

# **Medium-Priority Refactoring Opportunities**

## 🎨 3. Missing Common Utilities

* **Excel Formatting** → Column widths, freeze panes, and styling logic repeated.
* **Name Validation** → Multiple variations of `makeNameValid()` across the codebase.
* **Sheet Name Handling** → 31-character limit logic repeated in multiple places.
* **Data Validation** → Dropdown creation logic duplicated in different processors.

## 🔧 4. Pattern & Structure Improvements

* **Locale Extraction** → Same `msgId` parsing logic repeated in multiple places.
* **Error Handling** → Similar try-catch API call patterns across services.
* **Interface Contracts** → `IGenerateProcessor` missing a `generateExcel()` signature for consistency.

---

# **Refactoring Impact Summary**

| Category         | Current Issues       | After Refactoring     | Benefit                |
| ---------------- | -------------------- | --------------------- | ---------------------- |
| Configuration    | 15+ hardcoded values | Config-driven         | Easier customization   |
| Security         | Hardcoded passwords  | Configurable secrets  | Improved security      |
| Maintainability  | Logic scattered      | Service-oriented      | Easier to change       |
| Testing          | Tightly coupled      | Separated concerns    | Better testability     |

---
