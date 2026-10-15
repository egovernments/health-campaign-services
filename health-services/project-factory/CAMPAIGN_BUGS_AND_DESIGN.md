# Campaign Creation — Bug Inventory & Design Notes

> Branch: `nigeria-go-deep-fcm`  
> Last updated: 2026-05-07

---

## Symptom

Campaign creation always fails when uploading a large user sheet.  
Error shown to the user: **"Unified console template is not valid. Please correct the errors and try again."**  
This error is misleading — the real failure is a timeout or batch-processing error, not a validation problem.

---

## Bug Inventory

| # | Description | File | Status |
|---|-------------|------|--------|
| 1 | Misleading error on processing failure | `processingResultHandler.ts:210` | ✅ Fixed in earlier refactor |
| 2 | `processErrors` crashes on `error.row = undefined` | `userValidation-processClass.ts` | ✅ Fixed |
| 3 | Shared `transformConfigs` singleton mutated across Kafka consumers | `userBatchHandler.ts:106` | ✅ Fixed |
| 3b | Same shallow-copy bug in facility handler | `facilityBatchHandler.ts:66` | ✅ Fixed |
| 4 | `Promise.all` for user batches — one failure cancels all | `userBatchHandler.ts` (batch loop) | ✅ Already uses per-item isolation |
| 5 | No campaign-failed guard before creating users | `userBatchHandler.ts` | ✅ Fixed |
| 6 | `checkCampaignDataCompletionStatus` counts ALL types — boundary failure cascades to kill user creation | `genericUtils.ts:1898` | ✅ Fixed (optional `type` param) |
| 7 | Blind sleep `Math.max(5000, n*8)` in `persistDataInBatches` | `processingResultHandler.ts` | ✅ Fixed (replaced with `pollUntilCount`) |
| 8 | Ghost users on retry: users created in HRMS while campaign shows FAILED → next attempt gets "user already exists" | `userBatchHandler.ts` | ✅ Fixed (idempotency pre-check) |
| 9 | `axios timeout: 0` — infinite hang when any downstream service is unresponsive | `request.ts:10` | ✅ Fixed (300s default, override via `HTTP_TIMEOUT_MS` env var) |

---

## Fix Details

### Fix 2 — `processErrors` null/bounds guard
**File:** `src/server/processFlowClasses/userValidation-processClass.ts`

Added guard before `error.row - 3` index arithmetic:
```typescript
if (error.row == null) {
    logger.warn(`Skipping error without row number: ${error.message}`);
    continue;
}
const row = error.row - 3;
if (row < 0 || row >= sheetData.length) {
    logger.warn(`Row index ${row} out of bounds for error: ${error.message}`);
    continue;
}
```

### Fix 3 — Deep copy `transformConfigs` singleton
**File:** `src/server/utils/userBatchHandler.ts:106`  
**File:** `src/server/utils/facilityBatchHandler.ts:66`

Changed:
```typescript
// Before (mutates module-level singleton — concurrent Kafka consumers corrupt each other)
const transformConfig = transformConfigs?.["employeeHrmsUnified"];

// After
const transformConfig = JSON.parse(JSON.stringify(transformConfigs?.["employeeHrmsUnified"]));
```

Without the deep copy, concurrent Kafka consumers all write their own `tenantId`/`hierarchyType` into the same object. The last writer wins, causing random cross-tenant data corruption.

### Fix 5 — Campaign-failed guard
**File:** `src/server/utils/userBatchHandler.ts`

```typescript
if (campaignDetails.status === campaignStatuses.failed) {
    logger.warn(`Campaign ${campaignId} is already failed. Skipping user batch ${batchNumber}/${totalBatches}`);
    return;
}
```

### Fix 6 — Type-filtered `checkCampaignDataCompletionStatus`
**File:** `src/server/utils/genericUtils.ts:1898`

Added optional `type?: string` parameter:
```typescript
export async function checkCampaignDataCompletionStatus(campaignNumber: string, tenantId: string, type?: string) {
    const queryParams: any[] = [campaignNumber];
    const typeFilter = type ? `AND type = $${queryParams.push(type)}` : '';
    const queryString = `
      SELECT status, COUNT(*) as count
      FROM ${tableName}
      WHERE campaignNumber = $1 ${typeFilter}
      GROUP BY status
    `;
```

Without this, a boundary failure would mark the entire campaign as failed even if all users completed successfully, because the status check aggregated ALL types.

### Fix 7 — Replace blind sleep with `pollUntilCount`
**File:** `src/server/utils/processingResultHandler.ts` and `genericUtils.ts`

New utility in `genericUtils.ts`:
```typescript
export async function pollUntilCount<T>(
  fetchFn: () => Promise<T[] | null>,
  expectedCount: number,
  options: { timeoutMs?: number; pollIntervalMs?: number; label?: string } = {}
): Promise<T[]> {
  const { timeoutMs = 120_000, pollIntervalMs = 1_000, label = 'data' } = options;
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const result = await fetchFn();
    if ((result?.length ?? 0) >= expectedCount) return result as T[];
    await new Promise(r => setTimeout(r, pollIntervalMs));
  }
  throw new Error(`Persistence timeout: ${label} not ready within ${timeoutMs}ms`);
}
```

Replaced two blind sleeps in `processingResultHandler.ts`:
1. Initial wait after ingestion — now polls `searchSheetData` until `totalRowsProcessed` rows are visible
2. `persistDataInBatches` — all inter-batch and post-batch sleeps removed

