# Excel Ingestion Service - TODO

## Campaign Configuration Sheet Improvements

### 1. Refactor Campaign Config Creation Logic
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

### 2. Improve Error Handling - Align with Health Services Pattern
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