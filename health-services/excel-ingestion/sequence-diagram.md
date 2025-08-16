# Excel Ingestion Service - Sequence Diagram

## Excel Generation Flow

```mermaid
sequenceDiagram
    participant Client
    participant ExcelIngestionService
    participant boundary-service
    participant localization-service
    participant egov-mdms-service
    participant ExcelGenerator
    participant filestore-service
    
    Client->>ExcelIngestionService: POST /v1/data/_generate<br/>(GenerateResourceRequest)
    
    Note over ExcelIngestionService: Validate Request
    
    ExcelIngestionService->>boundary-service: Fetch Boundary Hierarchy<br/>(tenantId, hierarchyType)
    boundary-service-->>ExcelIngestionService: Boundary Hierarchy Data
    
    ExcelIngestionService->>localization-service: Fetch Localized Labels<br/>(locale, module, tenantId)
    localization-service-->>ExcelIngestionService: Localized Messages
    
    ExcelIngestionService->>egov-mdms-service: Fetch Master Data<br/>(module config, schemas)
    egov-mdms-service-->>ExcelIngestionService: MDMS Configuration
    
    ExcelIngestionService->>ExcelGenerator: Generate Excel Sheets
    
    Note over ExcelGenerator: Create Workbook
    Note over ExcelGenerator: Sheet 1: Campaign Config
    Note over ExcelGenerator: Sheet 2: Facility Sheet
    Note over ExcelGenerator: Sheet 3: User Sheet
    Note over ExcelGenerator: Sheet 4: Boundary Sheet
    
    ExcelGenerator-->>ExcelIngestionService: Generated Excel (byte[])
    
    ExcelIngestionService->>filestore-service: Upload Excel File<br/>(multipart file)
    filestore-service-->>ExcelIngestionService: FileStore ID
    
    Note over ExcelIngestionService: Update Resource<br/>with FileStore ID
    
    ExcelIngestionService-->>Client: GenerateResourceResponse<br/>(with fileStoreId)
```

## Error Handling Flow

```mermaid
sequenceDiagram
    participant Client
    participant ExcelIngestionService
    participant Service
    
    Client->>ExcelIngestionService: POST /v1/data/_generate
    
    alt Validation Error
        ExcelIngestionService-->>Client: 400 Bad Request<br/>(Throw ValidationException)
    else Service Error
        ExcelIngestionService->>Service: Service Call
        Service-->>ExcelIngestionService: Error/Timeout
        ExcelIngestionService-->>Client: 500 Internal Server Error<br/>(Throw ServiceException)
    else Success
        ExcelIngestionService-->>Client: 200 OK<br/>(FileStore ID)
    end
```

## Key Components

1. **GenerateController**: REST endpoint handler
2. **ExcelGenerationService**: Core business logic and error handling
3. **BoundaryService**: Integration with boundary-service
4. **LocalizationService**: Integration with localization-service
5. **MDMSService**: Integration with egov-mdms-service
6. **FileStoreService**: Integration with filestore-service
7. **Sheet Creators**: 
   - BoundaryHierarchySheetCreator
   - CampaignConfigSheetCreator
   - ExcelSchemaSheetCreator

## Data Flow Summary

1. **Input**: Tenant ID, Hierarchy Type, Reference ID, Boundary filters
2. **Processing**: Fetch data → Generate Excel → Upload file
3. **Output**: File Store ID for download

## Service Endpoints Used

- **boundary-service**: `/boundary-service/boundary-hierarchy-definition/_search`
- **localization-service**: `/localization/messages/v1/_search`
- **egov-mdms-service**: `/egov-mdms-service/v1/_search`
- **filestore-service**: `/filestore/v1/files`