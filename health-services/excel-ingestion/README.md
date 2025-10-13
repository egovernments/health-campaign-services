# Excel Ingestion Service

### Excel Ingestion Service
Excel Ingestion Service is a comprehensive Health Campaign Service that facilitates Excel template generation, processing, and sheet data management. The service supports full workflow including Excel template creation, file processing with validation, and temporary sheet data management. All functionality is exposed via REST APIs with async processing support.

### Service Architecture Diagrams

## 1. Excel Generation Flow (Async)

```mermaid
sequenceDiagram
    participant Client
    participant ExcelIngestionService
    participant boundary-service
    participant localization-service
    participant egov-mdms-service
    participant filestore-service
    
    Client->>ExcelIngestionService: POST /v1/data/_generate<br/>(GenerateResourceRequest)
    
    Note over ExcelIngestionService: Validate Request & Store in DB
    Note over ExcelIngestionService: Create Generation ID
    
    ExcelIngestionService-->>Client: 202 Accepted<br/>GenerateResourceResponse<br/>(id, status: PENDING)
    
    Note over ExcelIngestionService: Async Processing Starts
    
    ExcelIngestionService->>boundary-service: Fetch Boundary Hierarchy<br/>(tenantId, hierarchyType)
    boundary-service-->>ExcelIngestionService: Boundary Hierarchy Data
    
    ExcelIngestionService->>localization-service: Fetch Localized Labels<br/>(locale, module, tenantId)
    localization-service-->>ExcelIngestionService: Localized Messages
    
    ExcelIngestionService->>egov-mdms-service: Fetch Master Data<br/>(module config, schemas)
    egov-mdms-service-->>ExcelIngestionService: MDMS Configuration
    
    Note over ExcelIngestionService: Generate Excel Sheets
    Note over ExcelIngestionService: Create Workbook
    Note over ExcelIngestionService: Sheet 1: Facility Sheet
    Note over ExcelIngestionService: Sheet 2: User Sheet
    Note over ExcelIngestionService: Sheet 3: Boundary Sheet
    
    ExcelIngestionService->>filestore-service: Upload Excel File<br/>(multipart file)
    filestore-service-->>ExcelIngestionService: FileStore ID
    
    Note over ExcelIngestionService: Update DB Record<br/>status: COMPLETED, fileStoreId
```

## 2. Excel Processing Flow (Async)

```mermaid
sequenceDiagram
    participant Client
    participant ExcelIngestionService
    participant filestore-service
    participant project-factory
    participant Database
    
    Client->>ExcelIngestionService: POST /v1/data/_process<br/>(ProcessResourceRequest with fileStoreId)
    
    Note over ExcelIngestionService: Validate Request & Store in DB
    Note over ExcelIngestionService: Create Processing ID
    
    ExcelIngestionService-->>Client: 202 Accepted<br/>ProcessResourceResponse<br/>(id, status: PENDING)
    
    Note over ExcelIngestionService: Async Processing Starts
    
    ExcelIngestionService->>filestore-service: Download Excel File<br/>(fileStoreId)
    filestore-service-->>ExcelIngestionService: Excel File Data
    
    Note over ExcelIngestionService: Parse Excel Workbook
    Note over ExcelIngestionService: Validate All Sheets
    Note over ExcelIngestionService: Store Parsed Data in DB
    
    ExcelIngestionService->>Database: Save Sheet Data to<br/>eg_sheetdata_temp
    
    alt Processing Successful
        Note over ExcelIngestionService: Update Processing Status<br/>status: COMPLETED
        ExcelIngestionService->>project-factory: Send Processing Result<br/>(HCM_PROCESSING_RESULT_TOPIC)
    else Processing Failed
        Note over ExcelIngestionService: Update Processing Status<br/>status: FAILED with errors
        ExcelIngestionService->>project-factory: Send Failure Result<br/>(HCM_PROCESSING_RESULT_TOPIC)
    end
```

## 3. Generation Search Flow

