# Conversational Data Layer — POC Plan

## Vision

Build a natural language interface over Elasticsearch where users type plain-English queries (e.g., "Show me all administrations in Oyo state") and an LLM (Claude) translates them into safe, read-only Elasticsearch DSL queries, executes them, and returns formatted results.

---

## Architecture Overview

```
┌──────────────┐     ┌──────────────────┐     ┌────────────────────┐     ┌──────────────┐
│   User UI    │────▶│  API Gateway /   │────▶│   Query Engine     │────▶│ Elasticsearch│
│  (React/Web) │◀────│  Backend (Java)  │◀────│  (LLM Translator)  │◀────│  (Read-Only) │
└──────────────┘     └──────────────────┘     └────────────────────┘     └──────────────┘
                              │                        │
                              ▼                        ▼
                     ┌────────────────┐       ┌────────────────┐
                     │  PII Sanitizer │       │ Query Validator │
                     │  & Redactor    │       │ & Rate Limiter  │
                     └────────────────┘       └────────────────┘
```

---

## Key Constraints & Solutions

### 1. No PII Data Leak to LLM

| Risk | Mitigation |
|------|------------|
| User query contains PII (Aadhaar, phone, name) | Pre-process: Regex-based PII detection & replacement with placeholders before sending to LLM |
| LLM sees index data/mappings that contain PII | Only send **schema metadata** (field names, types, enum values) — never actual document data |
| LLM response contains PII | Post-process: Scan LLM output for PII patterns before returning to user |

**Approach:** The LLM only ever sees the **index mapping schema** and the **sanitized natural language query**. It never sees actual Elasticsearch documents. The backend executes the generated query and returns results directly to the user.

### 2. No Underperforming / Expensive Queries

| Risk | Mitigation |
|------|------------|
| Wildcard or `match_all` on large indices | Query validator whitelist: only allow specific query types (`term`, `match`, `range`, `bool`) |
| Missing pagination | Force `size` limit (max 100) and inject `from`/`size` if absent |
| Aggregation bombs | Cap `aggs` depth to 2 levels, cap bucket size |
| Slow regex/script queries | Blacklist `script`, `regexp`, `fuzzy` with high edit distance |
| Runaway queries | Set `timeout: "5s"` on every query; use ES `search.max_buckets` cluster setting |

**Approach:** A **Query Validator** layer parses the generated JSON DSL and applies a strict allowlist before execution.

### 3. No Write Operations

| Risk | Mitigation |
|------|------------|
| LLM generates `_update`, `_delete`, `_bulk` | Validator rejects any query targeting write endpoints |
| Prompt injection tricks LLM into writes | Backend only calls `_search` and `_msearch` endpoints — hardcoded, not LLM-controlled |
| Direct ES access | Use a **read-only ES user/role** with only `read` and `view_index_metadata` privileges |

**Approach:** Defense in depth — the ES credentials used by this service physically cannot write. The backend code only constructs `_search` requests regardless of LLM output.

### 4. Production Readiness (Long-term)

| Concern | Strategy |
|---------|----------|
| Accuracy | Build evaluation dataset; measure query correctness over time |
| Latency | Cache common query patterns; use Claude Haiku for simple queries, Sonnet for complex |
| Cost | Batch similar schema contexts; cache LLM responses for identical queries |
| Observability | Log every: user query → sanitized query → LLM prompt → generated DSL → validation result → ES response time |
| Multi-tenancy | Inject tenant filters (`tenantId` term) into every generated query server-side |
| Scaling | Stateless translation service behind load balancer; horizontal scaling |

---

## POC Execution Plan

### Phase 1: Foundation (Week 1)

**Goal:** End-to-end flow working for 1 index with hardcoded schema.

- [ ] Set up read-only ES credentials and a dedicated service account
- [ ] Build schema extractor — pull index mapping, strip to field names + types
- [ ] Build PII sanitizer (regex for Aadhaar, phone, email, PAN patterns)
- [ ] Create system prompt template with schema context and query generation rules
- [ ] Integrate Claude API (Sonnet) for NL → ES DSL translation
- [ ] Build query validator (allowlist parser)
- [ ] Wire up: sanitize → translate → validate → execute → return
- [ ] Test with 10 sample natural language queries

### Phase 2: Hardening (Week 2)

**Goal:** Safe enough for internal demo with real data.

- [ ] Expand PII detection (names via NER, address patterns)
- [ ] Add query complexity scoring and reject queries above threshold
- [ ] Add `timeout`, `size` injection to all queries
- [ ] Build basic React UI with query input, results table, and generated DSL preview
- [ ] Add conversation context (multi-turn: "now filter by ward 5")
- [ ] Test with 3-4 different indices (Property Tax, Trade License, Water & Sewerage)
- [ ] Logging and basic metrics (latency, validation failures, LLM errors)

### Phase 3: Production Path (Week 3-4)

**Goal:** Roadmap and architecture for production deployment.

- [ ] Build evaluation framework: 50+ query pairs (NL → expected DSL)
- [ ] Add semantic caching for repeated query patterns
- [ ] Multi-tenant query injection
- [ ] Rate limiting per user/tenant
- [ ] Error handling with user-friendly messages ("I couldn't understand that, try rephrasing")
- [ ] Documentation: API spec, security model, operational runbook
- [ ] Load testing with concurrent users