### Fix 8 — Idempotency: handle "user already exists" on retry
**File:** `src/server/utils/userBatchHandler.ts`

Added `fetchExistingUsersByPhone` pre-check before calling HRMS:
```typescript
const alreadyExistingMap = await fetchExistingUsersByPhone(uniqueIdentifiers, tenantId, requestInfo);
const phoneNumbersNeedingCreation = uniqueIdentifiers.filter(p => !alreadyExistingMap[String(p)]);

// Mark already-existing users as completed immediately
uniqueIdentifiers.forEach(uniqueIdentifier => {
    const existing = alreadyExistingMap[String(uniqueIdentifier)];
    if (existing) {
        campaignRecord.status = dataRowStatuses.completed;
        campaignRecord.uniqueIdAfterProcess = existing.serviceUuid;
    }
});
```

Flow:
1. Search Individual service for all phone numbers in the batch
2. Users already in HRMS → mark `completed`, skip HRMS call
3. Only call HRMS for new users
4. Merge existing entries into `createResult` so worker registry and campaign data update treat all users uniformly

This makes `handleUserBatch` safe to retry after a campaign failure.

### Fix 9 — HTTP timeout
**File:** `src/server/utils/request.ts:10`

```typescript
// Before
timeout: 0, // Set timeout to 0 to wait indefinitely

// After
// Default 5-minute timeout; use HTTP_TIMEOUT_MS=0 env var to disable (file downloads).
timeout: Number(process.env.HTTP_TIMEOUT_MS ?? 300_000),
```

The `timeout: 0` was set when Redis caching for large file downloads was introduced (commit "added timeout #1095"). A hung downstream microservice would block a Kafka consumer thread forever, preventing other messages from being processed. 5 minutes is enough for any legitimate API response; set `HTTP_TIMEOUT_MS=0` in the deployment if genuinely needed.

---

## Architecture: Why Campaign Fails at 30k Users

### Current data flow
```
Excel upload → excel-ingestion service → validates + persists rows
  ↓ Kafka: hcm-processing-result
handleProcessingResult
  ↓ reads all campaign data from DB
  ↓ splits into batches of 30 users
  ↓ publishes N Kafka messages (hcm-user-create-batch)
  ↓ pollUntilCount waits for user creation to finish (max 120s)
  ↓ monitorCampaignDataCompletion polls DB every 40s (max 133 min)
```

### Bottlenecks at 30k scale
| Issue | Impact |
|-------|--------|
| 30k ÷ 30 = 1000 Kafka messages, processed 10 at a time | ~100 rounds × (HRMS latency + Individual search) |
| Each batch calls IDGen for usernames (N sequential calls inside `transformBulkEmployee`) | Sequential bottleneck per batch |
| Each batch searches boundary data (same data fetched 1000 times) | Redundant I/O |
| `monitorCampaignDataCompletion` polls DB every 40s for 133 min max | Fine for 30k but wasteful |

### What would break at 30k
- `pollUntilCount` in `createUsersFromUserData` has a 120s timeout — may not be enough if 1000 Kafka messages take longer than 2 min to consume
- Memory: 30k rows loaded into Node.js heap once (acceptable ~60 MB)
- HRMS bulk employee create: no internal cap confirmed — handles batch of 30 fine

### Recommended improvements for 30k (not yet implemented)
1. **Increase batch size**: Change `BATCH_SIZE = 30` → `100`, `MAX_CONCURRENT = 10` → `20`
2. **Pre-fetch boundary data once** before batching (save 1000 redundant calls)
3. **Bulk IDGen**: Call IDGen for all usernames before splitting into batches
4. **Extend `pollUntilCount` timeout** in `createUsersFromUserData` from 120s → 600s for large campaigns

---

## Pending / Not Yet Implemented

| Item | File | Notes |
|------|------|-------|
| ExcelJS workbook memory leak | `excelUtils.ts` | `workbook.destroy()` not called after processing; workbook stays in memory for full background run |
| Retry with exponential backoff in `handleUserBatch` | `userBatchHandler.ts` | Currently fails immediately; dead-letter queue would prevent ghost users |
| Extend completion poll timeout for large campaigns | `processingResultHandler.ts` | `timeoutMs: 120_000` in `createUsersFromUserData` is too short for 30k users |
| Batch size increase | Kafka consumer config | `BATCH_SIZE=30`, `MAX_CONCURRENT=10` — safe to increase to 100/20 |
| Pre-fetch & cache boundary data before user batching | `processingResultHandler.ts` | Would eliminate 1000 redundant `searchBoundaryRelationshipData` calls |

---

## Key File Locations

| What | Where |
|------|-------|
| Main Kafka result handler | `src/server/utils/processingResultHandler.ts` |
| User batch Kafka handler | `src/server/utils/userBatchHandler.ts` |
| Facility batch Kafka handler | `src/server/utils/facilityBatchHandler.ts` |
| User sheet validation | `src/server/processFlowClasses/userValidation-processClass.ts` |
| DB polling utilities | `src/server/utils/genericUtils.ts` (`pollUntilCount`, `checkCampaignDataCompletionStatus`) |
| HTTP client (timeout config) | `src/server/utils/request.ts` |
| Transform configs (singleton) | `src/server/config/transformConfigs.ts` |
| Kafka consumer (concurrency) | `src/server/kafka/Listener.ts` |
| Campaign/process status constants | `src/server/config/constants.ts` |
