# Excel Ingestion Parsing Design Document

## Overview
Enhancement to existing `ProcessorSheetConfig` to support configurable parsing persistence and completion notification on a per-sheet basis.

## Current Structure
The existing `ProcessorConfigurationRegistry.ProcessorSheetConfig` currently has:
```java
public static class ProcessorSheetConfig {
    private final String sheetNameKey;
    private final String schemaName;
    private final String processorClass;
}
```

## Enhanced Configuration Structure

### Enhanced ProcessorSheetConfig
Add two new fields to the existing `ProcessorSheetConfig`:

```java
public static class ProcessorSheetConfig {
    private final String sheetNameKey;
    private final String schemaName;
    private final String processorClass;
    private final boolean persistParsings;           // NEW FIELD
    private final String triggerParsingCompleteTopic; // NEW FIELD
    
    // Enhanced constructors
    public ProcessorSheetConfig(String sheetNameKey, String schemaName) {
        this(sheetNameKey, schemaName, null, true, null);
    }
    
    public ProcessorSheetConfig(String sheetNameKey, String schemaName, String processorClass) {
        this(sheetNameKey, schemaName, processorClass, true, null);
    }
    
    public ProcessorSheetConfig(String sheetNameKey, String schemaName, 
                              String processorClass, boolean persistParsings, 
                              String triggerParsingCompleteTopic) {
        this.sheetNameKey = sheetNameKey;
        this.schemaName = schemaName;
        this.processorClass = processorClass;
        this.persistParsings = persistParsings;
        this.triggerParsingCompleteTopic = triggerParsingCompleteTopic;
    }
}
```

### Example Configuration Usage
```java
private void initializeConfigurations() {
    // Microplan processor configuration
    configs.put("microplan-ingestion", Arrays.asList(
        new ProcessorSheetConfig("HCM_ADMIN_CONSOLE_FACILITIES_LIST", 
                                "facility-microplan-ingestion", 
                                null, 
                                true,  // persist parsings
                                "facility.parsing.complete"), // trigger topic
                                
        new ProcessorSheetConfig("HCM_ADMIN_CONSOLE_USERS_LIST", 
                                "user-microplan-ingestion",
                                null,
                                false, // don't persist - validation only
                                "user.parsing.complete"),
                                
        new ProcessorSheetConfig("HCM_CONSOLE_BOUNDARY_HIERARCHY", 
                                null, 
                                "org.egov.excelingestion.processor.BoundaryHierarchyTargetProcessor",
                                true,  // persist parsings
                                null)  // no topic notification
    ));
}
```

## Configuration Parameters

### `persistParsings` (boolean)
- **Default**: `true` (maintains backward compatibility)
- **Purpose**: Controls whether parsed sheet data is persisted to database
- **Usage**:
  - `true`: Save parsed data to database
  - `false`: Parse and validate only, don't persist

### `triggerParsingCompleteTopic` (String, nullable)
- **Default**: `null` (maintains backward compatibility)
- **Purpose**: Kafka topic name for publishing parsing completion events
- **Usage**:
  - If topic specified: Publish completion message after sheet processing
  - If `null`: No message published

## Message Structure

### Parsing Complete Event
```java
public class ParsingCompleteEvent {
    private String filestoreId;
    private String referenceId;
    private String sheetName;
}
```

### JSON Message Format
```json
{
  "filestoreId": "uuid-of-uploaded-file",
  "referenceId": "reference-id-from-request", 
  "sheetName": "HCM_ADMIN_CONSOLE_FACILITIES_LIST"
}
```

## Implementation Components

### 1. Message Publisher Service
```java
@Component
public class ParsingEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public ParsingEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    public void publishParsingComplete(String topic, ParsingCompleteEvent event) {
        if (topic != null && !topic.trim().isEmpty()) {
            kafkaTemplate.send(topic, event);
            log.info("Published parsing complete event to topic: {}", topic);
        }
    }
}
```

### 2. Enhanced ConfigBasedProcessingService
Modify existing methods to handle new configuration:

```java
public void processSheetWithConfig(ProcessorSheetConfig config, 
                                 Sheet sheet, 
                                 ProcessResource resource, 
                                 RequestInfo requestInfo) {
    // Parse sheet data
    List<Object> parsedData = parseSheetData(sheet);
    
    // Persist if configured
    if (config.isPersistParsings()) {
        persistDataToDatabase(parsedData);
    }
    
    // Publish completion event if configured
    if (config.getTriggerParsingCompleteTopic() != null) {
        ParsingCompleteEvent event = createCompletionEvent(
            resource.getFilestoreId(),
            resource.getReferenceId(), 
            sheet.getSheetName()
        );
        parsingEventPublisher.publishParsingComplete(
            config.getTriggerParsingCompleteTopic(), 
            event
        );
    }
}
```

## Processing Flow

### Sheet Processing Logic
```
For Each Sheet:
  1. Parse sheet data
  2. If config.persistParsings == true → Save to database
  3. If config.triggerParsingCompleteTopic != null → Publish event
  4. Continue to next sheet
```

### Error Handling
```
Parse Error Occurred:
  1. If triggerParsingCompleteTopic configured → Publish completion event
  2. Continue processing (don't fail entire batch)
```

## Benefits

### 1. Granular Control
- Configure each sheet independently for persistence and notifications
- Mix validation-only and persistence sheets in same workbook

### 2. Backward Compatibility  
- All existing configurations work unchanged
- Default values maintain current behavior

### 3. Integration Flexibility
- Downstream services can subscribe to specific sheet completion events
- Enable workflow triggers based on sheet processing

### 4. Performance Optimization
- Skip database writes for validation-only sheets
- Reduce processing time and database load

## Migration Path

### Phase 1: Add New Fields
- Extend `ProcessorSheetConfig` with new optional fields
- Add default constructors maintaining backward compatibility

### Phase 2: Implement Publisher
- Create `ParsingEventPublisher` service
- Add Kafka template configuration

### Phase 3: Update Processing Logic
- Modify `ConfigBasedProcessingService` to use new configuration
- Add conditional persistence and publishing logic

### Phase 4: Update Configurations
- Update specific processor configurations to use new features
- Test with validation-only and topic publishing scenarios