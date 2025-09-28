# Boundary Bulk Service APIs - Sequence Diagrams

## Overview
This document contains sequence diagrams for the new Boundary Bulk Service APIs that will handle boundary creation with support for Excel file uploads and process tracking.

## API Specifications

### 1. Boundary Bulk Create API

**Endpoint:** `/boundary-bulk-service/boundary/_create`

**Request Body:**
```json
{
  "RequestInfo": {
    "authToken": "string",
    "userInfo": {...}
  },
  "BoundaryDetails": {
    "tenantId": "string",
    "hierarchyType": "string",
    "fileType": "excel"
  }
}
```

**Response:**
```json
{
  "ResponseInfo": {...},
  "ProcessDetails": {
    "processId": "uuid",
    "status": "started"
  }
}
```

**Sequence Diagram:**

```mermaid
sequenceDiagram
    participant Client
    participant BulkAPI as Boundary Bulk Service
    participant FileStore
    participant Database
    participant BoundaryService
    participant LocalizationService

    Client->>BulkAPI: POST /boundary-bulk-service/boundary/_create<br/>(RequestInfo, BoundaryDetails, File)
    
    BulkAPI->>BulkAPI: Validate Request
    
    BulkAPI->>Database: Create Process Record<br/>(status: STARTED)
    Database-->>BulkAPI: Process ID
    
    BulkAPI-->>Client: 202 Accepted<br/>(ProcessId, Status: STARTED)
    
    Note over BulkAPI: Background Processing
    
    BulkAPI->>FileStore: Upload Excel File
    FileStore-->>BulkAPI: FileStore ID
    
    BulkAPI->>BulkAPI: Parse Excel & Extract Data
    
    BulkAPI->>BulkAPI: Auto-generate Boundary Codes
    
    BulkAPI->>BoundaryService: Create Boundaries & Relationships
    BoundaryService-->>BulkAPI: Success
    
    BulkAPI->>LocalizationService: Create Localization
    LocalizationService-->>BulkAPI: Success
    
    BulkAPI->>FileStore: Upload Processed File
    FileStore-->>BulkAPI: Processed FileStore ID
    
    BulkAPI->>Database: Update Process<br/>(status: COMPLETED)
```

### 2. Boundary Bulk Search API

**Endpoint:** `/boundary-bulk-service/boundary/_search`

**Request Body:**
```json
{
  "RequestInfo": {
    "authToken": "string",
    "userInfo": {...}
  },
  "SearchCriteria": {
    "processId": "uuid",
    "tenantId": "string",
    "hierarchyType": "string",
    "status": "string"
  }
}
```

**Response:**
```json
{
  "ResponseInfo": {...},
  "BoundaryProcessDetails": [{
    "processId": "uuid",
    "status": "completed",
    "fileStoreId": "string",
    "processedFileStoreId": "string",
    "tenantId": "string",
    "hierarchyType": "string",
    "createdBy": "string",
    "createdTime": 1234567890,
    "errors": []
  }]
}
```

**Sequence Diagram:**

```mermaid
sequenceDiagram
    participant Client
    participant BulkAPI as Boundary Bulk Service
    participant Database
    participant FileStore

    Client->>BulkAPI: POST /boundary-bulk-service/boundary/_search<br/>(RequestInfo, SearchCriteria)
    
    BulkAPI->>BulkAPI: Validate Request
    
    alt Search by ProcessId
        BulkAPI->>Database: Query by ProcessId
    else Search by HierarchyType
        BulkAPI->>Database: Query by HierarchyType & TenantId
    else Search by Status
        BulkAPI->>Database: Query by Status & TenantId
    else Search by TenantId
        BulkAPI->>Database: Query by TenantId
    end
    
    Database-->>BulkAPI: Process Records
    
    loop For each process
        BulkAPI->>FileStore: Get File URLs
        FileStore-->>BulkAPI: URLs
    end
    
    BulkAPI-->>Client: 200 OK<br/>(BoundaryProcessDetails)
```

### 3. Boundary Template Generate API

**Endpoint:** `/boundary-bulk-service/boundary/template/_generate`

**Request Body:**
```json
{
  "RequestInfo": {
    "authToken": "string",
    "userInfo": {...}
  },
  "TemplateGenerateDetails": {
    "tenantId": "string",
    "hierarchyType": "string",
    "campaignId": "string",
    "includeExistingBoundaries": true
  }
}
```

**Response:**
```json
{
  "ResponseInfo": {...},
  "TemplateDetails": {
    "fileStoreId": "string",
    "templateUrl": "string"
  }
}
```

**Sequence Diagram:**