---

## Technology Stack

| Component | Technology |
|-----------|-----------|
| LLM | Claude Sonnet 4.5 (via Anthropic API) |
| Backend | Java Spring Boot (aligns with DIGIT stack) |
| Query Validator | Custom Java — JSON parse + allowlist rules |
| PII Sanitizer | Regex + optional NER (Stanford/OpenNLP) |
| Elasticsearch | Existing DIGIT ES cluster (read-only role) |
| Frontend | React (lightweight — input box + results table) |
| Caching | Redis (query pattern → DSL cache) |
| Logging | ELK stack (existing DIGIT infrastructure) |

---

## Sample System Prompt (Core of the POC)

```
You are an Elasticsearch query generator for the DIGIT urban governance platform.

RULES:
1. Output ONLY valid Elasticsearch Query DSL as JSON. No explanations.
2. NEVER generate write operations (no _update, _delete, _index, _bulk).
3. NEVER use script queries, regexp, or wildcard on text fields.
4. Always include "size": 10 unless the user specifies a count.
5. Use only the fields listed in the schema below.
6. For date ranges, use "range" with "gte"/"lte" in epoch_millis or ISO format.
7. Always wrap in a "bool" query for composability.

SCHEMA:
Index: oy-project-task-index-v1
Fields:
    @timestamp (date)
    id (keyword)
    taskId (keyword)
    taskClientReferenceId (keyword)
    clientReferenceId (keyword)
    individualId (keyword)
    projectId (keyword)
    projectType (keyword)
    projectTypeId (keyword)
    tenantId (keyword)
    nameOfUser (text)
    userName (text)
    role (keyword)
    gender (keyword)
    age (long)
    dateOfBirth (long)
    status (keyword): [ADMINISTRATION_FAILED, ADMINISTRATION_SUCCESS, BENEFICIARY_REFUSED, CLOSED_HOUSEHOLD, DELIVERED, NOT_ADMINISTERED, INELIGIBLE]
    administrationStatus (keyword)
    taskType (keyword)
    isDelivered (boolean)
    deliveredTo (text)
    deliveryComments (text)
    productName (keyword)
    productVariant (keyword)
    quantity (long)
    createdBy (keyword)
    createdTime (long)
    lastModifiedBy (keyword)
    lastModifiedTime (long)
    syncedDate (date)
    syncedTime (long)
    syncedTimeStamp (date)
    taskDates (date)
    latitude (float)
    longitude (float)
    geoPoint (geo_point)
    locationAccuracy (float)
    previousGeoPoint (float)
    localityCode (keyword)
    
Boundary Hierarchy:
    boundaryHierarchy.country (keyword)
    boundaryHierarchy.state (keyword)
    boundaryHierarchy.lga (keyword)
    boundaryHierarchy.ward (keyword)
    boundaryHierarchy.community (keyword)
    boundaryHierarchy.healthFacility (keyword)

Boundary Hierarchy Codes
    boundaryHierarchyCode.country (keyword)
    boundaryHierarchyCode.state (keyword)
    boundaryHierarchyCode.lga (keyword)
    boundaryHierarchyCode.ward (keyword)
    boundaryHierarchyCode.community (keyword)
    boundaryHierarchyCode.healthFacility (keyword)

Additional Details (nested object)
    additionalDetails.name (text)
    additionalDetails.age (keyword)
    additionalDetails.gender (keyword)
    additionalDetails.uniqueBeneficiaryId (keyword)
    additionalDetails.individualClientReferenceId (keyword)
    additionalDetails.deliveryStrategy (keyword)
    additionalDetails.deliveryType (keyword)
    additionalDetails.doseIndex (keyword)
    additionalDetails.cycleIndex (keyword)
    additionalDetails.taskStatus (keyword)
    additionalDetails.reAdministered (keyword)
    additionalDetails.ineligibleReasons (keyword)
    additionalDetails.latitude (keyword)
    additionalDetails.longitude (keyword)
    additionalDetails.dateOfAdministration (keyword)
    additionalDetails.dateOfDelivery (keyword)
    additionalDetails.dateOfVerification (keyword)

USER QUERY: {sanitized_user_query}
```
pla
---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| LLM generates invalid DSL | High (initially) | Low — validator catches | Iterative prompt tuning + few-shot examples |
| PII leaks in edge cases | Medium | High | Layered detection + LLM never sees documents |
| Users expect SQL-like precision | Medium | Medium | Set expectations in UI; show generated query for transparency |
| ES cluster load from bad queries | Low (with validator) | High | Read-only user + query timeout + complexity cap |
| Prompt injection via user input | Medium | Medium | Sanitize input; structured output parsing; validator as final gate |

---

## Success Criteria for POC

1. **Accuracy**: ≥ 80% of test queries produce correct results on first attempt
2. **Safety**: 0 PII tokens sent to LLM across all test cases
3. **Read-only**: 0 write operations possible (verified by ES audit log)
4. **Performance**: < 5s end-to-end latency for 90th percentile queries
5. **Robustness**: Graceful handling of ambiguous/invalid queries (no 500 errors)
