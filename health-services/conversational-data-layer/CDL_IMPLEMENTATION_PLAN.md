# CDL Implementation Plan: Multi-Index Support & Prompt Enhancement

## Current State

- **Only 1 index** (`oy-project-task-index-v1`) is hardcoded in `SchemaService.java`
- **15 indexes documented** in `INDEX_CATALOG.md` but not registered in code
- **No index auto-selection** - user must specify index or default is used
- **Prompt is basic** - missing aggregation guidance, sample queries, Data prefix rules, `.keyword` suffix rules
- **Fields missing `Data.` prefix** in `SchemaService` - the actual ES fields are wrapped in `Data.*`

---

## Phase 1: Register All 16 Index Schemas in `SchemaService.java`

### What to change
File: `src/main/java/digit/service/SchemaService.java`

### Steps

1. **Add `Data.` prefix to ALL field names** in `buildProjectTaskSchema()` (currently missing)
2. **Add builder methods** for all remaining 15 indexes, each with `Data.` prefixed fields matching `INDEX_CATALOG.md`
3. **Register all schemas** in `initializeSchemas()`

### Indexes to add (with builder method names):

| # | Index Name | Builder Method |
|---|------------|----------------|
| 1 | `oy-project-task-index-v1` | `buildProjectTaskSchema()` (fix `Data.` prefix) |
| 2 | `oy-project-index-v1` | `buildProjectSchema()` |
| 3 | `oy-project-staff-index-v1` | `buildProjectStaffSchema()` |
| 4 | `oy-service-task-v1` | `buildServiceTaskSchema()` |
| 5 | `oy-stock-index-v1` | `buildStockSchema()` |
| 6 | `oy-household-index-v1` | `buildHouseholdSchema()` |
| 7 | `oy-household-member-index-v1` | `buildHouseholdMemberSchema()` |
| 8 | `oy-referral-index-v1` | `buildReferralSchema()` |
| 9 | `oy-hf-referral-index-v1` | `buildHfReferralSchema()` |
| 10 | `oy-hf-referral-fever-index` | `buildHfReferralFeverSchema()` |
| 11 | `oy-hf-referral-drug-reaction-index` | `buildHfReferralDrugReactionSchema()` |
| 12 | `oy-side-effect-index-v1` | `buildSideEffectSchema()` |
| 13 | `oy-stock-reconciliation-index-v1` | `buildStockReconciliationSchema()` |
| 14 | `oy-transformer-save-attendance-log` | `buildAttendanceLogSchema()` |
| 15 | `oy-transformer-save-attendance-register` | `buildAttendanceRegisterSchema()` |
| 16 | `oy-pgr-services` | `buildPgrSchema()` |

