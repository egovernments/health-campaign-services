# Generation Flow Documentation - Project Factory

## Overview

This document explains the three generation approaches in project-factory service and their main differences:

1. **V1 Generation** - Basic generation (Old approach)
2. **V2 Generation** - Class-based generation with hardcoded configs
3. **Excel Ingestion** - MDMS-based generation with advanced features

---

## 🔵 V1 Generation (DataManagement Controller)

### **What it is:**
- Original/legacy approach for generating Excel templates
- Simple direct generation without much configuration

### **Endpoints:**
```
POST /v1/data/_generate    // Generate Excel
POST /v1/data/_download    // Download generated Excel
```

### **Key Features:**
- ✅ Basic Excel generation
- ✅ Simple validation
- ✅ Direct processing without configuration files
- ❌ No class-based generation
- ❌ Limited customization options

### **How it works:**
```
User Request → Validate → Generate Excel → Return File
```

---

## 🟢 V2 Generation (SheetManagement Controller)

### **What it is:**
- Modern approach using **class-based generation**
- Each sheet type has its own generation class
- Configuration is **hardcoded in the code**

### **Endpoints:**
```
POST /v2/data/_generate     // Generate Excel templates
POST /v2/data/_process      // Process uploaded Excel
```

### **Main Difference from V1:**
- **Class-Based Generation**: Each sheet type (User, Facility, Boundary) has its own Java class
- **Hardcoded Configs**: Generation templates are defined in code files like:
  - `generationtTemplateConfigs.ts` 
  - `processTemplateConfigs.ts`
- **Multiple Sheets Support**: Each type can have multiple sheets (README, USER_LIST, BOUNDARY_DATA, etc.)

### **Example Config (Hardcoded):**
```javascript
// generationtTemplateConfigs.ts
export const generationtTemplateConfigs: any = {
  user: {
    sheets: [
      {
        sheetName: "HCM_README_SHEETNAME",
        schemaName: "user-readme",
        lockWholeSheet: true
      },
      {
        sheetName: "HCM_ADMIN_CONSOLE_USER_LIST",
        schemaName: "user"
      },
      {
        sheetName: "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
        schemaName: "boundary-data",
        lockWholeSheet: false
      }
    ]
  },
  facility: {
    sheets: [...]
  }
}
```

### **How it works:**
```
User Request → Load Hardcoded Config → Call Generation Class → Generate Excel → Return File
```

### **Key Features:**
- ✅ **File class-based generation** - Each type has its own class
- ✅ Template-based approach
- ✅ Can have multiple sheets per type (user, facility, etc.)
- ✅ Better validation
- ❌ Configs are hardcoded in code
- ❌ Need code deployment for config changes

---

## 🔴 Excel Ingestion Service (New Approach)

### **What it is:**
- Latest approach with advanced features
- Configuration comes from **MDMS** (not hardcoded)
- Supports multiple sheets in one Excel file

### **Main Differences from V2:**

1. **MDMS Configuration** (Not Hardcoded):
   - Configs are stored in MDMS, not in code
   - Can change configs without code deployment
   - Dynamic configuration loading

2. **Cascading Boundary Dropdowns**:
   - Smart dropdowns that change based on parent selection
   - Example: Select State → District dropdown shows only that state's districts
   - Hierarchical data validation

3. **All-in-One Unified Excel**:
   - Generates all sheets in a single Excel file
   - Example: One Excel file containing Users, Facilities, and Boundaries sheets together
   - Better for bulk operations and data consistency

### **MDMS Config Example:**
```json
{
  "excelIngestionGenerate": {
    "microplan-ingestion": {
      "sheets": [
        {
          "sheetName": "HCM_ADMIN_CONSOLE_USER_LIST",
          "schemaName": "user-microplan-ingestion",
          "generationClass": "UserSheetGenerator"
        },
        {
          "sheetName": "HCM_ADMIN_CONSOLE_FACILITIES_LIST",
          "schemaName": "facility-microplan-ingestion",
          "generationClass": "FacilitySheetGenerator"
        },
        {
          "sheetName": "HCM_ADMIN_CONSOLE_BOUNDARY_HIERARCHY",
          "schemaName": "boundary-microplan-ingestion",
          "generationClass": "BoundarySheetGenerator"
        }
      ]
    }
  }
}
```

### **How it works:**
```
User Request → Fetch MDMS Config → Generate Multiple Sheets → Add Cascading Dropdowns → Return Excel
```

### **Key Features:**
- ✅ **MDMS-based configs** - No hardcoding
- ✅ **Cascading boundary dropdowns** - Smart hierarchical selection
- ✅ **All-in-One Unified Excel** - All sheets in single file
- ✅ Advanced validations
- ✅ Dynamic schema loading
- ✅ Can change configs without deployment

---

## 📊 Quick Comparison

| Feature | V1 | V2 | Excel Ingestion |
|---------|----|----|-----------------|
| **Generation Type** | Simple | Class-based | Class-based + MDMS |
| **Configuration** | None | Hardcoded in code | MDMS (Dynamic) |
| **All-in-One Unified Excel** | ❌ No | ❌ No | ✅ Yes (All sheets in single file) |
| **Cascading Dropdowns** | ❌ No | ❌ No | ✅ Yes |
| **Config Changes** | Code change | Code change | MDMS update (No deployment) |
| **Validation** | Basic | Good | Advanced |
| **Flexibility** | Low | Medium | High |

---

## 🎯 What to Use Going Forward?

### **✅ Use Excel Ingestion (ONLY RECOMMENDED APPROACH)**
- **For ALL new implementations**
- **For ALL future development** 
- Complete solution with all features
- MDMS-based configuration
- Cascading boundary dropdowns
- All sheets in one unified Excel file
- No deployment needed for config changes

### **❌ V1 & V2 are Legacy**
- V1 and V2 exist only for historical reasons
- Do NOT use V1 or V2 for any new work
- Excel Ingestion replaces both V1 and V2

---

## 💡 Key Takeaways

1. **V1 → V2 Evolution**: Added class-based generation but configs still hardcoded
2. **V2 → Excel Ingestion Evolution**: 
   - Moved configs from code to MDMS
   - Added cascading dropdowns
   - Support for multiple sheets
   - No deployment needed for config changes

3. **Main Benefits of Excel Ingestion**:
   - **Dynamic**: Change configs without touching code
   - **Smart**: Cascading dropdowns for better UX
   - **Unified**: All sheets in one Excel file
   - **Flexible**: MDMS-based configuration

**Excel Ingestion is the ONLY approach to use going forward!**