```mermaid
sequenceDiagram
    participant Client
    participant ExcelIngestionService
    participant Database
    
    Client->>ExcelIngestionService: POST /v1/data/_generationSearch<br/>(GenerationSearchRequest)
    
    Note over ExcelIngestionService: Validate Search Criteria
    Note over ExcelIngestionService: Build Query with Arrays<br/>(ids[], referenceIds[], types[], statuses[])
    
    ExcelIngestionService->>Database: SELECT * FROM eg_excelgeneration<br/>WHERE tenantId = ? AND<br/>id IN (ids[]) AND<br/>referenceId IN (referenceIds[]) AND<br/>type IN (types[]) AND<br/>status IN (statuses[])
    Database-->>ExcelIngestionService: Generation Records List
    
    Note over ExcelIngestionService: Apply Pagination<br/>(limit, offset)
    Note over ExcelIngestionService: Build Response
    
    ExcelIngestionService-->>Client: 200 OK<br/>GenerationSearchResponse<br/>(GenerateResources[])
```

## 4. Processing Search Flow

```mermaid
sequenceDiagram
    participant Client
    participant ExcelIngestionService
    participant Database
    
    Client->>ExcelIngestionService: POST /v1/data/_processSearch<br/>(ProcessingSearchRequest)
    
    Note over ExcelIngestionService: Validate Search Criteria
    Note over ExcelIngestionService: Build Query with Arrays<br/>(ids[], referenceIds[], types[], statuses[])
    
    ExcelIngestionService->>Database: SELECT * FROM eg_excelprocessing<br/>WHERE tenantId = ? AND<br/>id IN (ids[]) AND<br/>referenceId IN (referenceIds[]) AND<br/>type IN (types[]) AND<br/>status IN (statuses[])
    Database-->>ExcelIngestionService: Processing Records List
    
    Note over ExcelIngestionService: Apply Pagination<br/>(limit, offset)
    Note over ExcelIngestionService: Build Response with<br/>Validation Details
    
    ExcelIngestionService-->>Client: 200 OK<br/>ProcessingSearchResponse<br/>(ProcessResources[])
```

## 5. Sheet Data Search Flow

```mermaid
sequenceDiagram
    participant Client
    participant ExcelIngestionService
    participant Database
    
    Client->>ExcelIngestionService: POST /v1/data/sheet/_search<br/>(SheetDataSearchRequest)
    
    Note over ExcelIngestionService: Validate Search Criteria
    Note over ExcelIngestionService: Build Query Parameters<br/>(tenantId, referenceId, fileStoreId, sheetName)
    
    ExcelIngestionService->>Database: SELECT * FROM eg_sheetdata_temp<br/>WHERE tenantId = ? AND<br/>referenceId = ? AND<br/>fileStoreId = ? AND<br/>sheetName = ?<br/>ORDER BY rowNumber<br/>LIMIT ? OFFSET ?
    Database-->>ExcelIngestionService: Sheet Data Records
    
    Note over ExcelIngestionService: Apply Pagination<br/>(limit, offset)
    Note over ExcelIngestionService: Build Response with<br/>Row JSON Data
    
    ExcelIngestionService-->>Client: 200 OK<br/>SheetDataSearchResponse<br/>(SheetDataDetails[])
```

## 6. Sheet Data Delete Flow

```mermaid
sequenceDiagram
    participant Client
    participant ExcelIngestionService
    participant Database
    
    Client->>ExcelIngestionService: POST /v1/data/sheet/_delete<br/>?tenantId=x&referenceId=y&fileStoreId=z
    
    Note over ExcelIngestionService: Validate Query Parameters
    
    ExcelIngestionService->>Database: DELETE FROM eg_sheetdata_temp<br/>WHERE conditions match
    Database-->>ExcelIngestionService: Deletion Count
    
    ExcelIngestionService-->>Client: 202 Accepted<br/>SheetDataDeleteResponse<br/>(success message)
```

## 7. Error Handling Flow

```mermaid
sequenceDiagram
    participant Client
    participant ExcelIngestionService
    participant Service
    participant Database
    
    Client->>ExcelIngestionService: POST /v1/data/_generate or _process
    
    alt Validation Error
        ExcelIngestionService-->>Client: 400 Bad Request<br/>(ValidationException)
    else Service Error
        ExcelIngestionService->>Service: Service Call
        Service-->>ExcelIngestionService: Error/Timeout
        ExcelIngestionService->>Database: Update status: FAILED
        ExcelIngestionService-->>Client: 202 Accepted<br/>(Process continues async)
    else Success
        ExcelIngestionService->>Database: Update status: COMPLETED
        ExcelIngestionService-->>Client: 202 Accepted<br/>(Async processing)
    end
```

