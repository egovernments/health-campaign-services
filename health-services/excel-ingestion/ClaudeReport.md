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
* **Boundary Hierarchy Sheet**: Visual boundary relationships with dynamic population.
* Supports localization, color coding, cascading dropdowns, multi-select values.
* Uses **hidden helper sheets** (`_h_SheetName_h_`) for dropdown data and validation logic.

---

## 3️⃣ Key Features

### **1. Schema-Driven Columns**

* Reads `stringProperties`, `numberProperties`, `enumProperties` from MDMS.
* Applies **color**, **order**, **required flags**, **hide/freeze settings**.

### **2. Header Structure**

* **Row 1 (hidden):** Technical column names.
* **Row 2 (visible):** Localized names with color formatting.

### **3. Dropdowns & Validation**

* **Enum Dropdown:** Static list of values.
* **Cascading Boundary Dropdown:** Level → Boundary → Parent relationships.
* **Multi-Select:** Multiple visible columns + hidden concatenated column for backend.

### **4. Boundaries Feature**

* **Dynamic Population**: Generates rows from boundaries array with `includeAllChildren` expansion.
* **Last-Level Filtering**: Shows only leaf-level boundaries (e.g., C1→P1→T1, C1→P1→T2).
* **Hidden Code Column**: Stores boundary codes for backend processing.

### **5. Hidden Mapping Sheets**

* Store **parent → child** boundaries using `_h_SheetName_h_` naming format.
* Store **level → boundary lists** with localized names.
* Maintain **localization mappings** and level consistency.

### **6. Localization & Consistency**

* **Level Consistency**: Uses `HCM_CAMP_CONF_LEVEL_*` keys across all sheets.
* **Fallback Strategy**: Returns original localization key when translation missing.
* **Mapping Sheets**: Hidden level mapping (`_h_LevelMapping_h_`) for dropdown consistency.

### **7. Cell Locking & Freezing**

* Cells are **unlocked by default**.
* Freeze columns when `freezeColumnIfFilled = true`.

### **8. Campaign Configuration**

* **First sheet** with structured sections for campaign setup.
* **Green-highlighted editable cells** for boundary names and campaign details.
* **Protected layout** with only designated cells editable.

### **9. Sheet Protection**

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

## 5️⃣ Current Service Architecture & Stats

### **Service Overview**
The `excel-ingestion` service is a sophisticated Spring Boot 3.2.2 microservice built with Java 17, designed to generate complex multi-sheet Excel templates for health campaign data management.

### **Current Statistics**
* **Total Files**: 42 Java files + configuration/documentation files
* **Code Volume**: ~4,100+ lines of Java code
* **Architecture**: Clean layered microservice with processor-based pattern

### **Component Breakdown**
* **7 Core Services**: Excel generation, boundary management, localization, MDMS integration, file store, API payload building
* **20+ Model Classes**: Including new `Boundary`, `FileStoreResponse`, `FileInfo` models with Jakarta validation
* **4 Utility Classes**: Specialized Excel sheet creators for different purposes  
* **2 Processor Implementations**: Microplan and hierarchy-based Excel generation
* **1 REST Controller**: Clean API interface with validation
* **8 Configuration/Exception Classes**: Robust error handling and configuration management

### **Key Architectural Patterns**
* **Factory Pattern**: Dynamic processor selection based on Excel type
* **Strategy Pattern**: Multiple Excel generation strategies via IGenerateProcessor
* **Builder Pattern**: Lombok-enhanced model construction
* **Caching Strategy**: Caffeine cache for external service data (1-hour expiry, 100 max entries)

### **Recent Major Enhancements (Latest Session)**
1. **Boundaries Feature Implementation**
   - Dynamic boundary hierarchy population from request payload
   - Last-level filtering with `includeAllChildren` expansion logic
   - Hidden boundary code column for backend integration

2. **Level Localization Standardization**
   - Unified `HCM_CAMP_CONF_LEVEL_*` usage across campaign config and dropdowns
   - Level mapping sheet (`_h_LevelMapping_h_`) for consistent key-value handling
   - Maintained dropdown functionality with proper Excel formulas

3. **Hidden Sheet Naming Standardization**
   - All hidden sheets use `_h_SheetName_h_` format consistently
   - Updated formula references across both processors
   - Improved maintainability and debugging

4. **FileStore Service Enhancement**
   - Proper model classes (`FileStoreResponse`, `FileInfo`) replacing raw Maps
   - Better JSON parsing and error handling
   - Type-safe response processing

5. **Model Cleanup & API Enhancement**
   - Removed redundant `locale` field from `GenerateResource` (using RequestInfo)
   - Enhanced validation with `Boundary` model class
   - Cleaner API contracts with consistent localization fallbacks

