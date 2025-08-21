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

### **1. Schema-Driven Architecture**
* Reads `stringProperties`, `numberProperties`, `enumProperties` from MDMS
* **Dynamic headers** with localized names and color formatting
* **Cascading boundary dropdowns** with parent-child relationships
* **Multi-select support** with hidden concatenated columns

### **2. Boundaries & Validation**
* **Dynamic population** from boundaries array with `includeAllChildren` expansion
* **Last-level filtering** for leaf-level boundaries only
* **Hidden mapping sheets** for dropdown data and parent-child relationships
* **Level consistency** using `HCM_CAMP_CONF_LEVEL_*` keys

### **3. Excel Features**
* **Cell locking & freezing** with schema-driven protection
* **Campaign configuration** with green-highlighted editable cells
* **Password protection** after setup
* **Color-coded validation** and error handling

---

## 4️⃣ Current Service Architecture

### **Statistics**
* **66 Java files** | **7,212 lines of code** | **113 error constants**
* Spring Boot 3.2.2 with Java 17

### **Architecture Patterns**
* **Strategy Pattern**: 3 generation strategies (Schema-based, Excel Populator, Direct)
* **Configuration Registry**: Dynamic processor-to-sheet mapping
* **Chain of Responsibility**: Multi-level validation pipeline
* **Caching**: Caffeine (1-hour TTL) for boundaries/localization

### **Processing Workflow**
* **Generation**: Type resolution → Schema validation → Multi-sheet generation → Localization → Protection → Upload
* **Processing**: Download → Pre-validation → Row validation → Error annotation → Re-upload


---

## 5️⃣ Claude's Limitations & How to Work Around Them

### **Known Limitations**
* **Context Blindness**: May miss related code in other files/packages without explicit direction
* **Pattern Assumptions**: Sometimes applies patterns from other frameworks incorrectly
* **Partial Fixes**: May fix one occurrence but miss duplicate logic elsewhere (e.g., v1/v2 endpoints)
* **Over-Engineering**: Can suggest complex solutions for simple problems

### **Best Practices to Avoid Issues**
* **Provide Full Context**: Always mention related files, API versions, and dependencies
* **Incremental Changes**: Make small changes and test before proceeding
* **Explicit Instructions**: Specify exact locations (e.g., "check both v1 and v2 endpoints")
* **Verify Suggestions**: Review generated code always
* **Test Immediately**: Test after each change to catch issues early
* **Commit Working Code**: Save progress frequently with small, functional commits

---

## 6️⃣ Claude's Advanced Capabilities Demonstrated

* **Complex Code Generation**: 7,212+ lines across 66 Java files
* **Architecture Design**: Clean layered architecture with proper patterns
* **Problem-Solving**: MDMS integration, comprehensive validation, service orchestration
* **Context Maintenance**: Across multiple sessions with consistent patterns
* **Error Analysis**: Debugging based on compilation/runtime errors

---

## 7️⃣ Side Issues Fixed via Claude CLI

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

### **3. Project Service Search API Reference ID Bug**
* **Issue**: Project search API throwing "pass any field to search" error even when referenceId was provided in search body.
* **Investigation**: Opened Claude CLI in projects folder and described the issue.
* **Finding**: Claude found the validation logic was only checking for null values with AND operator, needed to include referenceId check.
* **First Fix**: Changed validation in one place (v2 search endpoint) - Claude confirmed it would work.
* **Problem**: Fix didn't work because the validation existed in two places (v1 and v2 search endpoints).
* **Final Solution**: Had to tell Claude to add the same fix in v1 search endpoint as well.
* **Learning**: Always check for multiple occurrences of similar validation logic across different API versions.