### DB UML Diagram

```mermaid
erDiagram
    eg_ex_in_generated_files {
        varchar id PK
        varchar referenceId
        varchar tenantId
        varchar type
        varchar hierarchyType
        varchar fileStoreId
        varchar status
        varchar locale
        jsonb additionalDetails
        bigint createdTime
        bigint lastModifiedTime
        varchar createdBy
        varchar lastModifiedBy
    }

    eg_ex_in_excel_processing {
        varchar id PK
        varchar referenceId
        varchar tenantId
        varchar type
        varchar hierarchyType
        varchar fileStoreId
        varchar processedFileStoreId
        varchar status
        jsonb additionalDetails
        bigint createdTime
        bigint lastModifiedTime
        varchar createdBy
        varchar lastModifiedBy
    }

    eg_ex_in_sheet_data_temp {
        varchar referenceId PK
        varchar tenantId
        varchar fileStoreId PK
        varchar sheetName PK
        integer rowNumber PK
        jsonb rowJson
        varchar createdBy
        bigint createdTime
        bigint deleteTime
    }

    eg_ex_in_generated_files ||--o{ eg_ex_in_excel_processing : "fileStoreId used for processing"
    eg_ex_in_excel_processing ||--o{ eg_ex_in_sheet_data_temp : "creates temp data during processing"
```

#### Table Details:
- **eg_ex_in_generated_files**: Tracks async Excel template generation requests
- **eg_ex_in_excel_processing**: Tracks async Excel file processing requests  
- **eg_ex_in_sheet_data_temp**: Stores parsed Excel data temporarily during validation

#### Key Relationships:
- Generated Excel (fileStoreId) can be used for processing
- Processing requests create temporary sheet data for validation
- Sheet temp data is cleaned up after processing completion

### Service Dependencies

#### Core Platform Services
- **egov-filestore**: Excel file upload/download management
- **egov-localization**: Multi-language support for labels and messages
- **egov-mdms**: Master data configuration and schemas
- **boundary-service**: Boundary hierarchy and relationships

#### Health Campaign Services  
- **project-factory**: Campaign data search and crypto operations
- **health-individual**: Individual/user data search and validation
- **facility**: Facility data search and validation

#### External Libraries
- Apache POI (Excel generation and parsing)
- Spring Boot
- Spring Web
- Spring Kafka (Producer integration)
- PostgreSQL (Database)
- Flyway (Database migrations)

### Swagger API Contract
Link to the swagger API contract yaml and editor link like below

https://editor.swagger.io/?url=https://raw.githubusercontent.com/egovernments/health-campaign-services/master/health-services/excel-ingestion/excel-ingestion-swagger.yml

For local reference, see [excel-ingestion-swagger.yml](./excel-ingestion-swagger.yml)

### Service Details

#### Functionality

1. **Excel Template Generation**: Generates Excel templates with boundary hierarchy data (Async)
2. **Excel File Processing**: Validates and processes uploaded Excel files with comprehensive error reporting (Async)
3. **Sheet Data Management**: Search and delete temporary sheet data stored during processing
4. **Generation/Processing Search**: Track and monitor async operations with detailed status
5. **Multi-sheet Support**: Creates multiple sheets including:
   - Facility Sheet  
   - User Sheet
   - Boundary Sheet
6. **Dynamic Column Generation**: Dynamically creates columns based on hierarchy levels
7. **File Upload**: Automatically uploads generated Excel files to egov-filestore
8. **Localization Support**: Integrates with localization service for multi-language support
9. **Data Validation**: Comprehensive validation rules for Excel data processing
10. **Error Reporting**: Detailed error reporting with line-by-line validation results

#### Features

1. **Configurable Templates**: Supports different template types based on hierarchy type
2. **Boundary Validation**: Validates boundary data before Excel generation
3. **Error Handling**: Comprehensive error handling with meaningful error messages
4. **Async Processing**: Supports asynchronous processing for large datasets
5. **Multi-tenant Support**: Full multi-tenant architecture support

