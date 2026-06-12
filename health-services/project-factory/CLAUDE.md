# project-factory

> **Self-healing**: update this file after every task if service patterns/contracts changed. Remove stale content. Keep <500 lines.

---

## Priority Stack

```
1. TYPE SAFETY   — branded types, no any/null/undefined primitives, explicit return types
2. SCALABILITY   — stateless handlers, bounded parallelism, no unbounded module-level state
3. CORRECTNESS   — idempotent ops, exact Kafka/DB contracts
4. SIMPLICITY    — one job per function, max ~40 lines, no premature abstraction
5. PERFORMANCE   — O(n) by design, O(n²) forbidden, chunk all large arrays
```

Higher priority wins. State the trade-off explicitly when they conflict.

---

## Business Context

- Provisions boundaries, facilities, users, attendance registers across DIGIT HCM services via Kafka.
- Accepts 50k-row Excel uploads → validates per-row → writes back row-level status annotations.
- Generates pre-populated Excel templates for download.
- HTTP layer triggers async jobs only — all heavy work is Kafka-driven.
- Campaigns run in low-connectivity health contexts. Silent data loss or wrong tenant routing is life-critical.

### What It Creates
| Entity | Created via | Purpose |
|---|---|---|
| Boundary entities + relationships | `boundary-service` | Geographic admin units (country → state → district → village) |
| Health projects | `health-project` | One project per boundary node scoped to the campaign |
| Project staff | `health-project/staff` | Assigns users (field workers, supervisors) to projects |
| Project facilities | `health-project/facility` | Assigns facilities (health posts, storage) to projects |
| Project resources | `health-project/resource` | Assigns product variants (commodities) to projects |
| Facilities | `facility` service | Physical locations used during the campaign |
| HRMS employees | `health-hrms` | User records for campaign field staff |
| Individuals | `health-individual` | Beneficiary records looked up during mapping |
| Attendance registers | `attendance` service | Training session containers per project |
| Attendance register attendees | `attendance` service | Field workers enrolled in a training register |
| Localization messages | `localization` service | Excel column headers and UI labels for each locale |

### External Services Used
| Service | Config key | Usage |
|---|---|---|
| `boundary-service` | `host.boundaryHost` | Create/search boundaries and hierarchy definitions |
| `health-project` | `host.projectHost` | Create/update/search projects, staff, facilities, resources |
| `facility` | `host.facilityHost` | Create and search campaign facility records |
| `health-hrms` | `host.hrmsHost` | Create and search field staff (employees) |
| `egov-user` | `host.userHost` | Search existing user accounts |
| `health-individual` | `host.healthIndividualHost` | Search individual records for mapping validation |
| `egov-mdms-service` (v1+v2) | `host.mdms` / `host.mdmsV2` | Fetch campaign type schema, hierarchy config, master data |
| `egov-localization` | `host.localizationHost` | Search and upsert localization keys for Excel templates |
| `egov-idgen` | `host.idGenHost` | Generate campaign number identifiers |
| `egov-filestore` | `host.filestore` | Upload/download Excel files (templates + status files) |
| `excel-ingestion` | `host.excelIngestionHost` | Delegate large Excel parsing to the ingestion service |
| `attendance` | `host.attendanceHost` | Create attendance registers and enroll attendees |
| `plan-service` | `host.planServiceHost` | Microplan integration — fetch plan facility assignments |
| `census-service` | `host.censusServiceHost` | Microplan integration — fetch census boundary data |
| `worker-registry` | `host.workerRegistryHost` | Look up worker records for attendance attendee creation |
| `product` (variant) | `host.productHost` | Validate product variants assigned to campaign resources |
| Redis | `host.redisHost` | Cache MDMS data, localization maps, search results |
| Kafka | `host.KAFKA_BROKER_HOST` | All async job triggering and result handling |

**Usage rules for external services:**
- All calls via `httpRequest(...)` in `utils/request.ts` with `defaultheader(request)` — never raw axios.
- Always read the service path from `config.paths.*` — never hardcode API paths inline.
- Treat every external service response as `unknown`; validate shape before accessing fields.
- Never call the same external service twice in one flow if the result can be passed as an argument.

