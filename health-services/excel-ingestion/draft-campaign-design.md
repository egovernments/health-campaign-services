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
    participant ProjectFactoryService
    
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
        Note over Database: CampaignDataTable:
        Note over Database: - Facility Data Rows
        Note over Database: - User Data Rows  
        Note over Database: - Target Data Rows
        Note over Database: Status: PENDING
        
        Note over ExcelIngestionService: Data Storage Complete
        ExcelIngestionService->>Database: Update Process Record<br/>(resourceId, status: COMPLETED)
        
        ExcelIngestionService->>ProjectFactoryService: POST /campaign/_create<br/>(Campaign Data + Process Config + referenceId (campaignId))
        
        Note over ExcelIngestionService: Excel Ingestion Work Complete
        
        Note over ProjectFactoryService: Project Factory handles all creation
        ProjectFactoryService->>Database: Create 7 Process Records
        Note over Database: CampaignProcessTable:
        Note over Database: 1. Facility Create - PENDING
        Note over Database: 2. User Create - PENDING
        Note over Database: 3. Project Create - PENDING
        Note over Database: 4. Facility Mapping - PENDING
        Note over Database: 5. User Mapping - PENDING
        Note over Database: 6. Resource Mapping - PENDING
        Note over Database: 7. Credential Generation - PENDING
        
        Note over ProjectFactoryService: - Facility Creation
        Note over ProjectFactoryService: - User Creation  
        Note over ProjectFactoryService: - Project Creation
        Note over ProjectFactoryService: - All Mappings
        Note over ProjectFactoryService: - Credential Generation
        
        ProjectFactoryService->>Database: Campaign Created in DB<br/>(Updates all process records to COMPLETED)
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