#### API Details
BasePath `/excel-ingestion/v1/data`

Excel Ingestion service APIs - comprehensive suite for Excel workflow management

**Generation APIs:**
* POST `/v1/data/_generate` - Generate Excel Template (Async), generates Excel template and uploads to filestore
* POST `/v1/data/_generationSearch` - Search generation records with status tracking

**Processing APIs:**  
* POST `/v1/data/_process` - Process Excel File (Async), validates uploaded Excel files 
* POST `/v1/data/_processSearch` - Search processing records with detailed results

**Sheet Data Management APIs:**
* POST `/v1/data/sheet/_search` - Search temporary sheet data by various criteria
* POST `/v1/data/sheet/_delete` - Delete temporary sheet data for cleanup

##### Request Structure
```json
{
  "RequestInfo": {
    "apiId": "excel-ingestion",
    "ver": "1.0",
    "ts": 1690371438000,
    "msgId": "1234567890",
    "userInfo": {
      "uuid": "11b0e02b-0145-4de2-bc42-c97b96264807"
    }
  },
  "GenerateResource": {
    "tenantId": "pg.citya",
    "type": "boundary",
    "hierarchyType": "ADMIN",
    "referenceId": "REF-2023-001",
    "additionalDetails": {}
  }
}
```

##### Response Structure
```json
{
  "ResponseInfo": {
    "apiId": "egov-bff",
    "ver": "0.0.1",
    "ts": 1690371438000,
    "status": "successful"
  },
  "GenerateResource": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "tenantId": "pg.citya",
    "type": "boundary",
    "hierarchyType": "ADMIN",
    "referenceId": "REF-2023-001",
    "status": "PENDING",
    "fileStoreId": null,
    "additionalDetails": {}
  }
}
```

### Configuration

### Kafka Consumers

- **NA** - This service does not consume from any Kafka topics

### Kafka Producers

This service produces to the following Kafka topics:

**Generation Service Topics:**
- **save-generated-file**: Initial generation request records
- **update-generated-file**: Updates to generation status (PENDING → COMPLETED/FAILED)

**Processing Service Topics:**
- **save-processing-file**: Initial processing request records  
- **update-processing-file**: Updates to processing status (PENDING → COMPLETED/FAILED)

**Sheet Data Topics:**
- **save-sheet-data-temp**: Saves parsed Excel data to temporary storage (chunks of 200 records)
- **delete-sheet-data-temp**: Deletes temporary sheet data after processing

**Result Topics:**
- **hcm-processing-result**: Sends processing results to project-factory service (configured dynamically)

## Excel Template Structure

### Sheet 1: Facility Sheet
Contains facility data with boundary columns for mapping facilities to geographical areas.

### Sheet 2: User Sheet
Contains user information with boundary columns for assigning users to specific areas.

### Sheet 3: Boundary Sheet
Contains the complete boundary hierarchy with dynamic columns based on hierarchy levels.

## Error Codes

| Error Code | Description |
|------------|-------------|
| INGEST_MISSING_TENANT_ID | Tenant ID is required |
| INGEST_INVALID_TENANT_ID_LENGTH | Tenant ID length must be between 2-50 characters |
| INGEST_MISSING_TYPE | Resource type is required |
| INGEST_INVALID_TYPE_LENGTH | Type length must be between 2-100 characters |
| INGEST_MISSING_HIERARCHY_TYPE | Hierarchy type is required |
| INGEST_INVALID_HIERARCHY_TYPE_LENGTH | Hierarchy type length must be between 2-100 characters |
| INGEST_MISSING_REFERENCE_ID | Reference ID is required |
| INGEST_INVALID_REFERENCE_ID_LENGTH | Reference ID length must be between 1-255 characters |

## Pre commit script