---

## Multi-Tenancy & Central Instance

This service supports two deployment modes controlled by `config.isEnvironmentCentralInstance`:

### Central Instance Mode (`IS_ENVIRONMENT_CENTRAL_INSTANCE=true`)
Multiple states share one deployment. Isolation is enforced at Kafka and DB levels:
- **Kafka produce**: prefix topic with `{tenantId}-` using `kafkaTopicUtils.getTopicName(baseTopic, tenantId)`.
- **Kafka consume**: subscribe via regex pattern from `getConsumerTopicPattern(baseTopic)` — matches all tenant prefixes.
- **Incoming topic**: strip prefix with `stripTopicPrefix(topic)` before handler lookup.
- **Startup topic creation**: `Listener.ensureTopicsExist(getStartupTopicsToCreate(baseTopics))` pre-creates every `{tenant}-{baseTopic}` before subscribing. This is mandatory — KafkaJS regex subscriptions only match topics that exist at subscribe time and never rediscover new ones, so a tenant whose prefixed topic did not pre-exist would silently never be consumed until a restart. Create with broker-default replication (`KAFKA_TOPIC_REPLICATION_FACTOR` default `-1`) — never hardcode `replicationFactor: 1` on a multi-broker cluster. Client-level Kafka `retry` (`KAFKA_CONSUMER_RETRIES`) must be high enough for `subscribe()` to ride out the brief leadership election that bulk topic creation triggers.
- **DB schema**: `getTableName(tableName, tenantId)` returns `{tenantId.split(".")[0]}.{tableName}` (e.g., `"ng.kaduna"` → `"ng.tablename"`).
- **Exception**: topics listed in `config.kafka.KAFKA_NON_CENTRAL_INSTANCE_TOPICS` (e.g., email) are never prefixed.

### Non-Central Mode (`IS_ENVIRONMENT_CENTRAL_INSTANCE=false`)
All tenants share one DB schema (`config.DB_CONFIG.DB_SCHEMA`) and unprefixed Kafka topics.

### Rules
- Always use `kafkaTopicUtils.ts` for produce/subscribe — never build topic names inline.
- Always use `getTableName(config.DB_CONFIG.DB_*_TABLE_NAME, tenantId)` — never inline schema strings.
- `tenantId` must be typed as `TenantId` (branded) and passed explicitly — never read from module-level state.
- **`CENTRAL_INSTANCE_TENANT_IDS`** (comma-separated, e.g. `ba,oy,ko`) is the single source of truth for central-instance Kafka: it drives both startup topic creation (`getStartupTopicsToCreate`) and the consumer subscription regex (`getEffectiveConsumerPrefix`), so the two can never drift. Parsing trims whitespace and ignores empty/extra commas.
- `KAFKA_CONSUMER_TOPIC_PREFIX` is an explicit override of the derived regex (back-compat); when set it wins over `CENTRAL_INSTANCE_TENANT_IDS`. Startup fails fast if central instance is on and neither is set.

---

## Key File Reference

| Purpose | Path |
|---|---|
| Env config | `config/index.ts` |
| Status / error / process constants | `config/constants.ts` |
| Branded types | `config/models/brandedTypes.ts` ← check first |
| Zod/AJV schemas | `config/models/*.ts` ← check before creating any type |
| Resource registry | `config/resourceTypeRegistry.ts` |
| DB access | `utils/db/index.ts` → `executeQuery`, `getTableName` |
| HTTP client | `utils/request.ts` → `httpRequest`, `defaultheader` |
| Response helpers | `utils/genericUtils.ts` → `sendResponse`, `errorResponder`, `throwError` |
| Request context | `utils/requestContext.ts` → `runWithRequestContext` |
| Kafka topic prefix | `utils/kafkaTopicUtils.ts` |
| Polling | `utils/pollUtils.ts` → `pollForTemplateGeneration`, `createAndPollForCompletion` |
| Redis cache | `utils/redisUtils.ts` |

---

## Type Safety Rules

### Branded Types — Mandatory