### Critical fix: `Data.` prefix
Current code has fields like `"id"`, `"taskId"` etc. The actual ES mapping uses `Data.id`, `Data.taskId`. All field names must be prefixed with `Data.` (except for indexes that don't use the Data wrapper: attendance log, attendance register, PGR).

### Add `description` field to `IndexSchema`
The `IndexSchema` model needs a `description` field so the LLM can auto-select indexes. Add:
```java
// In IndexSchema.java
private String description;
```

---

## Phase 2: Add Index Auto-Selection (LLM-Driven)

### What to change
Files: `QueryOrchestrationService.java`, `PromptBuilderService.java`, `SchemaService.java`

### Current flow (broken for multi-index)
```
User sends query (no indexName) → defaults to oy-project-task-index-v1 → fails for stock/household queries
```

### New flow
```
User sends query (no indexName)
  → Build index selection prompt with all index names + descriptions
  → LLM picks the best index
  → Build query generation prompt with selected index schema
  → LLM generates ES query
  → Execute
```

### Steps

1. **Add `SchemaService.getAllSchemaDescriptions()`** - returns map of index name → description
2. **Add `PromptBuilderService.buildIndexSelectionPrompt()`** - creates a prompt listing all indexes with descriptions, asks LLM to pick one
3. **Modify `QueryOrchestrationService.processQuery()`**:
   - If no `indexName` provided, call LLM with index selection prompt first
   - Parse LLM response to extract chosen index name
   - Then proceed with normal query generation flow
4. **Alternative (simpler)**: Include ALL index descriptions in the system prompt and ask the LLM to output both the index name and the query in a single call. This saves one LLM round-trip.

### Recommended approach: Single-call with index selection

Modify the prompt to include a catalog section and ask the LLM to output JSON like:
```json
{
  "index": "oy-stock-index-v1",
  "query": { ... }
}
```

This requires changes to:
- `PromptBuilderService` - new prompt format
- `QueryOrchestrationService` - parse index + query from response
- `QueryValidatorService` - handle the new response format

---

## Phase 3: Enhance the System Prompt

### What to change
File: `src/main/java/digit/service/PromptBuilderService.java`

### Current prompt issues
1. No guidance on `Data.` prefix - LLM generates `status` instead of `Data.status`
2. No `.keyword` suffix guidance for term/terms/aggregation queries
3. No aggregation examples
4. No sample query patterns
5. Status enums hardcoded only for project-task, not other indexes
6. No boundary hierarchy query guidance
7. No guidance on SMC campaign-specific filters (doseIndex, cycleIndex)

### New prompt structure

```
You are an Elasticsearch query generator for the DIGIT Health Campaign platform.

OUTPUT FORMAT:
- Output ONLY valid Elasticsearch Query DSL as JSON
- No explanations, no markdown, no code fences
- Raw JSON only

SECURITY RULES:
- NEVER generate write operations (no _update, _delete, _index, _bulk)
- NEVER use script, regexp, wildcard, or fuzzy queries
- Use only the fields listed in the schema below

FIELD NAME RULES:
- All fields are nested under a "Data" object in Elasticsearch
- Always use the exact field paths shown in the schema (e.g., "Data.status", "Data.boundaryHierarchy.lga")
- For keyword fields in term/terms/aggregation queries, append ".keyword" suffix
  e.g., "Data.status.keyword", "Data.boundaryHierarchy.lga.keyword"
- For text fields in match queries, do NOT append ".keyword"
- For date/long/boolean/geo_point fields, do NOT append ".keyword"

QUERY CONSTRUCTION RULES:
- Always include "size": 10 unless user specifies a count
- For count-only queries, use "size": 0
- Always wrap in a "bool" query for composability
- For keyword fields: use "term" or "terms" (exact match)
- For text fields: use "match" or "match_phrase"
- For date ranges: use "range" with "gte"/"lte" in epoch_millis

AGGREGATION RULES:
- For distribution/grouping queries, use Elasticsearch aggregations
- Use "size": 0 for aggregation-only queries
- Always use ".keyword" suffix on aggregation fields
- Default aggregation bucket size: 1000 (boundaries can exceed 10)
- Aggregation boundary level should match the user's ask:
  - "by state" → Data.boundaryHierarchy.state.keyword
  - "by LGA" → Data.boundaryHierarchy.lga.keyword
  - "by ward" → Data.boundaryHierarchy.ward.keyword
  - "by community" → Data.boundaryHierarchy.community.keyword

LOCATION FILTER RULES:
- When user asks about specific boundaries, add a terms filter:
  {"terms": {"Data.boundaryHierarchy.<level>.keyword": ["Name1", "Name2"]}}
- When user asks for ALL boundaries at a level, skip the filter and use aggregation only
- Boundary levels: country > state > lga > ward > community > healthFacility

STATUS MAPPING (Project Task Index):
- "successful deliveries" / "administered" → Data.administrationStatus.keyword = "ADMINISTRATION_SUCCESS"
- "referred children" → Data.administrationStatus.keyword = "BENEFICIARY_REFERRED"
- "ineligible children" → Data.administrationStatus.keyword = "BENEFICIARY_INELIGIBLE"
- "failed administration" → Data.administrationStatus.keyword = "ADMINISTRATION_FAILED"
- "refused" → Data.administrationStatus.keyword = "BENEFICIARY_REFUSED"

SMC CAMPAIGN RULES (Project Task Index):
- For SMC campaign queries with ADMINISTRATION_SUCCESS status:
  Data.additionalDetails.doseIndex.keyword filter is MANDATORY
- Cycle-level breakdown: aggregate on Data.additionalDetails.cycleIndex.keyword

DATE FILTER RULES:
- Apply Data.createdTime range filter only when user specifies a time period
- Use epoch milliseconds for createdTime range values
- If no time period specified, omit the date filter

STOCK TRANSACTION MAPPING (Stock Index):
- "stock received" → Data.eventType.keyword = "RECEIVED"
- "stock dispatched" → Data.eventType.keyword = "DISPATCHED"
- "damaged stock" → Data.reason.keyword IN ["DAMAGED_IN_STORAGE", "DAMAGED_IN_TRANSIT"]
- "lost stock" → Data.reason.keyword IN ["LOST_IN_STORAGE", "LOST_IN_TRANSIT"]

SCHEMA:
<dynamically injected schema>

SAMPLE QUERIES:

Example 1: Count successful deliveries by LGA for cycle 1, dose 1
{
  "query": {
    "bool": {
      "must": [
        {"terms": {"Data.boundaryHierarchy.lga.keyword": ["Ningi", "Bauchi"]}},
        {"term": {"Data.administrationStatus.keyword": {"value": "ADMINISTRATION_SUCCESS"}}},
        {"term": {"Data.additionalDetails.doseIndex.keyword": {"value": "01"}}}
      ]
    }
  },
  "size": 0,
  "aggs": {
    "by_cycle": {
      "terms": {"field": "Data.additionalDetails.cycleIndex.keyword"},
      "aggs": {
        "by_lga": {
          "terms": {"field": "Data.boundaryHierarchy.lga.keyword"},
          "aggs": {
            "count": {"value_count": {"field": "Data.id.keyword"}}
          }
        }
      }
    }
  }
}

Example 2: Total households registered by ward
{
  "query": {"match_all": {}},
  "size": 0,
  "aggs": {
    "by_ward": {
      "terms": {"field": "Data.boundaryHierarchy.ward.keyword", "size": 1000}
    }
  }
}

Example 3: Stock received at a specific facility
{
  "query": {
    "bool": {
      "must": [
        {"term": {"Data.eventType.keyword": "RECEIVED"}},
        {"term": {"Data.facilityName.keyword": "Facility ABC"}}
      ]
    }
  },
  "size": 10
}
```

---

## Phase 4: Add `IndexField.description` for Richer Schema Prompts

### What to change
File: `src/main/java/digit/web/models/IndexField.java`

### Steps
1. Add `description` field to `IndexField` model
2. Update `SchemaService.formatSchemaForPrompt()` to include descriptions
3. Update all builder methods to include field descriptions from `INDEX_CATALOG.md`

### New schema format in prompt:
```
Fields:
    Data.status (keyword): Task delivery status [ADMINISTRATION_SUCCESS, ADMINISTRATION_FAILED, ...]
    Data.boundaryHierarchy.lga (keyword): LGA name - use for location filtering/aggregation
    Data.createdTime (long): Creation timestamp in epoch milliseconds
```

---

## Phase 5: Response Format Enhancement for Index Auto-Selection

### What to change
Files: `PromptBuilderService.java`, `QueryOrchestrationService.java`, `CdlQueryResponse.java`

### When user doesn't specify an index

**Option A: Two-step LLM call (more reliable, slower)**
1. First call: Send index catalog → LLM picks index
2. Second call: Send schema + query → LLM generates ES DSL

**Option B: Single-call with embedded catalog (faster, needs careful prompt)**
1. Include compact index catalog in prompt
2. LLM outputs `{"selectedIndex": "...", "query": {...}}`
3. Parse response, validate, execute

### Recommended: Option A for reliability
- Smaller prompts = better LLM accuracy
- Index selection is a simpler task = can use faster/cheaper model
- Query generation gets full schema context

### Changes needed:
1. `PromptBuilderService.buildIndexSelectionPrompt(Map<String, String> indexDescriptions, String userQuery)` - new method
2. `QueryOrchestrationService.processQuery()` - add index selection step before query generation
3. `CdlQueryResponse` - add `selectedIndex` field to response

---

## Phase 6: Improve `formatSchemaForPrompt()` Output

### Current format (too terse):
```
Index: oy-project-task-index-v1
Fields:
    id (keyword)
    status (keyword): [ADMINISTRATION_FAILED, ADMINISTRATION_SUCCESS, ...]
```

### New format (richer, with Data prefix and descriptions):
```
Index: oy-project-task-index-v1
Description: Denormalized index combining task delivery data with individual/beneficiary details...

Fields:
    Data.id (keyword) - Unique record ID
    Data.taskId (keyword) - Task identifier
    Data.status (keyword) - Task delivery status [ADMINISTRATION_SUCCESS, ADMINISTRATION_FAILED, BENEFICIARY_REFUSED, CLOSED_HOUSEHOLD, DELIVERED, NOT_ADMINISTERED, INELIGIBLE]
    Data.boundaryHierarchy.lga (keyword) - LGA name, use .keyword for filtering
    Data.createdTime (long) - Creation timestamp (epoch ms)
    ...
```

---

## Implementation Order & Priority

| Priority | Phase | Effort | Impact |
|----------|-------|--------|--------|
| P0 | Phase 1: Fix `Data.` prefix in existing schema + register all 16 indexes | Medium | Critical - without this, no query works correctly |
| P0 | Phase 3: Enhance system prompt with `.keyword` rules + aggregation guidance | Low | Critical - current prompt produces invalid queries |
| P1 | Phase 6: Improve schema format in prompt | Low | High - better field context = better queries |
| P1 | Phase 4: Add field descriptions to `IndexField` | Low | Medium - enriches prompt with field meaning |
| P2 | Phase 2: Index auto-selection | Medium | High - enables multi-index without user specifying index |
| P2 | Phase 5: Response format for auto-selection | Medium | High - needed for Phase 2 |

---

## File Change Summary

| File | Changes |
|------|---------|
| `IndexSchema.java` | Add `description` field |
| `IndexField.java` | Add `description` field |
| `SchemaService.java` | Fix Data prefix, add 15 new builder methods, add `getAllSchemaDescriptions()` |
| `PromptBuilderService.java` | Rewrite system prompt, add index selection prompt, improve schema formatting |
| `QueryOrchestrationService.java` | Add index auto-selection step |
| `CdlQueryResponse.java` | Add `selectedIndex` field |
| `ServiceConstants.java` | Add status enum constants for all indexes |
| `application.properties` | Remove hardcoded default index (optional) |

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Prompt too long with all 16 schemas | Only include selected index schema, not all 16 |
| LLM picks wrong index | Include clear index descriptions; add user override |
| `.keyword` suffix errors | Add post-processing in `QueryValidatorService` to auto-fix common `.keyword` issues |
| Aggregation depth > 2 blocked by validator | Increase `cdl.query.max-agg-depth` to 3 for nested aggs (cycle > lga > count) |
| Data prefix inconsistency | Unit test all schema builders against INDEX_CATALOG.md |
