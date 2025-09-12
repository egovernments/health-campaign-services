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