Plain `string` for domain values causes silent parameter-swap bugs the compiler cannot catch.
`fn(tenantId: string, campaignId: string)` lets you call `fn(campaignId, tenantId)` — compiles, fails silently at runtime.
**Any string (or number) that represents a domain-specific concept must have its own branded type.**

### When to Create a Branded Type

Create a branded type whenever a value:
- identifies a domain entity (ID, code, reference, number)
- carries a specific semantic that makes it non-interchangeable with other strings of the same primitive type
- appears as a function parameter, return value, or DB/Kafka field in this codebase

**Default rule: if you find yourself typing `: string` for a domain term, stop and create a branded type.**

Examples of terms that must be branded — not an exhaustive list, extend freely:
- Any entity ID: `CampaignId`, `TenantId`, `FileStoreId`, `ResourceDetailsId`, `MappingId`, `ProcessId`, `AttendanceRegisterId`, `ProjectId`, `FacilityId`, `UserId`, `IndividualId`
- Any domain code: `BoundaryCode`, `HierarchyType`, `ResourceType`, `ProcessName`, `KafkaKey`, `CampaignNumber`
- Any user-identifying value: `MobileNumber`, `UserName`, `UserUuid`, `EmployeeCode`
- Any status value: `CampaignStatus`, `ProcessStatus`, `MappingStatus`, `DataRowStatus`, `SheetDataRowStatus`
- Any locale or module key: `LocaleCode`, `LocalizationModule`, `LocalizationKey`
- Any path or URL segment used as a domain key: `FilePath`, `ServiceEndpoint`
- Any numeric domain value: `EpochMs` (`Brand<number, "EpochMs">`), `RowIndex` (`Brand<number, "RowIndex">`)

### Pattern

```typescript
type Brand<T, B extends string> = T & { readonly __brand: B };

export type CampaignId    = Brand<string, "CampaignId">;
export type MappingStatus = Brand<string, "MappingStatus">;
export type EpochMs       = Brand<number, "EpochMs">;
// extend this file freely — grep it before adding to avoid duplicates
```

All branded types live in `config/models/brandedTypes.ts`. Always grep that file before declaring a new one.

### Status Constants Must Use Branded Types

Status constant values in `config/constants.ts` must cast to their branded status type:
```typescript
export const mappingStatuses = {
  toBeMapped: "toBeMapped" as MappingStatus,
  mapped:     "mapped"     as MappingStatus,
  deMapped:   "deMapped"   as MappingStatus,
  failed:     "failed"     as MappingStatus,
  skipped:    "skipped"    as MappingStatus,
};
```

Functions must use the branded type — never raw `string`:
```typescript
// Correct
async function updateMappingStatus(id: MappingId, status: MappingStatus): Promise<void> { /* impl */ }

// Forbidden
async function updateMappingStatus(id: string, status: string): Promise<void> { /* impl */ }
```

### Cast Rule

Cast (`as BrandedType`) only at validated inbound boundaries — Zod result, DB row mapper, or constant definition:
```typescript
const CampaignIdSchema = z.string().min(1).transform(v => v as CampaignId);
```
Never cast inside business logic.

### Trade-off: Special Types vs Primitive Types

**Always prefer the special type.** The trade-offs are real but the default is clear:

| Concern | Primitive (`string`) | Branded type (`CampaignId`) |
|---|---|---|
| Boilerplate | None | One line in `brandedTypes.ts` |
| Parameter safety | Compiler cannot catch swaps | Compiler rejects wrong type |
| Refactorability | Rename requires grep + manual audit | Rename is compiler-guided |
| Readability | Intent unclear at call site | Intent explicit in signature |
| Runtime cost | Zero | Zero — erased at compile time |

**When primitive is acceptable:**
- Truly generic utility functions with no domain semantics (e.g., a string formatter that could apply to any string)
- Local variables that are immediately used and never passed as arguments
- Third-party library call sites where the library accepts `string` — cast at that boundary only

**When to always use branded type (no exception):**
- Any function parameter or return type that carries domain meaning
- Any DB column value read from a row mapper
- Any Kafka message field that identifies an entity or carries a status
- Any value that could be confused with another string of the same primitive type in the same scope

**The boilerplate argument is not valid** — adding one line to `brandedTypes.ts` costs seconds. A parameter-swap bug in a campaign payload costs a failed health distribution. The trade-off is not close.

