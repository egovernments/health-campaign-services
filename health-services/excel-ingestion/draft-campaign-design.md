# Unified Microplan Workflow Design

## 1. Excel Template Generation
**Type:** `microplan-template-generate`  
**API:** `POST /v1/data/_generate` (Async - returns generateResourceId)
**Download API:** `POST /v1/data/_download` (returns fileStoreId)

```mermaid
sequenceDiagram
    participant Client
    participant ExcelIngestionService
    participant Database
    participant boundary-service
    participant localization-service
    participant mdms-service
    participant filestore-service
    
    Client->>ExcelIngestionService: POST /v1/data/_generate<br/>(type: microplan-template-generate, referenceId (campaignId))
    
    ExcelIngestionService-->>Client: GenerateResourceResponse<br/>(with generateResourceId)
    
    Note over ExcelIngestionService: Background Processing Started
    
    ExcelIngestionService->>boundary-service: Fetch Boundary Hierarchy
    boundary-service-->>ExcelIngestionService: Boundary Data
    
    ExcelIngestionService->>localization-service: Fetch Localized Labels
    localization-service-->>ExcelIngestionService: Localized Messages
    
    ExcelIngestionService->>mdms-service: Fetch Master Data
    mdms-service-->>ExcelIngestionService: MDMS Configuration
    
    Note over ExcelIngestionService: Generate Excel Template
    Note over ExcelIngestionService: Sheet 1: ReadMe Instructions
    Note over ExcelIngestionService: Sheet 2: Facility Data
    Note over ExcelIngestionService: Sheet 3: User Data  
    Note over ExcelIngestionService: Sheet 4: Target Data
    
    ExcelIngestionService->>filestore-service: Upload Excel Template
    filestore-service-->>ExcelIngestionService: FileStore ID
    
    ExcelIngestionService->>Database: Save Template Record<br/>(generateResourceId, fileStoreId, status: COMPLETED)
    
    Note over ExcelIngestionService: Template Ready for Download
```

## 2. Validation Process
**Type:** `microplan-ingestion-validate`  
**API:** `POST /v1/data/_process` (Async - returns resourceId)
**Search API:** `POST /v1/data/_search` (returns process status & fileStoreId if complete)

```mermaid
sequenceDiagram
    participant Client
    participant ExcelIngestionService
    participant Database
    participant filestore-service
    participant mdms-service
    
    Client->>ExcelIngestionService: POST /v1/data/_process<br/>(type: microplan-ingestion-validate, referenceId (campaignId), fileStoreId)
    
    ExcelIngestionService-->>Client: ProcessResponse<br/>(with resourceId)
    
    Note over ExcelIngestionService: Background Processing Started
    
    ExcelIngestionService->>filestore-service: Download Excel File
    filestore-service-->>ExcelIngestionService: Excel File Data
    
    Note over ExcelIngestionService: Parse Excel Sheets
    Note over ExcelIngestionService: MDMS Schema Validation
    Note over ExcelIngestionService: Manual Validation
    Note over ExcelIngestionService: - User Existence Check
    Note over ExcelIngestionService: - Boundary Code Existence
    Note over ExcelIngestionService: - Boundary Code in Campaign Selected
    
    ExcelIngestionService->>filestore-service: Upload Processed File
    filestore-service-->>ExcelIngestionService: Processed FileStore ID
    
    ExcelIngestionService->>Database: Save Process Record<br/>(resourceId, fileStoreId, status: COMPLETED)
    
    Note over ExcelIngestionService: Validation Complete - Ready for Search
```

## 3. Data Storage & Campaign Creation
**Type:** `microplan-ingestion`  
**API:** `POST /v1/data/_process` (Async - returns processId)  
**Search API:** `POST /v1/data/_search` (returns process status & referenceId (campaignId) if complete)

