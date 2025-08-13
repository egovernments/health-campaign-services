# Excel Ingestion Service - TODO

## Campaign Configuration Sheet Improvements

### 1. Analyze and Fix Localization in Campaign Config Sheet
- **Status**: Pending
- **Priority**: High
- **Description**: 
  - Analyze what text elements are currently localized vs not localized in the campaign configuration sheet
  - Check if all keys from MDMS response are being properly localized:
    - Section titles (HCM_CAMP_CONF_BOUNDARY_LEVELS, HCM_CAMP_CONF_CAMPAIGN_DETAILS)
    - Column headers (HCM_CAMP_CONF_BOUNDARY_LEVEL, HCM_CAMP_CONF_BOUNDARY_LEVEL_NAME, etc.)
    - Row labels (HCM_CAMP_CONF_LEVEL_0 through HCM_CAMP_CONF_LEVEL_8, campaign detail keys)
    - Header info text (HCM_CAMP_CONF_HEADER_INFO)
    - Sheet name (HCM_CAMP_CONF_SHEETNAME)
  - Identify missing localizations and add proper localization calls
  - Ensure consistent localization pattern across all campaign config elements

### 2. Create Generic MDMS Utility Function
- **Status**: Pending  
- **Priority**: Medium
- **Description**:
  - Create a centralized MDMS utility function that can handle all MDMS search operations
  - Consolidate existing MDMS calls:
    - `fetchSchemaFromMDMS()` - for facility/user schemas
    - `fetchCampaignConfigFromMDMS()` - for campaign config
    - Any other MDMS calls in the codebase
  - Design generic method signature that can handle different:
    - Schema codes
    - Filter criteria
    - Response parsing logic
  - Move to a dedicated utility class (e.g., `MDMSUtil`) or enhance existing `ApiPayloadBuilder`
  - Ensure error handling and fallback mechanisms are preserved

### 3. Refactor Campaign Config Creation Logic
- **Status**: Pending
- **Priority**: Medium  
- **Description**:
  - Review and refactor campaign configuration sheet creation logic for better maintainability
  - Areas to examine:
    - `CampaignConfigSheetCreator.createSection()` method - check if it can be simplified
    - Row/column positioning logic - ensure it's robust and easy to understand
    - Style application logic - consolidate repetitive styling code
    - Error handling in sheet creation process
    - Memory efficiency for large config data
  - Consider extracting common patterns into helper methods
  - Add comprehensive logging for debugging
  - Ensure the code follows existing patterns from other sheet creators
  - Validate that sheet ordering (campaign config as first sheet) works correctly in all scenarios

### 4. Improve Error Handling - Align with Health Services Pattern
- **Status**: Pending
- **Priority**: High
- **Description**:
  - Analyze error handling patterns used in other health services:
    - project-service
    - facility-service  
    - individual-service

  - Check how these services handle:
    - API call failures
    - Service integration errors
    - Data validation errors
    - Configuration issues
  - Use the exact same error handling format and logic as other health services
  - Review current excel-ingestion error handling where errors are just logged and execution continues
  - Replace inconsistent error handling with health services standard pattern:
    - Same exception types and hierarchy
    - Same error response format
    - Same logging patterns
    - Same error propagation mechanism
  - Ensure consistency across the entire health-campaign-services ecosystem