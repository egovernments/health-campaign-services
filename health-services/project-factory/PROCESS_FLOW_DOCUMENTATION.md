# Process Flow Documentation - Project Factory

## Simple Overview

This document explains how the three different ways of processing Excel files work in our system. Each method has its own way of handling files.

## 1. V1 Data Management (`/v1/data/_create`)

### How it Works
In V1, you have two main things:
- **TYPE**: What kind of data you're uploading (user, facility, boundary)
- **ACTION**: What you want to do with it (create, validate)

### Simple Flow
1. You send Excel file with TYPE and ACTION
2. System checks ACTION:
   - If ACTION = "validate" → Only check if data is correct
   - If ACTION = "create" → Check data and then create records
3. Based on TYPE, it processes that specific sheet (user sheet, facility sheet, etc.)

### Example
```
Request: 
- type = "user"
- action = "create"
Result: System will validate user data and create users

Request:
- type = "facility" 
- action = "validate"
Result: System will only validate facility data, won't create anything
```

### V1 Configuration
```javascript
// V1: Uses createAndSearchConfig from createAndSearch.ts
const createAndSearchConfig = createAndSearch[type];

// Example for facility type:
createAndSearch.facility = {
    uniqueIdentifier: "id",
    uniqueIdentifierColumn: "A", 
    activeColumn: "G",
    sheetSchema: {
        "type": "object",
        "properties": {
            "Facility Name": { "type": "string" },
            "Facility Type": { "enum": ["Warehouse", "Health Facility"] }
        }
    },
    createDetails: { url: "/facility/v1/_create" },
    searchDetails: { url: "/facility/v1/_search" }
}

// V1 also uses schemas from MDMS for validation (in some flows)
// Note: V1 has mixed usage - some parts use callMdmsTypeSchema with adminSchema
```

## 2. V2 Sheet Management (`/v2/data/_process`)

### What Changed from V1
- **No more ACTION parameter** - Everything is decided by TYPE only
- TYPE now controls both validation and creation

### How it Works
1. You only send TYPE (no ACTION needed)
2. Based on TYPE, system loads a specific process file/class
3. That process class has all the logic:
   - What to validate
   - How to transform data
   - Whether to create or just validate
   - What rules to apply

### Simple Flow
1. Send Excel with TYPE only
2. System finds process class for that TYPE
3. Process class does everything automatically

### Example (Actual Code)
```javascript
// In sheetManageService.ts line 25
const processTemplateConfig = JSON.parse(JSON.stringify(processTemplateConfigs?.[String(ResourceDetails.type)]));

// If type = "user", it loads:
processTemplateConfigs.user = {
    sheets: [
        { sheetName: "HCM_README_SHEETNAME", lockWholeSheet: true },
        { sheetName: "HCM_ADMIN_CONSOLE_USER_LIST", validateRowsGap: true, schemaName: "user" },
        { sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA", lockWholeSheet: true }
    ]
}

// If type = "facility", it loads:
processTemplateConfigs.facility = {
    sheets: [
        { sheetName: "HCM_README_SHEETNAME", lockWholeSheet: true },
        { sheetName: "HCM_ADMIN_CONSOLE_FACILITIES", validateRowsGap: true, schemaName: "facility" },
        { sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA", lockWholeSheet: true }
    ]
}
```

**Key Function**: `processTemplateConfigs[type]` - This is how TYPE decides which configuration to load.

### V2 Schema Integration
```javascript
// V2 uses schemas from MDMS for validation
const schema = await callMdmsSchema(tenantId, sheet?.schemaName); // "user", "facility", "boundary" etc.
// This calls MDMS with:
// - schemaCode: "HCM-ADMIN-CONSOLE.schemas" 
// - uniqueIdentifier: "user" or "facility" or "boundary" (based on sheet type)
// Note: All types use the same schemaCode but different identifiers
```

### Actual Process Class Loading Code
```javascript
// In sheetManageUtils.ts line 209-217
const className = `${ResourceDetails?.type}-processClass`;
let classFilePath = path.join(__dirname, '..', 'processFlowClasses', `${className}.js`);
if (!fs.existsSync(classFilePath)) {
    // fallback for local dev with ts-node
    classFilePath = path.join(__dirname, '..', 'processFlowClasses', `${className}.ts`);
}
const { TemplateClass } = await import(classFilePath);
const sheetMap: SheetMap = await TemplateClass.process(ResourceDetails, wholeSheetData, localizationMap, templateConfig);
```

**This is the exact code that decides which class file to load!**

Examples:
- If `type = "user"` → loads `user-processClass.ts`
- If `type = "facility"` → loads `facility-processClass.ts`  
- If `type = "boundary"` → loads `boundary-processClass.ts`
- If `type = "userValidation"` → loads `userValidation-processClass.ts`

Each class file has a `TemplateClass` with a `process()` method that contains all the processing logic for that type.

## 3. Excel Ingestion Service (Java Service)

### What's Different
- This is a **separate Java service** (V1 and V2 are in Node.js)
- All configuration is stored in **MDMS** (not in code)
- Uses **class-based processing**

### How it Works
1. **Configuration in MDMS**:
   - MDMS has complete process configuration
   - Configuration says which sheet type uses which Java class
   - Example: "user" sheet → UserIngestionClass
   - Example: "facility" sheet → FacilityIngestionClass