```mermaid
sequenceDiagram
    participant Client
    participant ExcelIngestionService
    participant Database
    participant filestore-service

    Client->>ExcelIngestionService: POST /v1/data/_process<br/>(type: microplan-ingestion, referenceId (campaignId), fileStoreId)
    ExcelIngestionService-->>Client: ProcessResponse<br/>(with processId)
    Note over ExcelIngestionService: Background Processing Started

    ExcelIngestionService->>filestore-service: Download Excel File
    filestore-service-->>ExcelIngestionService: Excel File Data

    Note over ExcelIngestionService: Run Above Validation Flow

    alt Validation Failed
        Note over ExcelIngestionService: Process Marked as FAILED
    else Validation Success
        Note over ExcelIngestionService: Parse All Sheet Data
        ExcelIngestionService->>Database: Insert Campaign Data (Temporary Storage)
        Note over Database: CampaignDataTable:<br/>- Facility Data Rows<br/>- User Data Rows<br/>- Target Data Rows<br/>Status: PENDING

        Note over ExcelIngestionService: Start Sequential Processing

        loop For Each Facility
            ExcelIngestionService->>Database: Create Facility Record<br/>Update Row → COMPLETED
        end

        loop For Each User
            ExcelIngestionService->>Database: Create User Record<br/>Update Row → COMPLETED
        end

        loop For Each Project
            ExcelIngestionService->>Database: Create Project Record<br/>Update Row → COMPLETED
        end

        loop For Each Mapping
            ExcelIngestionService->>Database: Create Mapping Record<br/>Update Row → COMPLETED
        end

        ExcelIngestionService->>Database: Update Process Record<br/>(status: COMPLETED)
        Note over ExcelIngestionService: Excel Ingestion Work Complete
    end
```

## 4. Download API (For Template Generation)
**API:** `POST /v1/data/_download`

```mermaid
sequenceDiagram
    participant Client
    participant ExcelIngestionService
    participant Database
    
    Client->>ExcelIngestionService: POST /v1/data/_download<br/>(generateResourceId, referenceId (campaignId))
    
    ExcelIngestionService->>Database: Search Template Record<br/>(generateResourceId, referenceId (campaignId))
    Database-->>ExcelIngestionService: Template Record<br/>(fileStoreId, status)
    
    ExcelIngestionService-->>Client: DownloadResponse<br/>(fileStoreId or status)
```

## 5. Search API (For Process Status)
**API:** `POST /v1/data/_search`

```mermaid
sequenceDiagram
    participant Client
    participant ExcelIngestionService
    participant Database
    
    Client->>ExcelIngestionService: POST /v1/data/_search<br/>(resourceId, referenceId (campaignId))
    
    ExcelIngestionService->>Database: Search Process Record<br/>(resourceId, referenceId (campaignId))
    Database-->>ExcelIngestionService: Process Record<br/>(status, fileStoreId, referenceId (campaignId))
    
    ExcelIngestionService-->>Client: SearchResponse<br/>(status, data based on type - referenceId (campaignId) for creation)
```

## 6. Sheet Data Search API
**API:** `POST /v1/data/_searchData`

```mermaid
sequenceDiagram
    participant Client
    participant ExcelIngestionService
    participant Database
    
    Client->>ExcelIngestionService: POST /v1/data/_searchData<br/>(referenceId, type, status, uniqueIdentifier, createdBy, limit, offset)
    
    ExcelIngestionService->>Database: Search Sheet Data<br/>(with filters + pagination)
    Database-->>ExcelIngestionService: Records + Count
    
    ExcelIngestionService-->>Client: SearchDataResponse<br/>(data, totalCount)
```

## 7. Generated Files Table Documentation
**Table Name:** `eg_ex_in_generated_files`

This table stores metadata for all generated files (templates, validation reports, processed files) for download purposes.