[commit-msg](https://gist.github.com/jayantp-egov/14f55deb344f1648503c6be7e580fa12)

## Usage

1. Start the service
2. Call the generate API with appropriate boundary data
3. Receive the fileStoreId in response
4. Use the fileStoreId to download the generated Excel template from filestore service

## Example cURL Commands

### Generate Excel Template
```bash
curl -X POST \
  http://localhost:8080/excel-ingestion/v1/data/_generate \
  -H 'Content-Type: application/json' \
  -d '{
    "RequestInfo": {
      "apiId": "excel-ingestion",
      "ver": "1.0",
      "ts": 1690371438000,
      "userInfo": {
        "uuid": "11b0e02b-0145-4de2-bc42-c97b96264807"
      }
    },
    "GenerateResource": {
      "tenantId": "pg.citya",
      "type": "boundary",
      "hierarchyType": "ADMIN",
      "referenceId": "REF-2023-001",
      "additionalDetails": {}
    }
  }'
```

### Process Excel File
```bash
curl -X POST \
  http://localhost:8080/excel-ingestion/v1/data/_process \
  -H 'Content-Type: application/json' \
  -d '{
    "RequestInfo": {
      "apiId": "excel-ingestion",
      "ver": "1.0",
      "ts": 1690371438000,
      "userInfo": {
        "uuid": "11b0e02b-0145-4de2-bc42-c97b96264807"
      }
    },
    "ResourceDetails": {
      "tenantId": "pg.citya",
      "type": "boundary",
      "hierarchyType": "ADMIN",
      "referenceId": "REF-2023-001",
      "fileStoreId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "additionalDetails": {}
    }
  }'
```

### Search Generation Records
```bash
curl -X POST \
  http://localhost:8080/excel-ingestion/v1/data/_generationSearch \
  -H 'Content-Type: application/json' \
  -d '{
    "RequestInfo": {
      "apiId": "excel-ingestion",
      "ver": "1.0",
      "ts": 1690371438000,
      "userInfo": {
        "uuid": "11b0e02b-0145-4de2-bc42-c97b96264807"
      }
    },
    "GenerationSearchCriteria": {
      "tenantId": "pg.citya",
      "ids": ["550e8400-e29b-41d4-a716-446655440000"],
      "referenceIds": ["REF-2023-001"],
      "types": ["boundary"],
      "statuses": ["COMPLETED"],
      "locale": "en_IN",
      "limit": 10,
      "offset": 0
    }
  }'
```

### Search Processing Records
```bash
curl -X POST \
  http://localhost:8080/excel-ingestion/v1/data/_processSearch \
  -H 'Content-Type: application/json' \
  -d '{
    "RequestInfo": {
      "apiId": "excel-ingestion",
      "ver": "1.0",
      "ts": 1690371438000,
      "userInfo": {
        "uuid": "11b0e02b-0145-4de2-bc42-c97b96264807"
      }
    },
    "ProcessingSearchCriteria": {
      "tenantId": "pg.citya",
      "ids": ["550e8400-e29b-41d4-a716-446655440001"],
      "referenceIds": ["REF-2023-001"],
      "types": ["boundary"],
      "statuses": ["COMPLETED", "FAILED"],
      "limit": 10,
      "offset": 0
    }
  }'
```

### Search Sheet Data
```bash
curl -X POST \
  http://localhost:8080/excel-ingestion/v1/data/sheet/_search \
  -H 'Content-Type: application/json' \
  -d '{
    "RequestInfo": {
      "apiId": "excel-ingestion",
      "ver": "1.0",
      "ts": 1690371438000,
      "userInfo": {
        "uuid": "11b0e02b-0145-4de2-bc42-c97b96264807"
      }
    },
    "SheetDataSearchCriteria": {
      "tenantId": "pg.citya",
      "referenceId": "REF-2023-001",
      "fileStoreId": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
    }
  }'
```

### Delete Sheet Data
```bash
curl -X POST \
  'http://localhost:8080/excel-ingestion/v1/data/sheet/_delete?tenantId=pg.citya&referenceId=REF-2023-001&fileStoreId=f47ac10b-58cc-4372-a567-0e02b2c3d479' \
  -H 'Content-Type: application/json' \
  -d '{
    "apiId": "excel-ingestion",
    "ver": "1.0",
    "ts": 1690371438000,
    "userInfo": {
      "uuid": "11b0e02b-0145-4de2-bc42-c97b96264807"
    }
  }'
```