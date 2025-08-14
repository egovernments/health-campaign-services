# Excel Ingestion Service - TODO

## Model Usage Analysis and Implementation

### 1. Analyze Objects/Maps That Need Model Classes  
- **Status**: Completed
- **Priority**: High
- **Description**: Analysis completed - identified 8 files using Map<String, Object> that need model classes. Migration plan created with 5 phases.

## 5-Phase Migration Plan Implementation

### 2. Phase 1: MDMS Integration Models
- **Status**: Pending
- **Priority**: High
- **Description**:
  - Create MDMSResponse, MDMSData, MDMSCriteria model classes
  - Update MDMSService.searchMDMS() method to use typed models instead of Map<String, Object>
  - Modify callers in MicroplanProcessor to use model methods
  - Add JSON parsing logic for structured MDMS data
  - Test MDMS API integration with new models

### 3. Phase 2: Campaign Configuration Models
- **Status**: Pending  
- **Priority**: High
- **Description**:
  - Create CampaignConfig, ConfigSection, ConfigColumn model classes
  - Refactor campaign config processing logic in CampaignConfigSheetCreator
  - Update MicroplanProcessor campaign config parsing to use models
  - Add model conversion from MDMS response to structured config objects
  - Test with real campaign configuration data

### 4. Phase 3: API Payload Models
- **Status**: Pending
- **Priority**: Medium
- **Description**:
  - Create request-specific models for boundary, hierarchy services
  - Create BoundaryHierarchyRequest, BoundaryRelationshipRequest models
  - Update ApiPayloadBuilder to use typed requests instead of Map<String, Object>
  - Modify service methods to use typed requests
  - Ensure JSON serialization works correctly

### 5. Phase 4: File Store Models
- **Status**: Pending
- **Priority**: Low
- **Description**:
  - Create FileStoreResponse, FileInfo model classes
  - Update FileStoreService.uploadFile() to use models
  - Handle file upload response parsing with proper models
  - Add file metadata handling through structured models

### 6. Phase 5: Error Response Models
- **Status**: Pending
- **Priority**: Low  
- **Description**:
  - Enhance existing error handling with dedicated models
  - Create ErrorResponse, ErrorDetail model classes (if needed beyond current implementation)
  - Ensure error response consistency with health services pattern
  - Update GlobalExceptionHandler to use structured error models