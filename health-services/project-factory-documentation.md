# Project Factory - Campaign Creation Flow

## Main Flow

### 1. API Entry Point
```
POST /project-factory/v1/project-type/create
    ↓
createProjectTypeCampaignService()
    ↓
processBasedOnAction()
```

### 2. Campaign Creation Steps

#### Step 1: Initial Setup
- Generate Campaign ID (UUID)
- Generate Campaign Number: `CMP-[date]-[sequence]`
- Set Status: "draft" or "inprogress"

#### Step 2: Save to Database
- Persist via Kafka topic
- Enrich with audit details (createdBy, createdTime)

#### Step 3: Process by Type

**A. Unified Template Campaign (Excel-based)**
- Call excel-ingestion service
- Parse Excel data
- Auto-generate resources

**B. Regular Campaign (Manual)**
- Background async process:
  - Create resources (facility, user, boundary)
  - Create mappings
  - Generate user credentials
  - Send email notifications

## Kafka Background Listeners

### 1. Task Processing (`KAFKA_START_ADMIN_CONSOLE_TASK_TOPIC`)
Handles: Facility, User, Boundary creation from Excel
```
Process Types:
- facilityCreation → Create facilities from Excel
- userCreation → Create users from Excel  
- projectCreation → Create boundaries from Excel
```

### 2. Mapping Processing (`KAFKA_START_ADMIN_CONSOLE_MAPPING_TASK_TOPIC`)
Handles: Resource linking and mapping
```
Mapping Types:
- resourceMapping → Link products to boundaries
- facilityMapping → Map facilities to projects
- userMapping → Assign users to projects
```

## Status Flow
```
draft → inprogress → started → completed
         ↓
      failed (on error)
```

## Key Points

1. **Async Processing**: Heavy tasks run in background
2. **Error Handling**: Mark status as "failed" and save error details
3. **Retry Logic**: 4 attempts for boundary sync
4. **Non-blocking**: Main thread stays free, immediate response

## Data Management Flow

### Excel Upload → Validation → Processing
```
1. File Upload (via FileStore)
   ↓
2. Excel Validation
   - Sheet structure check
   - Column mapping validation
   - Data type validation
   ↓
3. Data Extraction
   - Read Excel sheets
   - Convert to JSON format
   - Apply business rules
   ↓
4. Data Processing
   - Validate against MDMS schemas
   - Enrich with additional data
   - Generate resources
```

### Boundary Data Consolidation
```
Process:
1. Extract boundary data from Excel
2. Create hierarchy mapping (District → Block → Village)
3. Generate boundary codes automatically
4. Consolidate with existing boundaries
5. Create project-boundary relationships
```

### Template-based Data Generation
```
Templates Available:
- Facility templates (by project type)
- User templates (by role/hierarchy)
- Resource templates (by campaign type)
- Mapping templates (boundary-specific)

Process:
1. Fetch template from MDMS
2. Apply template rules to data
3. Generate standardized output
4. Validate generated data
```

## Resource Creation Flows

### 1. Facility Creation Flow
```
Excel Data → Validation → Facility Service → Project Mapping

Steps:
1. Extract facility data from Excel
2. Validate facility details (name, type, location)
3. Call Facility Service API to create facilities
4. Map facilities to projects based on boundary
5. Update facility status and coordinates
6. Generate facility reports
```

### 2. User Creation Flow
```
Excel Data → User Service → Credential Generation → Email

Steps:
1. Extract user data (name, phone, role, boundary)
2. Validate user details and check duplicates
3. Call User Service API to create users
4. Generate random passwords
5. Encrypt credentials using crypto service
6. Send welcome email with credentials
7. Map users to projects with assigned roles
```

### 3. Project/Boundary Creation Flow
```
Excel Data → Project Service → Hierarchy Setup

Steps:
1. Extract boundary hierarchy from Excel
2. Validate boundary relationships
3. Create projects for each boundary level
4. Set up parent-child relationships
5. Generate boundary codes if missing
6. Map projects to campaign
7. Set project status and metadata
```

## Mapping Flows

### 1. Resource Mapping Flow
```
Products ↔ Boundaries

Process:
1. Extract product-boundary relationships
2. Validate product variants exist
3. Create resource mappings in Project Service
4. Set delivery rules per boundary
5. Calculate resource allocation
6. Generate mapping reports
```

### 2. Facility Mapping Flow
```
Facilities ↔ Projects

Process:
1. Get facility list from Facility Service
2. Identify target projects for mapping
3. Create facility-project relationships
4. Set facility roles and permissions
5. Update facility catchment areas
6. Generate facility coverage reports
```

### 3. User Mapping Flow
```
Users ↔ Projects (with Roles)

Process:
1. Get user list from User Service
2. Extract role assignments from Excel
3. Map users to projects with specific roles
4. Set user permissions per project
5. Create user-boundary access rules
6. Send role assignment notifications
```

## Key Integration Points

### Excel Processing Pipeline
```
FileStore → Excel-Ingestion → Project-Factory → Target Services

Flow:
1. Upload Excel to FileStore
2. Excel-Ingestion validates and parses
3. Project-Factory receives processed data
4. Calls target services (User/Facility/Project)
5. Creates mappings and relationships
6. Updates campaign status
```

### Error Handling in Flows
```
Validation Errors:
- Excel format issues
- Missing mandatory fields
- Invalid data types
- Duplicate entries

Processing Errors:
- Service API failures
- Mapping conflicts
- Resource creation failures
- Network timeouts

Recovery:
- Retry failed operations
- Skip invalid records
- Generate error reports
- Update process status
```

## Summary

Campaign creation simplified:
1. **Create** → Generate Campaign ID & number
2. **Save** → Persist to database via Kafka
3. **Process** → Check type (Excel/Manual)
4. **Data Management** → Excel validation & processing
5. **Resource Creation** → Facilities, Users, Projects
6. **Mapping** → Link resources with relationships
7. **Complete** → Status update & notifications

All flows work together to create complete campaign ecosystem from Excel data.