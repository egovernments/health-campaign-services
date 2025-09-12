# Excel Ingestion Parsing Design Document

## Overview
This document outlines the architectural design for configurable parsing persistence and completion notification system. The design enables granular control over sheet processing behavior through configuration-driven approach, supporting both validation-only and persistent workflows with optional event publishing.

## Design Principles

### 1. Configuration-Driven Architecture
- **Declarative Configuration**: Define behavior through configuration rather than code
- **Sheet-Level Granularity**: Each sheet can have independent persistence and notification settings
- **Backward Compatibility**: Existing configurations continue to work without changes

### 2. Separation of Concerns
- **Parsing Logic**: Extract and validate data from sheets
- **Persistence Layer**: Conditionally store data based on configuration
- **Event Publishing**: Asynchronously notify downstream systems
- **Error Handling**: Isolate failures to individual sheets

### 3. Extensibility Patterns
- **Plugin Architecture**: Support custom processors per sheet type
- **Event-Driven Design**: Loosely coupled integration through Kafka topics
- **Configuration Registry**: Centralized configuration management

## System Architecture

### Configuration Layer
```
ProcessorConfigurationRegistry
├── Sheet Configuration Mapping
│   ├── sheetNameKey (Sheet Identifier)
│   ├── schemaName (Validation Schema)
│   ├── processorClass (Custom Processing Logic)
│   ├── persistParsings (Storage Behavior Flag)
│   └── triggerParsingCompleteTopic (Event Publishing Target)
└── Default Behavior Definition
```

### Processing Flow Architecture
```
Request Input
    ↓
Configuration Resolution
    ↓
Sheet Processing Pipeline
    ├── Parse & Validate Data
    ├── Apply Custom Processors (Optional)
    ├── Conditional Persistence
    └── Event Publishing (Optional)
    ↓
Response Generation
```

## Configuration Behavior Patterns

### Persistence Control Pattern
- **Full Persistence Mode**: `persistParsings = true`
  - Parse → Validate → Store → Complete
  - Used for production data ingestion workflows
  
- **Validation-Only Mode**: `persistParsings = false`
  - Parse → Validate → Discard → Complete
  - Used for data quality checks and preview scenarios

### Event Publishing Patterns
- **Silent Processing**: `triggerParsingCompleteTopic = null`
  - No downstream notifications
  - Self-contained processing workflow
  
- **Event-Driven Integration**: `triggerParsingCompleteTopic = "topic.name"`
  - Publishes completion events to Kafka topic
  - Enables workflow orchestration and monitoring

## Event Message Design

### Message Structure Philosophy
- **Minimal Payload**: Contains only essential identifiers for downstream processing
- **Stateless Design**: No processing state or business data in messages
- **Correlation Support**: Enables request tracking and workflow correlation

### Event Schema Pattern
```
ParsingCompleteEvent {
    filestoreId: String     // File reference for data retrieval
    referenceId: String     // Request correlation identifier  
    sheetName: String       // Specific sheet identifier
    recordCount: Integer    // Number of rows processed
}
```

### Integration Patterns
- **Fire-and-Forget**: Async publishing without delivery guarantees
- **Topic-Based Routing**: Different sheets can publish to different topics
- **Consumer Flexibility**: Downstream services choose their subscription model

## Component Architecture

### 1. Configuration Management Layer
- **ProcessorConfigurationRegistry**: Central configuration repository
- **Configuration Validation**: Ensures valid sheet and topic configurations
- **Default Value Management**: Provides backward compatibility

### 2. Processing Orchestration Layer
- **ConfigBasedProcessingService**: Coordinates sheet processing workflow
- **Conditional Logic**: Applies persistence and event publishing based on configuration
- **Error Isolation**: Prevents individual sheet failures from affecting others

### 3. Event Publishing Layer
- **ParsingEventPublisher**: Handles Kafka message publishing
- **Topic Management**: Routes events to configured destinations
- **Async Processing**: Non-blocking event delivery

## Processing Flow Patterns