### **Previous Major Improvements (Claude-Driven)**
1. **Comprehensive Error Handling**: Three-tier exception handling system
   - CustomExceptionHandler for service-level errors with context
   - ValidationExceptionHandler for input validation with custom error codes  
   - GlobalExceptionHandler for standardized health services error responses

2. **Advanced Validation System**: Jakarta validation with custom error codes
   - GenerateResource model with @NotBlank, @Size annotations
   - Custom error codes like "INGEST_MISSING_TENANT_ID"
   - Cascade validation with @Valid annotations

3. **MDMS Integration Refactoring**: Generic utility function replacing duplicate code
   - Single mdmsSearch function with filters, limit, offset parameters
   - Eliminated code duplication across multiple service calls

4. **Service Quality Enhancements**:
   - Excel sheet protection with password locking
   - Color-coded headers and cell formatting
   - Cascading dropdown validations with boundary relationships
   - Multi-language localization support

### **Integration Capabilities** 
* **External Services**: Boundary-service, MDMS-service, Localization-service, File-store
* **Caching Layer**: Distributed caching for boundary hierarchy, localization messages
* **File Management**: Automated Excel upload to external file store with unique IDs

### **Code Quality Indicators**
* **Clean Architecture**: Proper separation of concerns with service/utility/model layers
* **Error Safety**: Comprehensive error constants and exception handling patterns
* **Type Safety**: Moving from Map<String, Object> to proper model classes (5-phase migration plan)
* **Logging**: SLF4J logging throughout with proper error tracking

This reflects a **mature, well-architected microservice** with sophisticated Excel generation capabilities and enterprise-grade error handling - all primarily **authored by Claude CLI** with targeted refactoring guidance.

---

## 6️⃣ Usage Cautions

⚠ **Best Practices When Using Claude Max for Refactoring**

* **Review all suggestions** carefully — some changes may be inaccurate.
* **Test all working cases** before committing.
* **Commit small, functional changes** instead of large refactors.
* After each refactor, **validate all use cases** again and then commit.
* **Multiple refactoring iterations may be needed** — Claude gets the logic mostly right and is always accurate, but refactoring-wise you may need to iterate a few more times to achieve the desired structure.
* **Monitor each change closely** — check what files Claude is editing and verify the changes before proceeding.
* **Refactor incrementally** — break large refactoring tasks into smaller, manageable chunks and validate each change.

---

## 7️⃣ Recommended Practice for Ongoing Improvements

Maintain a **dedicated `.md` file** to log changes and architectural decisions.
Updating it regularly ensures Claude Max (and future developers) always have **accurate project context**, enabling **better and more relevant refactoring suggestions**.

---

## 8️⃣ Claude's Advanced Capabilities Demonstrated

### **Complex Code Generation & Refactoring**
Claude successfully:
* **Generated 4,100+ lines** of production-quality Java code for a complete microservice
* **Implemented sophisticated Excel generation** with multi-sheet templates, cascading dropdowns, and data validation
* **Created comprehensive error handling** with three-tier exception management system
* **Designed clean architecture** following Spring Boot best practices with proper layering

### **Problem-Solving & System Integration**
* **MDMS Integration**: Created generic utility functions to eliminate code duplication
* **Validation Framework**: Implemented Jakarta validation with custom error codes and cascade validation
* **Caching Strategy**: Designed and implemented Caffeine-based caching for external service data
* **Service Integration**: Built seamless integration with 4 external microservices

### **Code Quality & Patterns**
* **Design Patterns**: Implemented Factory, Strategy, and Builder patterns appropriately
* **Error Safety**: Created robust error handling that never silently fails
* **Type Safety**: Planned and started 5-phase migration from Map<String, Object> to proper models
* **Documentation**: Maintained comprehensive documentation throughout development

### **Iterative Improvement Process**
Claude demonstrated ability to:
1. **Understand complex requirements** and translate them into working code
2. **Respond to feedback** and refactor code based on specific user guidance  
3. **Maintain context** across multiple sessions and large codebases
4. **Follow coding standards** and existing project patterns consistently
5. **Debug issues** and implement fixes based on error analysis

### **Technical Depth**
Successfully handled:
* **Spring Boot 3.2.2** with Java 17 and Jakarta validation migration
* **Apache POI** for advanced Excel manipulation with cell protection and formatting
* **Caffeine caching** with proper configuration and expiration policies
* **REST API design** with proper validation and error response formatting
* **Maven dependency management** and configuration

---

## 9️⃣ Side Issues Fixed via Claude CLI

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