2. **Class-based Processing**:
   - Each sheet type has its own Java processor class
   - Class is picked based on configuration
   - Class contains all logic for that sheet type

### Simple Flow
1. Excel file comes in
2. System checks MDMS config to find which class to use
3. For each sheet:
   - If `parseEnabled = true` → Insert rows into `eg_ex_in_sheet_data_temp` table
   - If `parseEnabled = false` → Only validate, no database insertion
4. Send processing results to `processingResultTopic` via Kafka
5. Other services can consume results from Kafka for further processing

### Configuration Example (in MDMS)
```json
// In Excel Ingestion Java Service
// MDMS stores complete processor configuration
// Example MDMS configuration structure:
{
    "tenantId": "pb",
    "moduleDetails": [{
        "moduleName": "HCM-ADMIN-CONSOLE",
        "masterDetails": [{
            "name": "excelIngestionProcess",
            "data": {
                "microplan-ingestion": {
                    "processingResultTopic": "hcm-microplan-processing-result",
                    "sheets": [
                        {
                            "sheetName": "HCM_ADMIN_CONSOLE_USER_LIST",
                            "processorClass": "UserProcessor",
                            "parseEnabled": true  // Controls data parsing/processing
                        },
                        {
                            "sheetName": "HCM_ADMIN_CONSOLE_FACILITIES",
                            "processorClass": "FacilityProcessor", 
                            "parseEnabled": false  // Skip data processing, only validate
                        }
                    ]
                }
            }
        }]
    }]
}
```

### Key Configuration Properties:

#### **parseEnabled**
- **true**: Parse data row by row and insert into temp table (default)
- **false**: Only validate sheet structure, no database insertion
- **Parse means**: Insert Excel rows into `eg_ex_in_sheet_data_temp` table

#### **processingResultTopic**
- Kafka topic where processing results are published
- Used for async communication with other services
- Contains success/failure status and processed data details

### Java Implementation:
- Reads MDMS config to get processor class name
- Then process the sheet data according to  processor class
- If `parseEnabled = true` → Insert rows to `eg_ex_in_sheet_data_temp` table
- If `parseEnabled = false` → Process but no DB insertion
- Send results to configured Kafka topic

**Key Difference**: Excel Ingestion is a Java service that reads processor class names from MDMS configuration, not from code files.

## Main Differences Summary

### V1 (Old Way)
- Uses TYPE + ACTION
- ACTION decides validate or create
- Simple and direct
- Code decides what to do
- Uses **createAndSearchConfig** for configuration
- Uses **adminSchema** from MDMS for validation
- **Key Code**: Direct action-based processing
```javascript
// V1: Uses both type and action + createAndSearchConfig
if (action === "validate") {
    // only validate
} else if (action === "create") {
    // validate and create
}

// V1 Configuration loading
const createAndSearchConfig = createAndSearch[type]; // From createAndSearch.ts
// V1 uses callMdmsTypeSchema which uses "HCM-ADMIN-CONSOLE.adminSchema"
```

### V2 (New Way)
- Only uses TYPE (no ACTION)
- TYPE loads a complete process configuration
- Configuration has all logic
- More automated
- Uses **processTemplateConfigs** for configuration
- Uses **schemas** from MDMS for validation (HCM-ADMIN-CONSOLE.schemas)
- **Key Code**: `processTemplateConfigs[ResourceDetails.type]`
- **Dynamic Class Loading**: `${type}-processClass.ts` files
```javascript
// V2: Configuration from code files + Dynamic class loading
const config = processTemplateConfigs[type]; // Configuration
const className = `${type}-processClass`;    // Class file
const { TemplateClass } = await import(classFilePath);
const schema = await callMdmsSchema(tenantId, sheet?.schemaName); // uses HCM-ADMIN-CONSOLE.schemas
```

### Excel Ingestion (Java)
- Separate Java service (not Node.js)
- Configuration in MDMS (not in code)
- MDMS tells which Java class processes which sheet type
- Most flexible - can change behavior without code changes
- Uses **processor class names** from MDMS
- **Key Code**: Java reflection to load processor classes
```java
// Excel Ingestion: Java service reads class names from MDMS
String processorClass = mdmsConfig.get(type).get("processorClass");
Class<?> clazz = Class.forName(processorClass);
Processor processor = (Processor) clazz.newInstance();
processor.process(excelData);
```

## Schema Usage Summary

### How Each Version Uses Schemas

1. **V1 (Node.js)**: 
   - Uses `createAndSearchConfig` from code files
   - Fetches `HCM-ADMIN-CONSOLE.adminSchema` from MDMS (via callMdmsTypeSchema)

2. **V2 (Node.js)**: 
   - Uses `processTemplateConfigs` from code files
   - Fetches `HCM-ADMIN-CONSOLE.schemas` from MDMS (via callMdmsSchema)
   
3. **Excel Ingestion (Java)**: 
   - Fetches `HCM-ADMIN-CONSOLE.schemas` from MDMS
   - Reads processor class names from MDMS configuration
   - Uses Java reflection to load classes dynamically
   - All configuration is in MDMS, no hardcoded configs

The key difference: 
- V1 uses `HCM-ADMIN-CONSOLE.adminSchema` (via callMdmsTypeSchema function)
- V2 and Excel Ingestion use `HCM-ADMIN-CONSOLE.schemas` (via callMdmsSchema in V2)