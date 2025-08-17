# Draft Campaign Data Retrieval Design

## Current Behavior
- Project-Factory only retrieves `completed` data when generating templates
- For draft campaigns, no data has `completed` status yet
- This results in empty templates for draft campaigns

## Problem Statement
When a campaign is in draft state:
- No data has `completed` status
- `getRelatedDataWithCampaign` with `dataRowStatuses.completed` returns empty results
- Generated templates are empty even though data exists in the sheet data table

## Solution Options

### Option 1: Modify Status Check Logic
Instead of only retrieving `completed` data, retrieve all available data for draft campaigns.

#### Implementation:
In generate flow classes, check campaign status and adjust query accordingly:
```typescript
// In facility-generateClass.ts
const campaignStatus = campaignDetails.status;
let facilitiesRow;

if (campaignStatus === campaignStatuses.drafted) {
    // For draft campaigns, get all data regardless of status
    facilitiesRow = await getRelatedDataWithCampaign(
        responseToSend.type, 
        campaignDetails.campaignNumber, 
        responseToSend?.tenantId
    );
} else {
    // For other statuses, maintain current behavior
    facilitiesRow = await getRelatedDataWithCampaign(
        responseToSend.type, 
        campaignDetails.campaignNumber, 
        responseToSend?.tenantId, 
        dataRowStatuses.completed
    );
}
```

### Option 2: Microplan-Specific Logic
Add specific logic for microplan campaigns that always retrieves all data.

#### Implementation:
```typescript
// Check if this is a microplan campaign
const isMicroplan = checkIfSourceIsMicroplan(campaignDetails);

if (isMicroplan) {
    // For microplan campaigns, always get all data
    facilitiesRow = await getRelatedDataWithCampaign(
        responseToSend.type, 
        campaignDetails.campaignNumber, 
        responseToSend?.tenantId
    );
} else {
    // For regular campaigns, maintain current behavior
    facilitiesRow = await getRelatedDataWithCampaign(
        responseToSend.type, 
        campaignDetails.campaignNumber, 
        responseToSend?.tenantId, 
        dataRowStatuses.completed
    );
}
```

### Option 3: Remove Status Filter Completely
Always retrieve all data regardless of status.

#### Implementation:
```typescript
// Always get all data regardless of campaign status
const facilitiesRow = await getRelatedDataWithCampaign(
    responseToSend.type, 
    campaignDetails.campaignNumber, 
    responseToSend?.tenantId
);
```

## Recommended Approach

### Option 2 (Microplan-Specific Logic) is Recommended

**Reasons:**
1. **Backward Compatibility**: Maintains existing behavior for non-microplan campaigns
2. **Targeted Solution**: Only affects microplan campaigns where this behavior is needed
3. **Logical Separation**: Microplan campaigns have different data flow requirements
4. **Minimal Risk**: Changes are isolated to specific use cases

## Implementation Plan

### 1. Modify Generate Flow Classes

#### facility-generateClass.ts
```typescript
// Before:
const completedFacilitiesRow = await getRelatedDataWithCampaign(
    responseToSend.type, 
    campaignDetails.campaignNumber, 
    responseToSend?.tenantId, 
    dataRowStatuses.completed
);

// After:
const isMicroplan = campaignDetails?.additionalDetails?.source === "microplan";
let facilitiesRow;

if (isMicroplan) {
    // For microplan campaigns, get all data
    facilitiesRow = await getRelatedDataWithCampaign(
        responseToSend.type, 
        campaignDetails.campaignNumber, 
        responseToSend?.tenantId
    );
} else {
    // For regular campaigns, get only completed data
    facilitiesRow = await getRelatedDataWithCampaign(
        responseToSend.type, 
        campaignDetails.campaignNumber, 
        responseToSend?.tenantId, 
        dataRowStatuses.completed
    );
}
```

#### user-generateClass.ts
```typescript
// Before:
const users = await getRelatedDataWithCampaign(
    type, 
    campaignNumber, 
    tenantId, 
    dataRowStatuses.completed
);

// After:
const isMicroplan = campaignDetails?.additionalDetails?.source === "microplan";
let users;

if (isMicroplan) {
    // For microplan campaigns, get all data
    users = await getRelatedDataWithCampaign(
        type, 
        campaignNumber, 
        tenantId
    );
} else {
    // For regular campaigns, get only completed data
    users = await getRelatedDataWithCampaign(
        type, 
        campaignNumber, 
        tenantId, 
        dataRowStatuses.completed
    );
}
```

### 2. Update Other Generate Classes
Apply similar changes to:
- boundary-generateClass.ts
- userCredential-generateClass.ts
- Any other relevant generate classes

## Benefits of This Approach

1. **Draft Campaign Support**: Users can generate pre-filled templates for draft microplan campaigns
2. **Backward Compatibility**: Existing campaigns continue to work as before
3. **Performance**: No performance impact on non-microplan campaigns
4. **Maintainability**: Clear separation of logic based on campaign type

## Testing Considerations

1. **Draft Campaigns**: Verify that draft microplan campaigns generate pre-filled templates
2. **Completed Campaigns**: Ensure completed regular campaigns still work correctly
3. **Mixed Status Data**: Test campaigns with both completed and pending data
4. **Non-Microplan Campaigns**: Confirm regular campaigns are unaffected

## Rollout Strategy

1. **Development**: Implement changes in generate flow classes
2. **Testing**: Test with both draft and completed campaigns
3. **Staging**: Deploy to staging environment for validation
4. **Production**: Deploy with monitoring for any issues