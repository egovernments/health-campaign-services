# Unified Microplan Workflow Design

## Current vs New Approach

| **Current** | **New Proposed** |
|-------------|------------------|
| Generate 3 separate sheets | Generate 1 combined microplan sheet |
| Process each sheet individually | Process all data together |

## New Workflow Architecture

### Step 1: Single Template Generation
**One Comprehensive Template containing:**
- 🏥 **Facility Data Sheet**
- 👥 **User Data Sheet** 
- 🎯 **Target Data Sheet**

### Step 2: Unified Validation & Processing
- **Process API:** Single call validates entire sheet
- **Upload:** All 3 data types in one submission
- **Validation:** Combined validation for all sections

### Step 3: Campaign Creation
- **Campaign Create API:** Store all validated data
- **Single Campaign:** All data types under one campaign
- **Status Tracking:** Monitor processing progress

### Step 4: Sequential Processing Pipeline (7 Operations)

```
1. 🏥 Facility Create
2. 👥 User Create  
3. 🎯 Target/Project Create
4. 🔗 Facility Mapping
5. 🔗 User Mapping
6. 🔗 Resource Mapping
7. 🔑 User Credential Generation
```

## Complete Flow Diagram

```mermaid
flowchart TD
    A[📄 Single Microplan Template<br/>Facility + User + Target] --> B[📤 Upload Combined Sheet]
    B --> C[✅ Process API<br/>Validate All Data Together]
    C --> D{Validation<br/>Success?}
    D -->|❌ No| E[❌ Return Validation Errors]
    D -->|✅ Yes| F[💾 Campaign Create API<br/>Store All Data]
    F --> G[🔄 Start Sequential Processing]
    G --> H[1️⃣ Facility Create]
    H --> I[2️⃣ User Create]
    I --> J[3️⃣ Target/Project Create]
    J --> K[4️⃣ Facility Mapping]
    K --> L[5️⃣ User Mapping]
    L --> M[6️⃣ Resource Mapping]
    M --> N[7️⃣ User Credential Generation]
    N --> O[✅ Complete Microplan Campaign]
    
    style A fill:#e1f5fe
    style C fill:#fff3e0
    style F fill:#f3e5f5
    style O fill:#e8f5e8
```

## Key Benefits

| **Benefit** | **Description** |
|-------------|-----------------|
| **Simplified UX** | Users upload one sheet instead of three separate sheets |
| **Atomic Operation** | All data validated together, reducing inconsistencies |
| **Better Error Handling** | Single validation point for all related data |
| **Streamlined Process** | One unified workflow instead of three parallel ones |
| **Data Consistency** | Cross-section validation (facility-user relationships) |
| **Reduced Complexity** | Fewer API calls and fewer potential failure points |

## Implementation Requirements

### 1. Sheet Generator Updates
- **Combined Template:** Generate unified microplan sheet
- **Section Headers:** Clear separation of facility/user/target sections
- **Cross-references:** Maintain relationships between sections

### 2. Process API Enhancements
- **Multi-type Parsing:** Handle facility + user + target data in single call
- **Cross-section Validation:** Validate relationships across data types
- **Unified Error Reporting:** Consolidated error feedback

### 3. Campaign API Modifications
- **Temporary Storage:** Store multi-type campaign data temporarily
- **Data Structure:** Support combined facility/user/target storage
- **Status Tracking:** Track processing progress across 7 operations

### 4. Sequential Processor
- **Pipeline Manager:** Execute 7 operations in correct order
- **Failure Recovery:** Handle partial failures and rollback
- **Progress Tracking:** Monitor and report processing status

## Technical Implementation

### API Flow Changes

#### Before (3 Separate Flows):
```typescript
// Facility Flow
generateFacilitySheet() -> processFacilityData() -> createFacilityCampaign()

// User Flow  
generateUserSheet() -> processUserData() -> createUserCampaign()

// Target Flow
generateTargetSheet() -> processTargetData() -> createTargetCampaign()
```

#### After (Unified Flow):
```typescript
// Unified Microplan Flow
generateMicroplanSheet() -> 
processMicroplanData() -> 
createCampaign() ->
executeSequentialProcessing([
  facilityCreate,
  userCreate, 
  targetCreate,
  facilityMapping,
  userMapping,
  resourceMapping,
  credentialGeneration
])
```

## Data Structure

```typescript
interface MicroplanData {
  facilities: FacilityData[];
  users: UserData[];
  targets: TargetData[];
  campaignDetails: CampaignInfo;
  relationships: {
    facilityUserMappings: Mapping[];
    facilityTargetMappings: Mapping[];
  };
}
```