### Database Schema Diagram
```mermaid
erDiagram
    eg_ex_in_generated_files {
        VARCHAR(100) id PK "Unique resource identifier"
        VARCHAR(100) referenceId "Campaign/Process reference ID"
        VARCHAR(50) type "File type (template/validation-report/processed)"
        VARCHAR(200) fileStoreId "FileStore service ID for download"
        VARCHAR(20) status "PENDING/FAILED/COMPLETED"
        TEXT errorDetails "Error details if failed"
        JSONB additionalDetails "Additional details as JSON"
        VARCHAR(100) createdBy "Creator user/system"
        VARCHAR(100) lastModifiedBy "Last modifier"
        BIGINT createdTime "Creation epoch seconds"
        BIGINT lastModifiedTime "Last modified epoch"
    }
```

### Column Details
| Column | Type | Description |
|--------|------|-------------|
| id | VARCHAR(100) PRIMARY KEY | Unique identifier for the generated resource |
| referenceId | VARCHAR(100) NOT NULL | Campaign or process reference ID |
| type | VARCHAR(50) NOT NULL | Type of file (microplan-template/validation-report/processed-excel) |
| fileStoreId | VARCHAR(200) | FileStore service ID for downloading the file |
| status | VARCHAR(20) NOT NULL | Generation status → PENDING, FAILED, COMPLETED |
| errorDetails | TEXT | Error details if generation failed |
| additionalDetails | JSONB | Additional details (file size, sheets count, validation summary) |
| createdBy | VARCHAR(100) | User/system who initiated generation |
| lastModifiedBy | VARCHAR(100) | User/system who last modified |
| createdTime | BIGINT | Creation timestamp in epoch seconds |
| lastModifiedTime | BIGINT | Last modification timestamp in epoch seconds |

### Purpose & Usage
- **File Tracking:** Track all generated files (templates, validation reports)
- **Download Management:** Store fileStoreId for download API
- **Status Monitoring:** Track generation status for async operations
- **Error Handling:** Store error details for failed generations
- **Audit Trail:** Maintain complete audit details for compliance

### Example: SQL Create Table Script
```sql
CREATE TABLE eg_ex_in_generated_files (
    id VARCHAR(100) PRIMARY KEY,
    referenceId VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    fileStoreId VARCHAR(200),
    status VARCHAR(20) NOT NULL,
    errorDetails TEXT,
    additionalDetails JSONB,
    createdBy VARCHAR(100),
    lastModifiedBy VARCHAR(100),
    createdTime BIGINT,
    lastModifiedTime BIGINT
);

-- Index for faster lookups
CREATE INDEX idx_generated_files_reference ON eg_ex_in_generated_files(referenceId);
CREATE INDEX idx_generated_files_type ON eg_ex_in_generated_files(type);
CREATE INDEX idx_generated_files_status ON eg_ex_in_generated_files(status);
```


## 8. Sheet Data Table Documentation
**Table Name:** `eg_ex_in_sheet_data`

This table provides row-wise temporary storage for the Excel ingestion workflow.
Each row represents a record from an Excel sheet, linked to a campaign/process.

### Database Schema Diagram
```mermaid
erDiagram
    eg_ex_in_sheet_data {
        VARCHAR(100) referenceId PK "Campaign/Process Reference ID"
        TEXT uniqueIdentifier PK "Unique key per row"
        VARCHAR(50) type PK "Sheet type (Facility/User/Target)"
        JSONB rowData "Full Excel row data as JSON"
        VARCHAR(20) status "PENDING/FAILED/COMPLETED"
        BIGINT deleteTime "Expiry epoch (NULL=permanent)"
        VARCHAR(100) createdBy "Creator user/system"
        VARCHAR(100) lastModifiedBy "Last modifier"
        BIGINT createdTime "Creation epoch seconds"
        BIGINT lastModifiedTime "Last modified epoch"
    }
```