### Core Rules
- No `any`. Use `unknown` at boundaries; narrow with type guards before use.
- No unchecked `as T` — except branded cast at a validated boundary or constant definition.
- No `!` without adjacent null check in same scope.
- Explicit return type on every exported function.
- No `null`/`undefined` as "not found" — throw typed error or return typed `Option<T>`.
- No raw `string` for: entity IDs, boundary codes, tenant IDs, file store IDs, process names, Kafka keys, or status values.
- Always grep `config/models/` before declaring any new type.

---

## Scalability Rules

- All per-request state → `AsyncLocalStorage` via `runWithRequestContext` (`requestContext.ts`).
- No module-level mutable state that grows per request.
- Chunk all `Promise.all` by config batch size:
```typescript
for (let i = 0; i < items.length; i += config.batchSize)
  await Promise.all(items.slice(i, i + config.batchSize).map(fn));
```
- **Never hardcode a batch/chunk size.** Every batch/chunk/parallel-window size is env-configurable in `config/index.ts` (one key per operation, e.g. `config.project.creationBatchSize`, `config.facility.kafkaCreateBatchSize`, `config.user.individualSearchBatchSize`). New batching code must read from a config key and add the env var there (pattern `process.env.X ? parseInt(process.env.X, 10) : <default>`) — never a numeric literal in the loop. Defaults stay equal to the previous literal so behavior is unchanged until tuned.
- Kafka concurrency capped at `MAX_CONCURRENT=10` (semaphore in `Listener.ts`). Do not raise without OOM analysis.
- Release workbook / large object references after use — process memory limit is 3072 MB.

---

## Service Patterns

**DB**: `executeQuery(pool, sql, [$1, $2])` + `getTableName(config.DB_CONFIG.DB_*_TABLE_NAME, tenantId)`. Parameterized queries only. No `DELETE` — soft delete via `is_deleted=true`.

**HTTP**: `httpRequest(...)` via `utils/request.ts` only. Never raw axios. `defaultheader(request)` for DIGIT standard headers.

**Controller**:
```typescript
try { sendResponse(res, { Data: await service(req) }, req); }
catch (e: unknown) { errorResponder({ message: String(e), code: e?.code, description: e?.description }, req, res, e?.status ?? 500); }
```
Never `res.json()` or `res.status().send()` directly.

**Redis**: check `config.cacheValues.cacheEnabled` first; TTL = `config.cacheTime`; fall through on miss; never throw on cache miss.

**Kafka**: topic names from `config.kafka.*` only; produce/subscribe via `kafkaTopicUtils.ts`; GZIP compression default-on; schema changes additive only — never rename or remove message fields; wrap every handler in `runWithRequestContext`.

**Polling**: use `pollForTemplateGeneration` / `createAndPollForCompletion` from `utils/pollUtils.ts`. Never remove post-produce waits — intentional eventual-consistency with egov-persister.

**Localization**: all user-visible strings (Excel headers, sheet names, error annotations) are localization keys — never literal English. Resolve via `localizationMap` argument, not per-row fetches.

---

## Status Layers — Never Mix

| Layer | Constant | Sample values |
|---|---|---|
| Campaign | `campaignStatuses` | `drafted`, `started`, `inprogress`, `failed`, `cancelled` |
| Process | `processStatuses` | `pending`, `completed`, `failed` |
| Mapping | `mappingStatuses` | `toBeMapped`, `mapped`, `deMapped`, `failed` (map direction), `deMapFailed` (demap direction), `skipped` |
| Data row | `dataRowStatuses` | `pending`, `completed`, `failed` |
| Sheet row | `sheetDataRowStatuses` | `INVALID`, `CREATED`, `SKIPPED`, `EXISTING`, `UPDATED`, `FAILED` |

---

## Resource Type Extension

All five steps required atomically in one PR:
1. `config/constants.ts` → add to `allProcesses`
2. `config/resourceTypeRegistry.ts` → new registry entry (new UUID-suffixed `kafkaKey`; never reuse)
3. `processFlowClasses/<type>-processClass.ts` → implement `TemplateClass.process()`
4. `processFlowClasses/<type>Validation-processClass.ts` → validation class
5. `generateFlowClasses/<type>-generateClass.ts` → if template generation needed

