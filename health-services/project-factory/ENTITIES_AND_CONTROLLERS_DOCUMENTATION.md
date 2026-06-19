# Entities and Controllers Documentation

## Overview

Based on DIGIT Health documentation, this explains the core entities (Campaign, Project, Facility, Employee, Mapping) and their controllers in Project Factory and Excel Ingestion services.

---

## 🏗️ Core Entities (DIGIT Architecture)

### **1. Campaign**
**Definition**: Digital, scalable solution for public health campaign execution

- **What it is**: High-level campaign configuration for disease control
- **Purpose**: 
  - Set up multiple campaigns simultaneously
  - Configure campaigns across different disease types
  - Track campaign progress and execution
- **Examples**: 
  - Malaria bednet distribution campaign
  - Vaccination drive campaign  
  - Dengue prevention campaign
- **Key Features**:
  - Supports single-round and multi-round campaigns
  - Allows reuse of assets across campaigns
  - Configurable for different diseases

### **2. Project**
**Definition**: Bridge between high-level campaign workflows and underlying entity registries

- **What it is**: Orchestrates logic around managing projects within campaigns
- **Purpose**: 
  - Connect campaign goals to actual execution
  - Manage project-specific workflows
  - Track project resources and staff
- **Key Entities Managed**:
  - **Projects** - Main project containers
  - **Project Tasks** - Specific tasks within projects
  - **Project Staff** - People assigned to projects  
  - **Project Beneficiaries** - Target population
  - **Project Facilities** - Locations used by projects
  - **Project Resources** - Materials and supplies

### **3. Facility**
**Definition**: Physical locations and infrastructure for campaign execution

- **What it is**: Facility Registry manages facility-related data within HCM system
- **Purpose**:
  - Track physical locations for campaign activities
  - Manage facility capacity and resources
  - Support location-based operations
- **Key Operations**:
  - Create, update, delete, search facilities
  - Bulk facility operations
  - Facility-project associations
- **Examples**:
  - Health centers for vaccination
  - Storage warehouses for supplies
  - Distribution points for materials

### **4. Employee/Staff**
**Definition**: Human resources involved in project execution

- **What it is**: Project Staff entities managed within Project Service
- **Purpose**:
  - Assign staff to specific projects
  - Track staff roles and responsibilities
  - Manage staff availability and skills
- **Key Aspects**:
  - Staff-project assignments
  - Role-based access and permissions
  - Staff performance tracking

### **5. Mapping/Relationships**
**Definition**: Connections between different entities in the system

- **What it is**: Relationships managed through Project Service orchestration
- **Types**:
  - **Project ↔ Staff**: Who works on which project
  - **Project ↔ Facility**: Which facilities serve which projects
  - **Project ↔ Beneficiaries**: Who is targeted by which project
  - **Project ↔ Resources**: What resources are allocated to projects

---

## 🏛️ Entity Hierarchy (DIGIT Model)

```
📋 CAMPAIGN (High-level configuration)
│
├── 📍 PROJECT 1 (Execution unit)
│   ├── 👥 Project Staff (Assigned people)
│   ├── 🏢 Project Facilities (Assigned locations)
│   ├── 🎯 Project Beneficiaries (Target population)
│   ├── 📦 Project Resources (Allocated supplies)
│   └── ✅ Project Tasks (Specific activities)
│
├── 📍 PROJECT 2 (Another execution unit)
│   └── ... (Same structure)
│
└── 🔄 Campaign-level coordination and tracking
```



### **Example: Malaria Campaign in Nigeria**:

```
🦟 MALARIA CONTROL CAMPAIGN
├── Campaign Config: Multi-state, bednet distribution
├── Asset Reuse: Training materials, protocols
│
├── 📍 LAGOS STATE PROJECT
│   ├── 👥 Project Staff: 50 health workers, 5 supervisors
│   ├── 🏢 Project Facilities: 10 health centers, 3 warehouses
│   ├── 🎯 Project Beneficiaries: 2M households in Lagos
│   ├── 📦 Project Resources: 2.5M bednets, vehicles
│   └── ✅ Project Tasks: Distribution, education, monitoring
│
├── 📍 KANO STATE PROJECT  
│   └── ... (Similar structure for Kano)
│
└── 🔄 Campaign Coordination:
    ├── Progress tracking across all projects
    ├── Resource optimization
    └── Performance monitoring
```


---

## 🔵 Project Factory Controllers

### **Campaign Management Controller**
**Purpose**: Manages high-level campaign configuration and lifecycle

**Path**: `/v1/project-type`

**Core Functions**:
```
POST /v1/project-type/create              // Create new campaign
POST /v1/project-type/update              // Update campaign configuration  
POST /v1/project-type/search              // Search campaigns
POST /v1/project-type/status              // Track campaign progress
POST /v1/project-type/cancel-campaign     // Cancel campaign execution
POST /v1/project-type/fetch-from-microplan // Microplan integration
```

**What it manages**:
- Campaign creation and configuration
- Multi-disease campaign setup
- Campaign status and progress tracking
- Asset reuse across campaigns

### **Data Management Controller (V1)**
**Purpose**: Handles entity data operations (Legacy)

**Path**: `/v1/data`

**Core Functions**:
```
POST /v1/data/_generate       // Generate Excel templates
POST /v1/data/_create         // Create entity records
POST /v1/data/_search         // Search entity data
POST /v1/data/campaign/_search // Search campaign-specific data
POST /v1/data/mapping/_search  // Search relationship data
```

### **Sheet Management Controller (V2)**
**Purpose**: Template-based entity management (Legacy)

**Path**: `/v2/data`

**Core Functions**:
```
POST /v2/data/_generate       // Generate Excel templates
POST /v2/data/_process        // Process uploaded data (with strict validation)
POST /v2/data/_process-any    // Process uploaded data (relaxed validation)
```

**Difference between _process vs _process-any**:
- **`_process`**: Strict validation - validates entity type and enforces all business rules
- **`_process-any`**: Relaxed validation - processes any entity type without strict type checking

---

## 🔴 Excel Ingestion Service Controllers

### **Ingestion Controller**
**Purpose**: MDMS-driven, type-based entity processing (Recommended)

**Path**: `/v1/data`

**Core Functions**:
```
POST /v1/data/_generate          // Generate Excel based on type (user/facility/boundary)
POST /v1/data/_process           // Process uploaded Excel files
POST /v1/data/_generationSearch  // Search generation status/results
POST /v1/data/_processSearch     // Search processing status/results
POST /v1/data/sheet/_search      // Search sheet data records
POST /v1/data/sheet/_delete      // Delete sheet data records
```

**Key Features**:
- ✅ MDMS-based configuration (no hardcoded configs)
- ✅ Type-based Excel generation via query parameter
- ✅ Cascading boundary dropdowns
- ✅ Async processing with status tracking
- ✅ Sheet data management
- ✅ Campaign-aware entity relationships