```mermaid
sequenceDiagram
    participant Client
    participant BulkAPI as Boundary Bulk Service
    participant HierarchyService
    participant BoundaryService
    participant FileStore
    participant Cache

    Client->>BulkAPI: POST /boundary-bulk-service/boundary/template/_generate<br/>(RequestInfo, TemplateGenerateDetails)
    
    BulkAPI->>BulkAPI: Validate Request
    
    BulkAPI->>Cache: Check Cache<br/>(tenantId-hierarchyType-campaignId)
    
    alt Cache Hit
        Cache-->>BulkAPI: Cached Template
        BulkAPI-->>Client: 200 OK (Cached)
    else Cache Miss
        BulkAPI->>HierarchyService: Get Hierarchy Levels
        HierarchyService-->>BulkAPI: Hierarchy Data
        
        alt Include Existing Boundaries
            BulkAPI->>BoundaryService: Get Boundaries
            BoundaryService-->>BulkAPI: Boundary Data
        end
        
        BulkAPI->>BulkAPI: Generate Excel Template
        
        BulkAPI->>FileStore: Upload Template
        FileStore-->>BulkAPI: FileStore ID
        
        BulkAPI->>Cache: Store in Cache (5 min)
        
        BulkAPI-->>Client: 200 OK<br/>(TemplateDetails)
    end
```

### 4. Boundary Template Search API

**Endpoint:** `/boundary-bulk-service/boundary/template/_search`

**Request Body:**
```json
{
  "RequestInfo": {
    "authToken": "string",
    "userInfo": {...}
  },
  "SearchCriteria": {
    "tenantId": "string",
    "hierarchyType": "string"
  }
}
```

**Response:**
```json
{
  "ResponseInfo": {...},
  "Templates": [{
    "id": "uuid",
    "tenantId": "string",
    "hierarchyType": "string",
    "fileStoreId": "string",
    "createdBy": "string",
    "createdTime": 1234567890
  }]
}
```

**Sequence Diagram:**

```mermaid
sequenceDiagram
    participant Client
    participant BulkAPI as Boundary Bulk Service
    participant Database
    participant FileStore

    Client->>BulkAPI: POST /boundary-bulk-service/boundary/template/_search<br/>(RequestInfo, SearchCriteria)
    
    BulkAPI->>BulkAPI: Validate Request
    
    BulkAPI->>Database: Query Templates<br/>(tenantId, hierarchyType)
    Database-->>BulkAPI: Template Records
    
    loop For each template
        BulkAPI->>FileStore: Get File URLs
        FileStore-->>BulkAPI: URLs
    end
    
    BulkAPI-->>Client: 200 OK<br/>(Templates List)
```

## Key Components and Their Responsibilities

### 1. **Boundary Bulk Service**
- Main service handling bulk boundary operations
- Manages async processing with process IDs
- Coordinates with other services for boundary creation

### 2. **Process Management**
- Generates unique process IDs
- Tracks process status (STARTED → PROCESSING → COMPLETED/FAILED)
- Stores process metadata in database

### 3. **Excel Processing**
- Parses uploaded Excel files
- Validates data format and hierarchy
- Auto-generates boundary codes

### 4. **Boundary Service**
- Creates boundary entities
- Establishes parent-child relationships
- Manages boundary hierarchy

### 5. **Localization Service**
- Creates localization messages for boundary names
- Supports multiple languages
- Batch processes localization data

## Error Handling

All APIs implement comprehensive error handling:

1. **Validation Errors (400)**
   - Missing required fields
   - Invalid file format
   - Invalid hierarchy type

2. **Processing Errors (500)**
   - File processing failures
   - Database connection issues
   - External service failures

3. **Not Found Errors (404)**
   - Process ID not found
   - Template not found

## Performance Considerations

1. **Batch Processing**
   - Boundary entities created in batches of 200
   - Localization messages processed in chunks

2. **Caching**
   - Template generation cached for 5 minutes
   - Redis used for performance optimization

3. **Async Processing**
   - Long-running operations handled asynchronously
   - Process status tracking for monitoring

## Database Schema

### Process Table
```sql
CREATE TABLE boundary_process (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    hierarchy_type VARCHAR(128) NOT NULL,
    file_type VARCHAR(32),
    file_store_id VARCHAR(256),
    processed_file_store_id VARCHAR(256),
    status VARCHAR(32) NOT NULL,
    errors JSONB,
    created_by VARCHAR(256),
    created_time BIGINT,
    last_modified_by VARCHAR(256),
    last_modified_time BIGINT
);
```

### Template Table
```sql
CREATE TABLE boundary_template (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    hierarchy_type VARCHAR(128) NOT NULL,
    campaign_id VARCHAR(256),
    file_store_id VARCHAR(256) NOT NULL,
    created_by VARCHAR(256),
    created_time BIGINT
);
```