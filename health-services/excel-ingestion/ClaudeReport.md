# 📄 Claude Max Usage Report

## 1️⃣ Project Context

* **Project Name:** Microplan Ingestion
* **Service Name:** `excel-ingestion`
* **Purpose:** Automate Excel ingestion and validation for the microplan facility sheet, with schema-driven configuration, cascading dropdowns, and rich formatting.

---

## 2️⃣ Overview

* Generates **multi-sheet Excel templates** directly from MDMS JSON schema.
* **Campaign Configuration Sheet**: First sheet with editable green-highlighted cells for campaign setup.
* **Facility & User Sheets**: Schema-driven data entry with boundary dropdowns.
* **Boundary Hierarchy Sheet**: Visual boundary relationships.
* Supports localization, color coding, cascading dropdowns, multi-select values.
* Uses **hidden helper sheets** for dropdown data and validation logic.

---

## 3️⃣ Key Features

### **1. Schema-Driven Columns**

* Reads `stringProperties`, `numberProperties`, `enumProperties` from MDMS.
* Applies **color**, **order**, **required flags**, **hide/freeze settings**.

### **2. Header Structure**

* **Row 1 (hidden):** Technical column names.
* **Row 2 (visible):** Localized names with color formatting.

### **3. Dropdowns**

* **Enum Dropdown:** Static list of values.
* **Cascading Dropdown:** Dependent on the previous column’s selection.
* **Multi-Select:** Multiple visible columns + hidden concatenated column for backend.

### **4. Hidden Mapping Sheets**

* Store **parent → child** boundaries.
* Store **level → boundary lists**.
* Maintain **localization mappings**.

### **5. Cell Locking & Freezing**

* Cells are **unlocked by default**.
* Freeze columns when `freezeColumnIfFilled = true`.

### **6. Campaign Configuration**

* **First sheet** with structured sections for campaign setup.
* **Green-highlighted editable cells** for boundary names and campaign details.
* **Protected layout** with only designated cells editable.

### **7. Sheet Protection**

* Password-protected after setup.
* Only unlocked cells are editable.

---

## 4️⃣ Workflow

1. **Fetch** campaign config & schemas from MDMS.
2. **Create** campaign configuration sheet (first sheet).
3. **Generate** facility & user sheets with schema-driven columns.
4. **Add** boundary hierarchy sheet with visual relationships.
5. **Apply** dropdowns (enum/cascading), multi-select formulas.
6. **Lock/Freeze** cells as per rules, protect sheets.
7. **Set** campaign config as active sheet and deliver.

---

## 5️⃣ Service Stats

The `excel-ingestion` service currently has:

* **Files**:

  * 34 Java files (main source code)
  * Updated with new campaign configuration utilities

* **Code Volume**:

  * \~3,151 lines of Java code
  * Represents a complete microservice with:

    * Controllers (web layer)
    * Services (boundary, file store, localization, excel generation)
    * Utilities (boundary hierarchy, schema creation, campaign config, request conversion)
    * Models (boundary, excel, localization)
    * Processors (microplan with campaign configuration support)
    * Configuration classes
  * **Proper Spring Boot structure** with layered architecture and **good refactored code**.

* **Code Authoring Process**:

  * All core code was **written by the Claude CLI coding agent**.
  * Some parts of the **design required manual refactoring** to align with project requirements.
  * During the refactoring phase, I provided **specific refactor instructions**, and Claude CLI performed the actual code rewriting.

This reflects a **substantial and well-structured microservice**, with over **3K lines** of good refactored Java code including the new campaign configuration functionality.

---

## 6️⃣ Usage Cautions

⚠ **Best Practices When Using Claude Max for Refactoring**

* **Review all suggestions** carefully — some changes may be inaccurate.
* **Test all working cases** before committing.
* **Commit small, functional changes** instead of large refactors.
* After each refactor, **validate all use cases** again and then commit.

---

## 7️⃣ Recommended Practice for Ongoing Improvements

Maintain a **dedicated `.md` file** to log changes and architectural decisions.
Updating it regularly ensures Claude Max (and future developers) always have **accurate project context**, enabling **better and more relevant refactoring suggestions**.

---

## 8️⃣ Side Issues Fixed via Claude CLI

### **1. YAML Duplicate Code Detection**
* **Issue**: Duplicate configurations in service YAML files (visible in logs but easy to miss).
* **Solution**: Copy-pasted logs into Claude CLI within cloned DevOps config repo.
* **Result**: Claude automatically detected the service → identified YAML → found duplicate code → fixed it.

### **2. Kafka Message Size Optimization in Project Service**
* **Issue**: Message size increases dramatically when updating project dates.
* **Investigation**: Asked Claude to check Kafka persistence in project-service for date change behavior.
* **Finding**: Claude discovered that date updates were cascading to all child entities, sending entire bulk project arrays as single Kafka messages.
* **Solution**: Implemented batch processing with batches of 100 projects.
* **Result**: Reduced Kafka message size and improved system stability.