Never change existing phase numbers or `dependsOn` chains.

---

## Campaign Failure Semantics

| Failure | Blocks campaign? |
|---|---|
| Boundary / project creation | Yes |
| User credential generation | Yes |
| User batch | **No** — non-blocking by policy |
| Facility batch | **No** |
| Mapping skipped | **No** — auto-resolved |
| Mapping failed (retryable) | **No** — reconciler retries up to `config.mapping.maxRetries` |
| Facility/resource mapping terminally failed | Yes — judged once at reconcile conclusion |
| User mapping terminally failed | **No** — non-blocking |
| Attendance register | **No** — optional |

## Mapping Lifecycle (Reconciler)

The mapping table (`eg_cm_campaign_mapping_data`) is **desired state**; `health-project` is **actual state**. `runMappingReconciler` (`utils/mappingReconciler.ts`) drives convergence — never judge failure from transient failure rows.

- **Failure statuses carry direction.** `failed` = a map attempt failed; `deMapFailed` = a demap attempt failed. Every demap failure writer (batch handler + legacy `userMappingUtils.startUserDemapping`) MUST write `deMapFailed` — never infer direction from `mappingId` in runtime code. (The `V20260612130000` migration is the sole exception: it uses `mappingId IS NOT NULL` as a safe one-time heuristic because in pre-reconciler code `mappingId` was only ever set after a confirmed successful creation.) Terminal requires `retryCount >= config.mapping.maxRetries`; each cycle resets retryable rows via two direction-preserving SQL UPDATEs.
- **Adopt-existing pre-pass** (`mappingBatchHandler.ts` shared driver `processToBeMappedGroup` + per-type adapters): before any create, bulk-search `health-project` (`searchProject{Resources,Facilities,Staff}ByProjects` in `api/genericApis.ts`, ≤`config.mapping.projectSearchChunkSize` projects per call, offset-paginated, **narrowed by the batch's `facilityId`/`staffId` lists** — resource search has no entity filter server-side). Existing combinations are adopted as `mapped` — never infer "already exists" from `DUPLICATE_ENTITY` error responses.
- **Generation fencing** (`utils/mappingGenerationUtils.ts`, Redis `mapping-gen:{tenantId}:{campaignNumber}`): batches carry the cycle's generation; consumers drop stale ones. Fail-open by design — correctness comes from the pre-pass + health-project unique validators, fencing only avoids wasted work.
- **Horizontal scale**: random partition keys stay; uniqueness comes from dispatch disjointness (each mapping row in exactly one batch per cycle), NOT partition affinity. Never pin a campaign to one partition.
- **Observation is stall-based** (`pollUntilCountFn` on `completed + failed` vs total, `config.mapping.reconcileStallTimeoutMs`), bounded by `config.mapping.maxReconcileCycles`. A stalled cycle re-dispatches unresolved rows next cycle (lost-batch recovery). Campaign-failed status is re-checked at cycle start, after every observe phase, and at conclusion — never conclude or mark processes complete for a failed campaign.
- **`lastError` / `retryCount`** columns are written via direct SQL only (batched VALUES-join UPDATE) — the mapping Kafka message contract is unchanged (additive `generation` field only); persister configs live outside this repo.
- Blocking policy is applied exactly once at conclusion from terminal counts (`checkCampaignMappingCompletionStatus` → `terminallyFailedMappings` / `retryableFailedMappings`; pass `maxRetries` explicitly when policy differs from `config.mapping.maxRetries`).
- **Deploy order**: migrations (`retryCount`/`lastError` columns + `deMapFailed` backfill) MUST run before pods roll — `checkCampaignMappingCompletionStatus` references `retryCount` for ALL campaigns, old and new.
- **External consumers**: `campaignStatusService` summaries and `mapping/_search` filters now surface `deMapFailed`; dashboards summing `failed` must include it.
- **User demap is sheet-presence-driven, never absence-driven** (`handleUserBoundaryMappings`): only phones explicitly present in the current upload with usage `Active`/`Inactive` can be demapped — `Inactive` demaps all the user's mappings, `Active` demaps only stale boundaries. Phones absent from the sheet keep their mappings (incl. staff adopted from `health-project` that PF never created). Deactivation requires an explicit `Inactive` row.

---

## Layering & Abstraction

Each layer has one job. Never mix concerns or bleed entity logic across layers.

| Layer | Owns | Must not |
|---|---|---|
| `controllers/` | Parse request, call validator, call service, return `sendResponse`/`errorResponder` | Contain business logic, DB calls, or Kafka produce |
| `service/` | Orchestrate business flow for one use case | Import Express objects (`req`/`res`/`next`) or call another service's service layer |
| `utils/` | Pure typed functions for one domain (boundary, facility, user, mapping…) | Cross-import between entity domains (e.g., `facilityUtils` must not import `userUtils`) |
| `api/` | Outbound HTTP to one external service | Make business decisions or handle errors beyond response normalization |
| `processFlowClasses/` | Implement `TemplateClass.process()` for one resource type | Handle logic for a different resource type |
| `generateFlowClasses/` | Implement `TemplateClass.generate()` for one resource type | Handle logic for a different resource type |
| `kafka/` | Connect, subscribe, route to handler, decompress | Contain business logic |

### Entity Separation Rules
- One util file per entity domain: `facilityMappingUtils.ts`, `userMappingUtils.ts`, `boundaryUtils.ts`. Do not merge.
- Functions operating on facility data live in facility utils. Functions on user data live in user utils. Never combine.
- A shared operation (e.g., generic Kafka produce) belongs in a shared util (`genericUtils.ts`, `kafkaTopicUtils.ts`) — not copied into each entity util.
- When a new entity is added, create its own util file. Do not extend an existing entity's util file.
- Cross-entity orchestration (e.g., "create facility then map it") belongs in `service/` — not in either entity's util file.

### Abstraction Rules
- Extract a shared utility only when identical logic appears in 3+ places with identical semantics.
- Do not abstract merely because two implementations look similar — campaign boundary logic and microplan boundary logic may look alike but have different invariants.
- If abstraction requires a flag/boolean to handle two entity types differently, it is the wrong abstraction — keep them separate.

---

## Service-Specific Anti-Patterns

| Forbidden | Why |
|---|---|
| `any` type | Masks type errors |
| `string` for domain ID/code | Parameter-swap bugs compile silently |
| Branded cast inside business logic | Cast only at validated boundary |
| New type without grepping `config/models/` | Duplicates diverge |
| `null`/`undefined` as "not found" return | Callers cannot distinguish absence from error |
| Unbounded `Promise.all(large.map(...))` | OOM / backpressure loss |
| Module-level mutable state per request | Cross-request data leak |
| Inline Kafka topic string | Bypasses multi-tenant prefix logic |
| Inline DB table/schema string | Bypasses `getTableName` routing |
| SQL string interpolation | SQL injection |
| `res.json()` directly | Bypasses response contract |
| Rename/remove Kafka topic or message field | Silent consumer break |
| Hard-delete DB records | DIGIT uses soft delete everywhere |
| Remove post-produce wait | Intentional eventual-consistency — not dead code |
| Mix status layers | Silent state corruption |
| Hardcode user-visible strings | Breaks non-English deployments |
| `process.exit()` directly | Skips terminus cleanup |

---

## Unit Testing

**Framework**: Jest + ts-jest. Test files in `src/server/__tests__/`. Run: `yarn test`.

### Coverage Requirements
Every task must include tests covering:
- Happy path (valid input → correct output/behaviour)
- All failure/error branches (invalid input, upstream failure, missing data)
- Edge cases specific to the function (empty array, zero count, boundary values)
- Multi-tenant variants where the function behaves differently per `isEnvironmentCentralInstance`

### Structure
```typescript
describe('<functionName>', () => {
  describe('<scenario group>', () => {
    it('<specific behaviour>', () => { /* assertions */ });
  });
});
```

### Mocking Rules
- Mock at the module boundary — never mock internal implementation details.
- Always mock: `logger`, `config`, `kafka/Producer`, `utils/request`, `utils/db`.
- Use typed mocks: `jest.mocked(fn)` over casting to `jest.Mock`.
- Reset mocks in `afterEach` when a test modifies shared mock state.
- Do not mock the function under test — test it directly with its mocked dependencies.

### Pattern
```typescript
jest.mock('../utils/logger', () => ({
  logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));
jest.mock('../kafka/Producer', () => ({
  produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
}));

describe('myUtil', () => {
  afterEach(() => jest.clearAllMocks());

  it('returns mapped result for valid input', async () => { /* assert result */ });
  it('throws CAMPAIGN_NOT_FOUND when campaign missing', async () => { /* assert throws */ });
  it('skips failed rows without throwing in bulk mode', async () => { /* assert no throw */ });
});
```

### Rules
- One `it` block per distinct behaviour — no multi-assertion mega-tests.
- Test file mirrors the source file name: `userMappingUtils.ts` → `userMappingUtils.test.ts`.
- Tests must not depend on execution order — each `it` is fully self-contained.
- No `setTimeout` / `sleep` in tests — use `jest.useFakeTimers()` for time-dependent code.
- Verify checklist item: `yarn test` passes with no new failures before declaring done.

---

## Code Review Rules

Every review of a diff or PR on this service must check all items below. A review is incomplete until every point is assessed. Raise a change request if any item fails — do not approve with open type-safety or scalability violations.

**Type Safety**
- No `any`, no unchecked `as T` outside a validated boundary, no unguarded `!`
- Every domain-specific string or number uses a branded type — if a new term appears as plain `string` and it represents a domain concept (ID, code, status, key, locale), flag it and require a branded type
- Status fields use their specific branded status type (`MappingStatus`, `CampaignStatus`, etc.) — not `string`
- Status constants in `config/constants.ts` cast their values to the branded type (`"toBeMapped" as MappingStatus`)
- All exported functions have explicit return types
- No `null` or `undefined` returned as a "not found" signal — must throw or use `Option<T>`

**Scalability**
- All `Promise.all` over arrays bounded by a config batch size — no unbounded parallel calls
- No new module-level mutable state that grows across requests
- Per-request state flows through `runWithRequestContext` / `AsyncLocalStorage` only

**Correctness**
- Kafka topic names from `config.kafka.*` — no inline strings
- DB tables via `getTableName(config.DB_CONFIG.DB_*_TABLE_NAME, tenantId)` — no inline schema string
- SQL parameterized with `$1`, `$2` — no string interpolation
- Multi-tenant produce/subscribe via `kafkaTopicUtils.ts` helpers
- Post-produce waits not removed or shortened
- Status layers not mixed across entity types
- No hard deletes — soft delete only

**Layering**
- No business logic in `controllers/`; no Express objects in `utils/` or `service/`
- Entity-specific logic stays in its own util file — not merged with another entity's utils
- No abstraction controlled by a boolean flag to handle two entity types

**Tests**
- New or changed logic covered by tests: happy path, all error branches, relevant edge cases
- No existing test broken without explicit justification
- Mocks at module boundary only — not mocking internal implementation details

**Comments**
- Only one-line JSDoc on non-trivial exported functions explaining WHY — no inline comments, no narrative steps

---

## Verification Checklist

- [ ] `npm run build` — zero new errors
- [ ] `yarn test` — no new failures; new code covered (happy path, error branches, edge cases)
- [ ] No `any`, no unchecked casts, no unguarded `!`
- [ ] Domain IDs/codes use branded types from `config/models/brandedTypes.ts`
- [ ] All exported functions have explicit return types
- [ ] Kafka topics from `config.kafka.*`; produce via `kafkaTopicUtils.ts`
- [ ] DB tables via `getTableName`; SQL parameterized
- [ ] `Promise.all` bounded by config batch size
- [ ] Per-request state in `AsyncLocalStorage` only
- [ ] No PII in logs
- [ ] `sendResponse` / `errorResponder` in all controller paths
- [ ] Change boundary: **Changed** / **Unchanged** / **Not checked**
- [ ] Regression risk stated
- [ ] **This file updated** if new service patterns, types, or contracts were established