### Column Details
| Column | Type | Description |
|--------|------|-------------|
| referenceId | VARCHAR(100) NOT NULL | Campaign or process reference ID (scope of ingestion). |
| uniqueIdentifier | TEXT NOT NULL | Unique key per row (single/composite/custom). |
| type | VARCHAR(50) NOT NULL | Sheet type (e.g., Facility, User, Target). |
| rowData | JSONB NOT NULL | Full row data from Excel sheet, stored as JSON. |
| status | VARCHAR(20) NOT NULL | Row processing status → PENDING, FAILED, COMPLETED. |
| deleteTime | BIGINT | Expiry timestamp in epoch seconds. NULL = permanent row. |
| createdBy | VARCHAR(100) | User/system who created the row. |
| lastModifiedBy | VARCHAR(100) | User/system who last modified the row. |
| createdTime | BIGINT | Row creation timestamp in epoch seconds, set by application. |
| lastModifiedTime | BIGINT | Last modification timestamp in epoch seconds, set by application. |

### Keys
**Primary Key:** (referenceId, uniqueIdentifier, type)
- Ensures uniqueness of rows per campaign/process per sheet type.

### Purpose & Usage
- **Staging Table:** Temporary storage of Excel sheet rows before final ingestion.
- **Validation & Tracking:**
  - Each row has a status → track validation or processing result.
- **Temporary vs Permanent Rows:**
  - deleteTime = NULL → row is permanent.
  - deleteTime = epoch → row can be purged after that time.
- **Row Data Flexibility:**
  - rowData as JSONB allows storing arbitrary columns from Excel without altering table schema.

### Example: SQL Create Table Script
```sql
CREATE TABLE eg_ex_in_sheet_data (
    referenceId VARCHAR(100) NOT NULL,
    uniqueIdentifier TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    rowData JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    deleteTime BIGINT,            -- NULL = permanent
    createdBy VARCHAR(100),
    lastModifiedBy VARCHAR(100),
    createdTime BIGINT,           -- epoch seconds, set by application
    lastModifiedTime BIGINT,      -- epoch seconds, set by application
    PRIMARY KEY (referenceId, uniqueIdentifier, type)
);
```


### Process Steps:
1. **Process Sheet**: Extract all data from unified sheet
2. **Add Facilities**: Add new facilities (PENDING, UniqueId: Facility Name)
3. **Add Facility-Mappings**: Add facility-boundary mappings (PENDING, UniqueId: Facility Name + Boundary Code)
4. **Add Users**: Add users (PENDING, UniqueId: Mobile Number)  
5. **Add Projects**: Add projects (PENDING, UniqueId: Boundary Code)
6. **Add Project-Mappings**: Add project-boundary mappings (PENDING, UniqueId: Boundary Code + Resource ID)
7. **Create All**: Parallel creation - facilities + users + projects (together), then mappings (together) (PENDING → COMPLETED/FAILED)
8. **Complete/Retry**: If all COMPLETED = success, if any FAILED = retry from PENDING items

## Update Flow - Addition Only

For campaign updates via Excel ingestion, **only addition of new data is supported**:

- ✅ **Add new facilities** to existing campaign
- ✅ **Add new users** to existing campaign  
- ✅ **Add new projects** to existing campaign
- ✅ **Add new mappings** (facility-boundary, user-boundary, project-resource)

### Removal/Demapping Operations

For **removal or demapping** operations, **UI-based approach is required**:

- ❌ **Remove facility** from campaign → Use UI + Facility Demapping API
- ❌ **Remove user** from campaign → Use UI + Staff Demapping API
- ❌ **Remove project** from campaign → Use UI + Project Update API to make project inactive and parent null
- ❌ **Demap resource** from campaign → Use UI + Resource Demapping API

**Reason**: Demapping and removal operations require specific API calls that are better handled through UI workflows rather than bulk Excel processing.

**Note**: To know campaign data for UI display, use **Excel Ingestion Search API** (`POST /v1/data/_searchData`) to fetch and show existing facilities, users, projects, and mappings in the UI.

