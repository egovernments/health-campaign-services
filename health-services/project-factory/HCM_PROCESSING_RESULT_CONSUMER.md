# HCM Processing Result Consumer

## Overview
Added a new Kafka consumer in the project-factory service to consume messages from the `hcm-processing-result` topic. This topic receives processing results from the excel-ingestion service when Excel files are processed with type `unified-console-parse`.

## Changes Made

### 1. Configuration Update
**File**: `src/server/config/index.ts`
- Added new Kafka topic configuration:
  ```typescript
  KAFKA_HCM_PROCESSING_RESULT_TOPIC: process.env.KAFKA_HCM_PROCESSING_RESULT_TOPIC || "hcm-processing-result"
  ```

### 2. Kafka Listener Update
**File**: `src/server/kafka/Listener.ts`
- Added `hcm-processing-result` topic to the list of subscribed topics
- Added case handler for the new topic in the message processor

### 3. Processing Result Handler
**File**: `src/server/utils/processingResultHandler.ts` (New file)
- Created handler function `handleProcessingResult` that logs:
  - Processing ID, Tenant ID, Type, and Status
  - Input and Processed File Store IDs
  - Audit details (created/modified times and users)
  - Additional details including error information
  - Sheet error counts if processing had validation errors

## Message Flow

1. Excel-ingestion service processes Excel with type `unified-console-parse`
2. After processing completes, it publishes `ProcessResource` to `hcm-processing-result` topic
3. Project-factory consumer receives the message
4. Handler logs all processing details for monitoring

## Message Structure
The consumer receives a `ProcessResource` object containing:
```javascript
{
  id: "processing-id",
  tenantId: "tenant-id",
  type: "unified-console-parse",
  status: "COMPLETED" | "FAILED",
  fileStoreId: "input-file-id",
  processedFileStoreId: "output-file-id",
  auditDetails: {
    createdTime: timestamp,
    lastModifiedTime: timestamp,
    createdBy: "user-uuid",
    lastModifiedBy: "user-uuid"
  },
  additionalDetails: {
    // Error information if failed
    errorCode: "ERROR_CODE",
    errorMessage: "Error description",
    // Sheet error counts
    sheetErrorCounts: {
      "SheetName": errorCount
    }
  }
}
```

## Environment Variables
- `KAFKA_HCM_PROCESSING_RESULT_TOPIC`: Override default topic name (default: `hcm-processing-result`)

## Testing
To test the consumer:
1. Start the project-factory service
2. Process an Excel file through excel-ingestion with type `unified-console-parse`
3. Check project-factory logs for the processing result output

## Future Enhancements
The current implementation only logs the processing results. Based on requirements, you can extend the handler to:
- Store processing results in database
- Trigger downstream workflows
- Send notifications on processing completion
- Update campaign status based on processing results