### Standard Processing Workflow
```
Excel File Input
    ↓
Configuration Lookup (by processor type)
    ↓
For Each Sheet:
    ├── Resolve Sheet Configuration
    ├── Parse & Validate Data
    ├── Apply Custom Processor (if configured)
    ├── Conditional Persistence Decision
    │   ├── [TRUE] → Persist to Database
    │   └── [FALSE] → Skip Persistence
    ├── Event Publishing Decision
    │   ├── [Topic Configured] → Publish Event
    │   └── [No Topic] → Skip Publishing
    └── Continue to Next Sheet
    ↓
Complete Processing
```

### Error Handling Flow
```
Processing Error Encountered
    ↓
Log Error Details
    ↓
Update Process Status (for processSearch polling)
```

## Data Storage Design

### Temporary Sheet Data Storage

#### Table: ex_in_sheet_data_temp

**Purpose**: Temporary storage for parsed sheet data during processing workflow

**Schema Design**:
```sql
CREATE TABLE eg_ex_in_sheet_data_temp (
    referenceId         VARCHAR(100)    NOT NULL,
    fileStoreId         VARCHAR(100)    NOT NULL,
    sheetName           VARCHAR(100)    NOT NULL,
    rowNumber           INTEGER         NOT NULL,
    rowJson             JSONB           NOT NULL,
    createdBy           VARCHAR(100)    NOT NULL,
    createdTime         BIGINT          NOT NULL,
    deleteTime          BIGINT          NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000 + 86400000),
    
    PRIMARY KEY (referenceId, fileStoreId, sheetName, rowNumber)
);
```

**Column Definitions**:

| Column | Type | Description |
|--------|------|-------------|
| referenceId | VARCHAR(100) | Request correlation identifier from ProcessResource |
| fileStoreId | VARCHAR(100) | File reference from ProcessResource |
| sheetName | VARCHAR(100) | Name of the Excel sheet being processed |
| rowNumber | INTEGER | Row number within the sheet (1-based) |
| rowJson | JSONB | Parsed row data as JSON object with column headers as keys |
| createdBy | VARCHAR(100) | User identifier who initiated the processing |
| createdTime | BIGINT | Creation timestamp (epoch milliseconds) |
| deleteTime | BIGINT | Auto-deletion timestamp (epoch milliseconds, defaults to 1 day after creation) |

**Primary Key Strategy**:
- **Composite Key**: (referenceId, fileStoreId, sheetName, rowNumber)
- **Uniqueness**: Ensures each row within a sheet for a specific processing request is unique
- **Performance**: Enables efficient querying by processing context

**Data Storage Patterns**:

1. **Row-Level Granularity**: Each Excel row stored as separate database record
2. **JSON Flexibility**: Column data stored as JSONB for schema flexibility
3. **Audit Trail**: Full audit details for tracking processing lifecycle
4. **Auto-Cleanup Strategy**: Data automatically marked for deletion 1 day after creation via deleteTime column

**Query Patterns**:
```sql
-- Retrieve all data for a specific sheet
SELECT * FROM eg_ex_in_sheet_data_temp 
WHERE referenceId = ? AND fileStoreId = ? AND sheetName = ?
ORDER BY rowNumber;

-- Count records per sheet
SELECT sheetName, COUNT(*) as recordCount 
FROM eg_ex_in_sheet_data_temp 
WHERE referenceId = ? AND fileStoreId = ?
GROUP BY sheetName;

-- Cleanup expired records (cron job at 12:00 AM daily)
DELETE FROM eg_ex_in_sheet_data_temp 
WHERE deleteTime < EXTRACT(EPOCH FROM NOW()) * 1000;

-- Manual cleanup after processing (optional)
DELETE FROM eg_ex_in_sheet_data_temp 
WHERE referenceId = ? AND fileStoreId = ?;
```

**Integration Points**:
- **Persistence Control**: Only populated when `persistParsings = true` in configuration
- **Event Publishing**: Row count used in ParsingCompleteEvent messages
- **Error Recovery**: Enables reprocessing failed rows without re-parsing Excel file
- **Downstream Processing**: Provides structured data access for consuming services

**Cleanup Strategy**:
- **Auto-Deletion**: `deleteTime` column defaults to 1 day (86400000ms) after creation
- **Cron Job**: Daily cleanup job runs at 12:00 AM to delete expired records
- **Query**: `DELETE FROM eg_ex_in_sheet_data_temp WHERE deleteTime < current_timestamp_millis`
- **Retention**: Data available for 24 hours for debugging and reprocessing scenarios
