# Campaign Management Service — Java Migration & Saga Architecture

> **Status:** Design approved, pending implementation  
> **Author:** Ritik Kumar  
> **Date:** 2026-06-29  
> **Replaces:** `project-factory` (TypeScript) + `excel-ingestion` (Java, separate service)  
> **Target scale:** 100,000 boundaries · 30,000 users · 15,000 facilities per campaign

---

## Table of Contents

1. [Why We Are Doing This](#1-why-we-are-doing-this)
2. [What the Current System Does](#2-what-the-current-system-does)
3. [Why the Current System Breaks at Scale](#3-why-the-current-system-breaks-at-scale)
4. [Target Architecture Overview](#4-target-architecture-overview)
5. [Campaign Lifecycle (Draft & Create)](#5-campaign-lifecycle-draft--create)
6. [Saga Design](#6-saga-design)
7. [Scalability Design](#7-scalability-design)
8. [Database Schema](#8-database-schema)
9. [Kafka Topic Design](#9-kafka-topic-design)
10. [Project File Structure](#10-project-file-structure)
11. [Key Implementation Patterns](#11-key-implementation-patterns)
12. [Implementation Roadmap](#12-implementation-roadmap)
13. [Technology Decisions](#13-technology-decisions)
14. [Confidence Assessment](#14-confidence-assessment)
15. [Contracts That Must Not Change](#15-contracts-that-must-not-change)

---

## 1. Why We Are Doing This

The current `project-factory` (TypeScript/Node.js) breaks under real-world campaign sizes:

| Scenario | Limit | Failure Mode |
|---|---|---|
| Users > 2,000 | Fails silently | IDGen bottleneck + poll timeout |
| Boundaries > 15,000 | OOM or timeout | Node.js single-thread, no streaming |
| Facilities > 4,000 | Partial creates | No idempotency — retries create ghost records |
| Any downstream service hangs | Consumer blocked forever | `axios timeout: 0` (now fixed but pattern is fragile) |
| Campaign fails mid-way | Manual cleanup required | No saga compensation |

The goal is a single unified Java 25 service that handles 100k boundaries, 30k users, and 15k facilities in a single campaign with zero data loss and safe retry at every step.

---

## 2. What the Current System Does

Two services collaborate:

### project-factory (TypeScript)
- Accepts campaign create/update/search via REST
- Generates pre-populated Excel templates (delegates to excel-ingestion)
- Accepts filled Excel uploads, sends to excel-ingestion for parsing
- Receives parsed rows via Kafka
- Orchestrates creation of: boundaries, projects, HRMS users, worker registry, facilities, project-staff mappings, project-facility mappings, project-resources, attendance registers
- Tracks row-level status, writes back a status Excel with per-row errors

### excel-ingestion (Java/Spring Boot)
- Generates Excel templates (MDMS-driven column definitions, cascading boundary dropdowns)
- Parses uploaded Excel files row-by-row (Apache POI)
- Validates each row against MDMS schema
- Persists rows to staging table
- Publishes parsed results to Kafka

### External services called
| Service | Purpose |
|---|---|
| health-hrms | Create field staff (employees) |
| health-project | Create projects, staff/facility/resource mappings |
| facility | Create health facilities |
| boundary-service | Search boundary hierarchy |
| egov-idgen | Generate campaign numbers and usernames |
| egov-mdms-service | Fetch schema definitions and master data |
| egov-localization | Fetch/upsert Excel column headers |
| egov-filestore | Upload/download Excel files |
| attendance-service | Create attendance registers |
| worker-registry | Persist worker records |
| egov-user | Search existing user accounts |
| health-individual | Lookup beneficiaries |
| Redis | Cache MDMS data, localization maps |

---

## 3. Why the Current System Breaks at Scale

### Root Causes

**Node.js single-threaded event loop**
- Cannot parallelize CPU-heavy work (Excel parsing, large batch transforms)
- I/O concurrency is soft-capped by the event loop

**Batch size = 30, concurrent consumers = 10**
- 30,000 users → 1,000 Kafka messages → 100 processing rounds
- Each round: HRMS call + Individual search + IDGen call (sequential)

**IDGen called once per batch**
- 30,000 users at batch=30 → 1,000 separate IDGen API calls
- Sequential bottleneck inside every batch

**Boundary data fetched per batch**
- Same boundary codes queried 1,000 times across 1,000 batches
- No pre-fetch strategy

**No circuit breakers**
- One slow HRMS pod blocks all 10 Kafka consumer threads
- No fallback, no timeout coordination

**ExcelJS loads entire workbook into heap**
- 50,000 rows → ~500MB heap spike
- 3,072 MB Node.js heap → OOM at large files

**No Saga compensation**
- Failure mid-way leaves partial state in HRMS and project service
- Retry creates ghost users and orphan facilities
- Manual cleanup required

**Poll-based completion monitoring**
- DB polled every 40 seconds for up to 133 minutes
- Not event-driven, wasteful at scale

---

## 4. Target Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│              CAMPAIGN MANAGEMENT SERVICE (Java 25)                    │
│                                                                        │
│  ┌──────────────┐  ┌─────────────────┐  ┌──────────────────────────┐ │
│  │  REST Layer   │  │  Saga Engine    │  │  Excel Module            │ │
│  │ (Spring MVC)  │  │ (Kafka Events)  │  │ (embedded, Apache POI)   │ │
│  └──────┬───────┘  └───────┬─────────┘  └──────────────────────────┘ │
│         │                  │                                           │
│  ┌──────▼──────────────────▼────────────────────────────────────────┐ │
│  │                     Application Layer                             │ │
│  │  CampaignApplicationService | SagaOrchestrator | IdempotencySvc  │ │
│  └──────────────────────────────────────────────────────────────────┘ │
│         │                                                              │
│  ┌──────▼──────────────────────────────────────────────────────────┐  │
│  │                     Domain Layer                                 │  │
│  │  Campaign | Saga flows | Mapping | Excel generation/processing   │  │
│  └──────────────────────────────────────────────────────────────────┘ │
│         │                                                              │
│  ┌──────▼──────────────────────────────────────────────────────────┐  │
│  │                  Infrastructure Layer                            │  │
│  │  PostgreSQL | Kafka | Redis | FileStore | External HTTP Clients  │  │
│  └──────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
```

### Key architectural decisions

| Decision | Choice | Reason |
|---|---|---|
| Language | Java 25 | Virtual threads, type safety, Spring ecosystem, better CPU concurrency |
| Concurrency | Virtual threads (Project Loom) | 50–100 concurrent Kafka consumers without OOM |
| Saga pattern | Orchestrated (coordinator) | Visibility, debuggability, explicit compensation, easy partial retry |
| Saga state store | PostgreSQL (same DB) | No new infra, transactional step advance, queryable for debugging |
| Idempotency | DB registry per entity (idempotency key) | Eliminates ghost creates on retry |
| Excel parsing | SXSSF streaming (Apache POI) | O(1) memory for 100k-row files |
| Resilience | Resilience4j (circuit breaker + retry) | Per-service config, standard Spring ecosystem |
| Excel ingestion | Embedded module (not a separate service) | One deployment, no HTTP round-trip, shared transaction context |
| Multi-tenancy | Kafka topic prefix + DB schema routing | Same contract as existing TypeScript service |

---

## 5. Campaign Lifecycle (Draft & Create)

### Phase 1: Draft (action = draft)

Purpose: configure the campaign incrementally. No resources are created.

```
POST /v1/campaigns/_create (action=draft)
  ↓
  1. Validate required fields (projectType, tenantId, campaignName,
     hierarchyType, startDate, endDate)
  2. Persist cms_campaign (status=DRAFTED)
  3. If boundaries present → emit {t}.cms.excel.command.generate
  4. Return 202 { campaignId, campaignNumber, status=DRAFTED }

POST /v1/campaigns/_update (action=draft)
  ↓
  1. Validate campaignId exists and status=DRAFTED
  2. If boundaries changed:
       a. Mark old cms_campaign_resource rows as EXPIRED
       b. Emit {t}.cms.excel.command.generate (new boundary set)
  3. Persist updated fields
  4. Return 200

Excel generation consumer:
  ← {t}.cms.excel.command.generate
  ↓ ExcelGenerationService.generate(campaignId, boundaries, tenantId)
  ↓ Fetch MDMS schema → build template → upload to filestore
  ↓ Persist cms_campaign_resource (status=ACTIVE, type=TEMPLATE)
  ↓ Emit {t}.cms.excel.event.generation.completed
```

**What is NOT executed during Draft:**
- Project creation
- HRMS user creation
- Facility creation
- Project-staff / project-facility / project-resource mappings
- Attendance registers

---

### Phase 2: Create (action = create)

Purpose: provision all downstream resources. Triggers the full saga.

```
POST /v1/campaigns/_create (action=create)
  ↓
  1. Validate complete campaign config (all required fields + resources
     + boundaries + deliveryRules + additionalDetails)
  2. Persist/update cms_campaign (status=CREATING)
  3. Emit {t}.cms.campaign.command.create
  4. Return 202 { campaignId, status=CREATING }

Campaign command consumer:
  ← {t}.cms.campaign.command.create
  ↓ SagaOrchestrator.start(CampaignCreationSaga, campaignId)
  ↓ Saga runs steps (see Section 6)
  ↓ On full completion → cms_campaign status=ACTIVE
  ↓ On failure → cms_campaign status=FAILED, compensation triggered
```

---

## 6. Saga Design

### Why Orchestrated Saga (not Choreography)

| | Choreography | Orchestration (chosen) |
|---|---|---|
| State visibility | Distributed across topics | Central — query cms_saga_instance |
| Debugging | Trace events across 12 topics | Single table lookup |
| Compensation rollback | Each service must know the rollback sequence | Coordinator sends explicit compensate commands |
| Partial retry | Hard — need to know which step failed | Trivial — resume from last_completed_step |
| Suitable for 12+ services | No | Yes |

---

### 6.1 CampaignCreationSaga (master)

```
Step 1: VALIDATE_CAMPAIGN
  Execute:    Validate campaign config, check all references (projectType, hierarchyType, etc.)
  Compensate: Mark campaign VALIDATION_FAILED
  Retry:      No retry — validation failures are terminal

Step 2: CREATE_PROJECTS           → delegates to ProjectCreationSaga
  Execute:    Create one project per boundary node
  Compensate: Soft-delete all created projects
  Retry:      max 3, exponential backoff

Step 3: PROCESS_USERS             → delegates to UserCreationSaga
  Execute:    Parse user rows, create HRMS employees, project-staff mappings
  Compensate: Deactivate created employees, delete project-staff mappings
  Retry:      max 5

Step 4: PROCESS_FACILITIES        → delegates to FacilitySaga
  Execute:    Parse facility rows, create facilities, project-facility mappings
  Compensate: Soft-delete facilities, delete project-facility mappings
  Retry:      max 5

Step 5: RECONCILE_MAPPINGS        → delegates to MappingReconcilerSaga
  Execute:    Ensure all user/facility → project mappings converge
  Compensate: Remove all mappings created by this campaign
  Retry:      max reconcile cycles = 5

Step 6: CREATE_RESOURCES
  Execute:    Create project-resource assignments (product variants)
  Compensate: Delete resource assignments
  Retry:      max 3

Step 7: FINALIZE_CAMPAIGN
  Execute:    Set campaign status=ACTIVE, generate status Excel, upload to filestore
  Compensate: Revert to FAILED
  Retry:      max 2
```

---

### 6.2 ProjectCreationSaga

```
Input: campaignId, tenantId, boundaries[]

Step 1: CHUNK_BOUNDARIES
  Split boundaries into chunks of BOUNDARY_CHUNK_SIZE (default 500)
  Emit one {t}.cms.resource.command.project.create message per chunk
  No compensate needed (pure split)

Step 2: CREATE_PROJECT_CHUNKS (parallel, one Kafka message per chunk)
  Execute:    POST /project/v1/_create (batch of 50 per API call)
  Idempotency key: tenantId:campaignNumber:boundaryCode
  Compensate: PUT /project/v1/_update (isDeleted=true)
  Retry:      max 3, backoff 1s → 2s → 4s

Step 3: VERIFY_ALL_PROJECTS_CREATED
  Poll cms_process for all chunk completions
  Timeout: configurable (default 10 minutes for 100k boundaries)
```

---

### 6.3 UserCreationSaga

```
Input: campaignId, tenantId, userRows[] (from Excel processing)

Step 1: BULK_IDGEN
  Split rows:
    Group A: username column blank → need generated username
    Group B: username column filled → use as-is
  For Group A only: ONE bulk IDGen call (count = Group A size)
  Assign generated usernames to Group A rows
  Merge Group A + Group B → all rows now have usernames
  Note: idempotency key is always tenantId:mobileNumber (not username)
        so user-provided usernames don't affect idempotency

Step 2: PRE_FETCH_BOUNDARY_CONTEXT
  Collect all unique boundaryCodes from user rows (from Excel column)
  Fetch boundary data in chunks of 50 → ONE pre-fetch before any batching
  Build map: boundaryCode → projectId
  Store in Redis: key = "boundary-ctx:{campaignId}", TTL = 24h
  This eliminates per-batch boundary lookups (was 1000 calls, now ~60)

Step 3: CREATE_HRMS_EMPLOYEES
  Split all user rows into chunks of USER_BATCH_SIZE (default 100)
  For each chunk:
    a. Check idempotency registry (tenantId:mobileNumber)
       → already exists: mark completed, skip HRMS call
    b. POST to health-hrms (batch of 100)
    c. Register each created UUID in cms_idempotency_registry
  Compensate: Deactivate HRMS employees (PUT with status=INACTIVE)
  Retry:      max 5

Step 4: CREATE_WORKER_REGISTRY
  For each created employee: POST to worker-registry
  Idempotency key: tenantId:mobileNumber:workerRegistry
  Compensate: Soft-delete worker records
  Retry:      max 3

Step 5: CREATE_PROJECT_STAFF
  For each employee row:
    Look up projectId from Redis boundary-ctx cache using row's boundaryCode
    POST to /project/v1/staff/_create
  Idempotency key: tenantId:employeeUuid:projectId
  Compensate: DELETE /project/v1/staff/_delete
  Retry:      max 5
```

**Key points:**
- The Excel row owns the user-to-boundary assignment (`boundaryCode` column)
- The Redis cache maps `boundaryCode → projectId` for fast lookup (no API call per row)
- IDGen is called once in bulk, not once per batch
- Idempotency is always keyed on mobile number — not username

---

### 6.4 FacilitySaga

```
Input: campaignId, tenantId, facilityRows[] (from Excel processing)

Step 1: DEDUPLICATE_FACILITIES
  Bulk search existing facilities by code/name
  Classify rows:
    toCreate:       facility does not exist
    alreadyExists:  facility found → record existing UUID, skip create

Step 2: CREATE_FACILITIES
  For rows in toCreate:
    Chunks of FACILITY_BATCH_SIZE (default 100)
    POST to facility service
    Idempotency key: tenantId:facilityCode
  Compensate: Soft-delete created facilities
  Retry:      max 3

Step 3: MAP_FACILITIES_TO_PROJECTS
  For all facility rows (created + already existing):
    Look up projectId from Redis boundary-ctx using row's boundaryCode
    POST to /project/v1/facility/_create
  Idempotency key: tenantId:facilityUuid:projectId
  Compensate: DELETE /project/v1/facility/_delete
  Retry:      max 5
```

---

### 6.5 MappingReconcilerSaga

Replaces the current poll-based reconciler with an event-driven convergence loop.

```
Trigger: ResourceWorkerConsumer receives resource.event.users-created
         AND resource.event.facilities-created for same campaignId

Step 1: SNAPSHOT_DESIRED_STATE
  Read cms_mapping WHERE campaignId=? AND status=TO_BE_MAPPED

Step 2: SNAPSHOT_ACTUAL_STATE
  Bulk search health-project for existing mappings
  Chunks of MAPPING_PROJECT_SEARCH_CHUNK_SIZE (default 50 projects per call)
  Offset-paginated per chunk

Step 3: DIFF_AND_CLASSIFY
  missing  = desired - actual   → needs create
  orphan   = actual - desired   → needs demap
  matched  = desired ∩ actual   → mark MAPPED (adopt)

Step 4: APPLY_CREATES
  Batch create missing mappings
  Idempotency key per mapping type:
    user:     tenantId:staffUuid:projectId
    facility: tenantId:facilityUuid:projectId
  Retry: max 5

Step 5: APPLY_DEMAPS
  Delete orphan mappings (demap direction tracked as DE_MAP_FAILED on failure)
  Retry: max 3

Step 6: CONVERGE_CHECK
  If not fully converged AND cycle < MAX_RECONCILE_CYCLES (default 5):
    → back to Step 1 (next cycle)
  Else if converged:
    → emit {t}.cms.resource.event.mapping.applied
  Else (max cycles reached with failures):
    → classify as terminal failure
    → emit saga step FAILED event

Failure policy (applied at conclusion):
  Blocking:     facility/resource mapping terminally failed → campaign FAILED
  Non-blocking: user mapping terminally failed → campaign continues
  Non-blocking: individual mapping skipped → auto-resolved
```

---

### Saga State Machine

```
STARTED
  ↓ (first step begins)
RUNNING
  ↓ (all steps complete)         ↓ (unrecoverable failure)
COMPLETED                      COMPENSATING
                                  ↓ (all compensations done)
                                FAILED
```

Each step transition is atomic via optimistic locking on `cms_saga_instance.version`.  
If two consumers race on the same saga, only one wins the CAS update. The loser re-reads and retries.

---

## 7. Scalability Design

### 7.1 Throughput Math

**100,000 boundaries (Project creation):**
```
100,000 boundaries
÷ BOUNDARY_CHUNK_SIZE=500        → 200 Kafka messages
× BOUNDARY_CONSUMER_THREADS=20  → 20 parallel workers
Each worker: 500 ÷ 50 per API = 10 project/create calls
Total calls: 200 × 10 = 2,000
Parallel rounds: 2,000 ÷ 20 = 100 rounds × ~200ms = ~20 seconds
```

**30,000 users:**
```
Step 1 - IDGen: 1 API call (bulk) instead of 1,000
Step 2 - Boundary pre-fetch: ~60 API calls instead of 30,000
Step 3 - HRMS create:
  30,000 ÷ USER_BATCH_SIZE=100   → 300 Kafka messages
  × USER_CONSUMER_THREADS=30     → 30 parallel workers
  Each worker: 1 HRMS call + 1 project-staff call
  Parallel rounds: 300 ÷ 30 = 10 rounds × ~500ms = ~5 seconds
Previously (batch=30, concurrent=10): ~100 rounds × 500ms = ~50 seconds
Improvement: 10x faster
```

**15,000 facilities:**
```
15,000 ÷ FACILITY_BATCH_SIZE=100 → 150 Kafka messages
× FACILITY_CONSUMER_THREADS=20   → 20 parallel workers
Parallel rounds: 150 ÷ 20 = 8 rounds × ~400ms = ~3 seconds
```

### 7.2 Virtual Threads (Java 25)

```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerFactory() {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConcurrency(30); // safe with virtual threads — would OOM with platform threads
    factory.getContainerProperties().setListenerTaskExecutor(
        Executors.newVirtualThreadPerTaskExecutor()
    );
    return factory;
}
```

Platform threads at 30 concurrency = ~480 MB stack.  
Virtual threads at 30 concurrency = ~1 MB stack.  
This is why Node.js was capped at 10 concurrent consumers but Java 25 can safely run 50–100.

### 7.3 Streaming Excel Parsing (O(1) memory)

```java
// Old approach (ExcelJS / XSSFWorkbook): loads entire file into heap
// 50,000 rows → ~500MB heap spike → OOM at large files

// New approach: SXSSF event-based streaming
OPCPackage pkg = OPCPackage.open(inputStream);
XSSFReader reader = new XSSFReader(pkg);
SheetHandler handler = new SheetHandler(row -> processRow(row)); // one row at a time
XMLReader parser = XMLReaderFactory.createXMLReader();
parser.setContentHandler(new XSSFSheetXMLHandler(styles, handler, false));
parser.parse(sheetSource);
// Memory: constant regardless of file size
```

### 7.4 Boundary Context Pre-fetch

```java
// Called ONCE at the start of UserCreationSaga (before any batching)
public Map<String, String> buildBoundaryContext(String campaignId, String tenantId) {
    List<String> codes = campaignRepo.getAllUniqueBoundaryCodes(campaignId);
    // codes come from Excel rows (user-to-boundary assignment is in the Excel)

    Map<String, String> boundaryToProjectId = new HashMap<>();
    Lists.partition(codes, 50).forEach(chunk ->
        boundaryClient.search(chunk, tenantId)
                      .forEach(b -> boundaryToProjectId.put(b.getCode(), b.getProjectId()))
    );

    redisTemplate.opsForValue().set(
        "cms:boundary-ctx:" + campaignId,
        boundaryToProjectId,
        Duration.ofHours(24)
    );
    return boundaryToProjectId;
}
// Result: ~60 API calls for 100k boundaries instead of 30,000 per-row calls
```

---

## 8. Database Schema

### Naming convention: `cms_{domain}_{entity}`

```sql
-- ─────────────────────────────────────────────
-- V001: Campaign core tables
-- ─────────────────────────────────────────────

CREATE TABLE cms_campaign (
    id                  VARCHAR(64)  PRIMARY KEY,
    campaign_number     VARCHAR(64)  NOT NULL UNIQUE,
    tenant_id           VARCHAR(64)  NOT NULL,
    project_type        VARCHAR(64)  NOT NULL,
    campaign_name       VARCHAR(256) NOT NULL,
    hierarchy_type      VARCHAR(64)  NOT NULL,
    start_date          BIGINT       NOT NULL,
    end_date            BIGINT       NOT NULL,
    status              VARCHAR(32)  NOT NULL,   -- DRAFTED, CREATING, ACTIVE, FAILED, CANCELLED
    action              VARCHAR(32)  NOT NULL,   -- DRAFT, CREATE
    boundaries          JSONB,
    resources           JSONB,
    delivery_rules      JSONB,
    additional_details  JSONB,
    boundary_code       VARCHAR(64),
    is_deleted          BOOLEAN      DEFAULT FALSE,
    created_by          VARCHAR(64),
    last_modified_by    VARCHAR(64),
    created_time        BIGINT,
    last_modified_time  BIGINT
);
CREATE INDEX ON cms_campaign (tenant_id, status);
CREATE INDEX ON cms_campaign (campaign_number);

CREATE TABLE cms_campaign_resource (
    id              VARCHAR(64) PRIMARY KEY,
    campaign_id     VARCHAR(64) NOT NULL REFERENCES cms_campaign(id),
    tenant_id       VARCHAR(64) NOT NULL,
    type            VARCHAR(64) NOT NULL,   -- TEMPLATE, STATUS_FILE
    file_store_id   VARCHAR(128),
    filename        VARCHAR(256),
    status          VARCHAR(32) NOT NULL,   -- ACTIVE, EXPIRED, FAILED
    is_deleted      BOOLEAN     DEFAULT FALSE,
    created_time    BIGINT,
    last_modified_time BIGINT
);
CREATE INDEX ON cms_campaign_resource (campaign_id, type, status);

-- ─────────────────────────────────────────────
-- V002: Row-level data from Excel
-- ─────────────────────────────────────────────

CREATE TABLE cms_campaign_row_data (
    id                      VARCHAR(64) PRIMARY KEY,
    campaign_id             VARCHAR(64) NOT NULL,
    campaign_number         VARCHAR(64) NOT NULL,
    tenant_id               VARCHAR(64) NOT NULL,
    type                    VARCHAR(32) NOT NULL,   -- USER, FACILITY, BOUNDARY
    row_index               INT         NOT NULL,
    boundary_code           VARCHAR(64),
    status                  VARCHAR(32) NOT NULL,   -- PENDING, COMPLETED, FAILED
    unique_id_after_process VARCHAR(64),            -- UUID returned from downstream after creation
    data                    JSONB,                  -- raw row data from Excel
    is_deleted              BOOLEAN     DEFAULT FALSE,
    created_time            BIGINT,
    last_modified_time      BIGINT
);
CREATE INDEX ON cms_campaign_row_data (campaign_id, type, status);
CREATE INDEX ON cms_campaign_row_data (campaign_number, type);

CREATE TABLE cms_campaign_row_error (
    id           VARCHAR(64) PRIMARY KEY,
    campaign_id  VARCHAR(64) NOT NULL,
    tenant_id    VARCHAR(64) NOT NULL,
    row_index    INT         NOT NULL,
    type         VARCHAR(32),
    column_name  VARCHAR(128),
    error_code   VARCHAR(64),
    error_message TEXT,
    created_time BIGINT
);
CREATE INDEX ON cms_campaign_row_error (campaign_id);

-- ─────────────────────────────────────────────
-- V003: Saga engine tables
-- ─────────────────────────────────────────────

CREATE TABLE cms_saga_instance (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id         VARCHAR(64) NOT NULL,
    tenant_id           VARCHAR(64) NOT NULL,
    saga_type           VARCHAR(64) NOT NULL,
    -- CAMPAIGN_CREATION, PROJECT_CREATION, USER_CREATION,
    -- FACILITY_CREATION, MAPPING_RECONCILER
    status              VARCHAR(32) NOT NULL,
    -- STARTED, RUNNING, COMPLETED, COMPENSATING, FAILED
    current_step        VARCHAR(64),
    last_completed_step VARCHAR(64),
    retry_count         INT         DEFAULT 0,
    payload             JSONB,
    error_details       JSONB,
    started_at          BIGINT      NOT NULL,
    updated_at          BIGINT      NOT NULL,
    completed_at        BIGINT,
    version             INT         DEFAULT 0,      -- optimistic lock
    CONSTRAINT uc_saga_campaign_type UNIQUE (campaign_id, saga_type)
);
CREATE INDEX ON cms_saga_instance (campaign_id);
CREATE INDEX ON cms_saga_instance (status, saga_type);

CREATE TABLE cms_saga_event (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_id     UUID        NOT NULL REFERENCES cms_saga_instance(id),
    campaign_id VARCHAR(64) NOT NULL,
    step_name   VARCHAR(64) NOT NULL,
    event_type  VARCHAR(32) NOT NULL,
    -- STEP_STARTED, STEP_COMPLETED, STEP_FAILED, COMPENSATED
    payload     JSONB,
    occurred_at BIGINT      NOT NULL
);
CREATE INDEX ON cms_saga_event (saga_id);

CREATE TABLE cms_saga_dead_letter (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_id     UUID,
    campaign_id VARCHAR(64),
    tenant_id   VARCHAR(64),
    step_name   VARCHAR(64),
    error_type  VARCHAR(128),
    error_msg   TEXT,
    payload     JSONB,
    occurred_at BIGINT      NOT NULL,
    resolved    BOOLEAN     DEFAULT FALSE
);

-- ─────────────────────────────────────────────
-- V004: Idempotency registry
-- ─────────────────────────────────────────────

CREATE TABLE cms_idempotency_registry (
    idempotency_key VARCHAR(256) PRIMARY KEY,
    entity_type     VARCHAR(64)  NOT NULL,   -- USER, FACILITY, PROJECT, MAPPING
    entity_id       VARCHAR(64)  NOT NULL,   -- UUID from downstream service
    tenant_id       VARCHAR(64)  NOT NULL,
    campaign_id     VARCHAR(64)  NOT NULL,
    created_at      BIGINT       NOT NULL,
    expires_at      BIGINT                   -- TTL-based cleanup (7 days default)
);
CREATE INDEX ON cms_idempotency_registry (campaign_id, entity_type);

-- ─────────────────────────────────────────────
-- V005: Mapping desired state
-- ─────────────────────────────────────────────

CREATE TABLE cms_mapping (
    id                 VARCHAR(64) PRIMARY KEY,
    campaign_id        VARCHAR(64) NOT NULL,
    campaign_number    VARCHAR(64) NOT NULL,
    tenant_id          VARCHAR(64) NOT NULL,
    type               VARCHAR(32) NOT NULL,   -- USER, FACILITY
    parent_resource_id VARCHAR(64),            -- staffId or facilityId
    project_id         VARCHAR(64),
    boundary_code      VARCHAR(64),
    direction          VARCHAR(32) NOT NULL,
    -- TO_BE_MAPPED, MAPPED, DE_MAP_FAILED, DE_MAPPED
    status             VARCHAR(32) NOT NULL,
    -- TO_BE_MAPPED, MAPPED, FAILED, SKIPPED
    mapping_id         VARCHAR(64),            -- ID from health-project after creation
    retry_count        INT         DEFAULT 0,
    last_error         TEXT,
    generation         INT         DEFAULT 0,  -- for generation fencing in Redis
    is_deleted         BOOLEAN     DEFAULT FALSE,
    created_time       BIGINT,
    last_modified_time BIGINT
);
CREATE INDEX ON cms_mapping (campaign_id, type, status);
CREATE INDEX ON cms_mapping (campaign_number);

-- ─────────────────────────────────────────────
-- V006: Excel job tracking
-- ─────────────────────────────────────────────

CREATE TABLE cms_excel_job (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id     VARCHAR(64),
    tenant_id       VARCHAR(64) NOT NULL,
    job_type        VARCHAR(32) NOT NULL,   -- GENERATE, PROCESS
    status          VARCHAR(32) NOT NULL,   -- QUEUED, PROCESSING, COMPLETED, FAILED
    file_store_id   VARCHAR(128),
    resource_type   VARCHAR(64),            -- USER, FACILITY, BOUNDARY
    total_rows      INT,
    processed_rows  INT         DEFAULT 0,
    error_count     INT         DEFAULT 0,
    created_at      BIGINT      NOT NULL,
    updated_at      BIGINT      NOT NULL
);
CREATE INDEX ON cms_excel_job (campaign_id, job_type, status);

CREATE TABLE cms_excel_row_staging (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id        UUID        NOT NULL REFERENCES cms_excel_job(id),
    campaign_id   VARCHAR(64),
    tenant_id     VARCHAR(64) NOT NULL,
    row_index     INT         NOT NULL,
    row_data      JSONB,
    errors        JSONB,
    created_time  BIGINT
);
CREATE INDEX ON cms_excel_row_staging (job_id);
CREATE INDEX ON cms_excel_row_staging (campaign_id);

-- ─────────────────────────────────────────────
-- V007: Process tracking
-- ─────────────────────────────────────────────

CREATE TABLE cms_process (
    id           VARCHAR(64) PRIMARY KEY,
    campaign_id  VARCHAR(64) NOT NULL,
    tenant_id    VARCHAR(64) NOT NULL,
    process_name VARCHAR(64) NOT NULL,
    type         VARCHAR(32),
    status       VARCHAR(32) NOT NULL,   -- PENDING, COMPLETED, FAILED
    is_deleted   BOOLEAN     DEFAULT FALSE,
    created_time BIGINT,
    last_modified_time BIGINT
);
CREATE INDEX ON cms_process (campaign_id, status);
```

---

## 9. Kafka Topic Design

### Naming convention
```
{tenantPrefix}.cms.{domain}.{entity}.{action}
```

In central instance mode: `ng.cms.campaign.command.draft`  
In non-central mode: `cms.campaign.command.draft`

### All topics

```
# Campaign lifecycle
{t}.cms.campaign.command.draft
{t}.cms.campaign.command.create
{t}.cms.campaign.event.drafted
{t}.cms.campaign.event.status.changed

# Saga coordination
{t}.cms.saga.command.start
{t}.cms.saga.command.step.execute
{t}.cms.saga.command.step.compensate
{t}.cms.saga.event.step.completed
{t}.cms.saga.event.step.failed
{t}.cms.saga.event.completed
{t}.cms.saga.event.failed

# Excel
{t}.cms.excel.command.generate
{t}.cms.excel.command.process
{t}.cms.excel.event.generation.completed
{t}.cms.excel.event.processing.completed

# Resource provisioners (chunked parallel work)
{t}.cms.resource.command.project.create
{t}.cms.resource.command.user.create
{t}.cms.resource.command.facility.create
{t}.cms.resource.command.mapping.apply
{t}.cms.resource.event.project.created
{t}.cms.resource.event.user.created
{t}.cms.resource.event.facility.created
{t}.cms.resource.event.mapping.applied

# Dead letter queue
{t}.cms.dlq
```

### Consumer group strategy

| Consumer Group | Concurrency | Handles |
|---|---|---|
| `cms-campaign-saga-coordinator` | 5 | Low-volume saga state transitions |
| `cms-project-provisioner` | 20 | Project create chunks |
| `cms-user-provisioner` | 30 | User create chunks (highest volume) |
| `cms-facility-provisioner` | 20 | Facility create chunks |
| `cms-mapping-reconciler` | 15 | Mapping convergence |
| `cms-excel-processor` | 10 | Excel parsing (CPU-heavy) |
| `cms-excel-generator` | 5 | Template generation (I/O-heavy) |

> **Naming rule:** Never use "worker" in consumer group IDs — `worker-registry` is a separate HCM service and "worker" in group names causes confusion in monitoring dashboards and Kafka consumer lag metrics. Use: `provisioner` (creates entities), `reconciler` (converges state), `coordinator` (saga state machine), `processor`/`generator` (Excel).

All consumer groups use virtual thread executors.

### Multi-tenancy topic prefix

```java
// Producer
String topic = tenantId + "." + BASE_TOPIC;   // ng.cms.campaign.command.create

// Consumer subscription (regex matches all tenant prefixes)
Pattern pattern = Pattern.compile(".*\\.cms\\.campaign\\.command\\.create");

// Startup: pre-create all tenant-prefixed topics
tenantIds.forEach(t -> admin.createTopic(t + "." + BASE_TOPIC));
```

---

## 10. Project File Structure

```
campaign-management-service/
│
├── pom.xml
├── Dockerfile
├── README.md
│
└── src/
    ├── main/
    │   ├── java/org/egov/cms/
    │   │   │
    │   │   ├── CampaignManagementApplication.java
    │   │   │
    │   │   ├── api/                             ← HTTP boundary only. No business logic.
    │   │   │   ├── campaign/
    │   │   │   │   ├── CampaignController.java  (_create, _update, _search, _download)
    │   │   │   │   ├── CampaignRequest.java
    │   │   │   │   └── CampaignResponse.java
    │   │   │   ├── excel/
    │   │   │   │   ├── ExcelController.java     (generate, process)
    │   │   │   │   ├── GenerateRequest.java
    │   │   │   │   └── ProcessRequest.java
    │   │   │   └── admin/
    │   │   │       └── SagaAdminController.java (status, retry, dlq)
    │   │   │
    │   │   ├── domain/                          ← Zero framework dependencies.
    │   │   │   │                                   Pure Java. Fully unit-testable.
    │   │   │   ├── campaign/
    │   │   │   │   ├── Campaign.java
    │   │   │   │   ├── CampaignStatus.java      (enum: DRAFTED, CREATING, ACTIVE,
    │   │   │   │   │                                    FAILED, CANCELLED)
    │   │   │   │   ├── CampaignAction.java      (enum: DRAFT, CREATE)
    │   │   │   │   ├── Boundary.java
    │   │   │   │   ├── Resource.java
    │   │   │   │   └── DeliveryRule.java
    │   │   │   │
    │   │   │   ├── saga/
    │   │   │   │   ├── core/
    │   │   │   │   │   ├── Saga.java            (interface: define steps)
    │   │   │   │   │   ├── SagaStep.java        (interface: execute + compensate)
    │   │   │   │   │   ├── SagaContext.java     (per-execution state bag)
    │   │   │   │   │   ├── SagaInstance.java    (DB-persisted state)
    │   │   │   │   │   ├── SagaStatus.java      (enum)
    │   │   │   │   │   ├── StepResult.java      (enum: SUCCESS, RETRY, FAIL)
    │   │   │   │   │   └── RetryPolicy.java
    │   │   │   │   │
    │   │   │   │   └── flows/
    │   │   │   │       ├── CampaignCreationSaga.java
    │   │   │   │       ├── ProjectCreationSaga.java
    │   │   │   │       ├── UserCreationSaga.java
    │   │   │   │       ├── FacilityCreationSaga.java
    │   │   │   │       └── MappingReconcilerSaga.java
    │   │   │   │
    │   │   │   ├── excel/
    │   │   │   │   ├── generation/
    │   │   │   │   │   ├── ExcelGenerationService.java
    │   │   │   │   │   ├── SheetGenerator.java         (interface)
    │   │   │   │   │   ├── UserSheetGenerator.java
    │   │   │   │   │   ├── FacilitySheetGenerator.java
    │   │   │   │   │   └── BoundarySheetGenerator.java
    │   │   │   │   └── processing/
    │   │   │   │       ├── ExcelProcessingService.java
    │   │   │   │       ├── SheetProcessor.java         (interface)
    │   │   │   │       ├── UserSheetProcessor.java
    │   │   │   │       └── FacilitySheetProcessor.java
    │   │   │   │
    │   │   │   └── mapping/
    │   │   │       ├── MappingDesiredState.java
    │   │   │       ├── MappingActualState.java
    │   │   │       ├── MappingDiff.java
    │   │   │       └── MappingStatus.java              (enum)
    │   │   │
    │   │   ├── application/                     ← Orchestrates domain objects.
    │   │   │   │                                   No HTTP/Kafka details here.
    │   │   │   ├── CampaignApplicationService.java
    │   │   │   ├── SagaOrchestrator.java
    │   │   │   ├── ExcelApplicationService.java
    │   │   │   └── IdempotencyService.java
    │   │   │
    │   │   ├── infrastructure/
    │   │   │   │
    │   │   │   ├── kafka/
    │   │   │   │   ├── config/
    │   │   │   │   │   ├── KafkaTopicConfig.java
    │   │   │   │   │   └── MultiTenantTopicResolver.java
    │   │   │   │   ├── producer/
    │   │   │   │   │   └── CampaignEventProducer.java
    │   │   │   │   └── consumer/
    │   │   │   │       ├── CampaignCommandConsumer.java
    │   │   │   │       ├── SagaEventConsumer.java
    │   │   │   │       ├── ResourceWorkerConsumer.java
    │   │   │   │       └── ExcelJobConsumer.java
    │   │   │   │
    │   │   │   ├── persistence/
    │   │   │   │   ├── CampaignRepository.java
    │   │   │   │   ├── SagaRepository.java
    │   │   │   │   ├── MappingRepository.java
    │   │   │   │   ├── ExcelJobRepository.java
    │   │   │   │   └── IdempotencyRepository.java
    │   │   │   │
    │   │   │   ├── clients/                     ← One file per external service.
    │   │   │   │   ├── HrmsClient.java          All wrapped with circuit breaker.
    │   │   │   │   ├── ProjectClient.java
    │   │   │   │   ├── FacilityClient.java
    │   │   │   │   ├── BoundaryClient.java
    │   │   │   │   ├── IdGenClient.java
    │   │   │   │   ├── MdmsClient.java
    │   │   │   │   ├── LocalizationClient.java
    │   │   │   │   └── FileStoreClient.java
    │   │   │   │
    │   │   │   └── cache/
    │   │   │       ├── BoundaryContextCache.java
    │   │   │       └── MdmsSchemaCache.java
    │   │   │
    │   │   └── config/
    │   │       ├── AppConfig.java
    │   │       ├── KafkaConfig.java
    │   │       ├── ResilienceConfig.java         (circuit breakers, retry policies)
    │   │       ├── VirtualThreadConfig.java
    │   │       └── MultiTenantConfig.java
    │   │
    │   └── resources/
    │       ├── application.yml
    │       └── db/
    │           └── migration/
    │               ├── V001__create_campaign_tables.sql
    │               ├── V002__create_row_data_tables.sql
    │               ├── V003__create_saga_tables.sql
    │               ├── V004__create_idempotency_table.sql
    │               ├── V005__create_mapping_table.sql
    │               ├── V006__create_excel_job_tables.sql
    │               └── V007__create_process_table.sql
    │
    └── test/
        └── java/org/egov/cms/
            ├── integration/
            │   ├── CampaignDraftIntegrationTest.java
            │   ├── CampaignCreationIntegrationTest.java
            │   ├── UserSagaIntegrationTest.java
            │   ├── FacilitySagaIntegrationTest.java
            │   └── MappingReconcilerIntegrationTest.java
            └── unit/
                ├── saga/
                │   ├── SagaOrchestratorTest.java
                │   ├── UserCreationSagaTest.java
                │   └── MappingReconcilerSagaTest.java
                └── excel/
                    ├── UserSheetProcessorTest.java
                    └── FacilitySheetProcessorTest.java
```

### Layering rules (strictly enforced)

| Layer | Owns | Must not |
|---|---|---|
| `api/` | Parse HTTP request, call application service, return response | Business logic, DB calls, Kafka produce |
| `application/` | Orchestrate domain objects for one use case | Import Spring MVC objects (HttpServletRequest etc.) |
| `domain/` | Business logic, saga definitions, Excel logic | Import Spring beans, Kafka, JDBC |
| `infrastructure/kafka/` | Connect, subscribe, route to application service | Business decisions |
| `infrastructure/persistence/` | SQL queries only | Business logic |
| `infrastructure/clients/` | HTTP to one external service | Business decisions, error handling beyond normalization |
| `infrastructure/cache/` | Read/write Redis | Business logic |

---

## 11. Key Implementation Patterns

### 11.1 Idempotency — Eliminating Ghost Creates

```java
// Every downstream create goes through this
public String createWithIdempotency(String idempotencyKey, String entityType,
                                     Supplier<String> createFn) {
    // 1. Check registry
    Optional<String> existing = idempotencyRepo.findEntityId(idempotencyKey);
    if (existing.isPresent()) {
        return existing.get(); // already created — return existing UUID
    }

    // 2. Create downstream
    String entityId = createFn.get();

    // 3. Register (TTL = 7 days)
    idempotencyRepo.register(idempotencyKey, entityType, entityId,
                              System.currentTimeMillis() + Duration.ofDays(7).toMillis());
    return entityId;
}

// Usage in UserCreationSaga:
// idempotencyKey = tenantId + ":" + mobileNumber
// entityType = "USER"
```

### 11.2 Optimistic Locking on Saga State

```java
// Atomic step advance — prevents two Kafka consumers racing on same saga
public boolean advanceSagaStep(UUID sagaId, String fromStep,
                                String toStep, int expectedVersion) {
    int updated = jdbcTemplate.update("""
        UPDATE cms_saga_instance
        SET current_step = ?, last_completed_step = ?,
            version = version + 1, updated_at = ?
        WHERE id = ? AND current_step = ? AND version = ?
        """,
        toStep, fromStep, System.currentTimeMillis(),
        sagaId, fromStep, expectedVersion
    );
    return updated == 1; // false = lost the race, caller re-reads and retries
}
```

### 11.3 Circuit Breakers per Downstream Service

```java
@Configuration
public class ResilienceConfig {
    @Bean
    public Customizer<CircuitBreakerRegistry> circuitBreakerCustomizer() {
        return registry -> {
            // HRMS: strict (slow, expensive)
            registry.addConfiguration("hrms", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());

            // Boundary: lenient (fast, critical path)
            registry.addConfiguration("boundary", CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .failureRateThreshold(70)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .build());
        };
    }
}
```

### 11.4 Bulk IDGen (User Creation)

```java
// Step 1 of UserCreationSaga
public void assignUsernames(List<UserRow> rows, String tenantId) {
    List<UserRow> needsUsername = rows.stream()
        .filter(r -> r.getUsername() == null || r.getUsername().isBlank())
        .toList();

    if (needsUsername.isEmpty()) return;

    // ONE bulk call — not one per batch
    List<String> usernames = idGenClient.bulkGenerate(
        "campaign.user.username", tenantId, needsUsername.size()
    );

    for (int i = 0; i < needsUsername.size(); i++) {
        needsUsername.get(i).setUsername(usernames.get(i));
    }
    // rows with user-provided usernames are untouched
}
```

---

## 12. Implementation Roadmap

### Phase 0 — Foundation (Week 1–2)

**Goal:** Empty Java service that compiles, connects to Kafka and DB, health check passes.

- [ ] Init Spring Boot 3.3 + Java 25 project (`campaign-orchestrator`)
- [ ] Add dependencies: Resilience4j, Apache POI 5.4.1, Testcontainers, OpenTelemetry
- [ ] Flyway migrations V001–V007 (all tables)
- [ ] Port multi-tenant Kafka topic prefix logic (from TypeScript `kafkaTopicUtils.ts`)
- [ ] Port multi-tenant DB schema routing (`getTableName` equivalent)
- [ ] Stub all external service clients (circuit breakers wired, no real impl)
- [ ] Integration test harness (Testcontainers: Postgres + Kafka)
- [ ] `./mvnw test` passes · health check returns 200

**Deliverable:** Running service, wired to infra, no business logic yet.

---

### Phase 1 — Campaign CRUD + Draft Flow (Week 3–4)

**Goal:** Draft create/update/search works end-to-end. No saga yet.

- [ ] `CampaignRepository` — CRUD on `cms_campaign`
- [ ] `POST /v1/campaigns/_create` (action=draft) — persist + emit generate event
- [ ] `POST /v1/campaigns/_update` (action=draft) — enrich + boundary-change detection
- [ ] `POST /v1/campaigns/_search`
- [ ] Embed Excel generation module (port from excel-ingestion, wire to generate topic)
- [ ] Boundary-change → expire old resource + regenerate template
- [ ] **Spike test:** Does HRMS accept bulk of 100 employees? (answer before Phase 4)

**Deliverable:** Frontend can draft a campaign. Excel template generates and downloads.

---

### Phase 2 — Saga Engine Core (Week 5–6)

**Goal:** Generic orchestrator that runs any saga definition.

- [ ] `SagaOrchestrator` — step execution, event emit, state persist
- [ ] Optimistic locking on `cms_saga_instance.version`
- [ ] `SagaStep` interface — `execute()`, `compensate()`, `isIdempotent()`
- [ ] Retry mechanism — exponential backoff, max retries configurable per step
- [ ] Dead letter queue — unrecoverable failures → `cms_saga_dead_letter`
- [ ] `GET /admin/saga/status/{campaignId}` — debug endpoint
- [ ] `POST /admin/saga/retry/{sagaId}` — manual recovery endpoint
- [ ] Unit test the engine with a dummy 3-step saga (happy path + compensation)

**Deliverable:** Saga engine runs, retries, compensates, persists state correctly.

---

### Phase 3 — Project Creation Saga (Week 7)

**Goal:** Campaign create triggers project creation at 100k boundary scale.

- [ ] `ProjectCreationSaga` — chunk boundaries → create in parallel
- [ ] `ProjectClient` — POST /project/v1/_create with circuit breaker
- [ ] Idempotency key: `tenantId:campaignNumber:boundaryCode`
- [ ] Wire `POST /v1/campaigns/_create (action=create)` → start `CampaignCreationSaga`
- [ ] Load test: 100k boundaries → measure time, memory, error rate
- [ ] Target: < 5 minutes, zero duplicates on retry

**Deliverable:** 100k boundary campaign creates all projects safely.

---

### Phase 4 — User Creation Saga (Week 8–9)

**Goal:** 30k users created without IDGen bottleneck or ghost users.

- [ ] Bulk IDGen call (all usernames in one request before batching)
- [ ] Username split logic (user-provided vs IDGen-generated)
- [ ] Boundary context pre-fetch + Redis cache (`BoundaryContextCache`)
- [ ] `UserCreationSaga` — 5 steps (IDGen → pre-fetch → HRMS → worker-registry → project-staff)
- [ ] `HrmsClient` — batch of 100, idempotency + circuit breaker
- [ ] Streaming Excel parse for user sheet (SXSSF)
- [ ] Wire `UserCreationSaga` as step 3 of `CampaignCreationSaga`
- [ ] Load test: 30k users end-to-end
- [ ] Chaos test: kill HRMS mid-saga → verify no ghost users on retry
- [ ] Target: < 5 minutes, idempotent retry

**Deliverable:** 30k users created safely and quickly.

---

### Phase 5 — Facility Creation Saga (Week 10)

**Goal:** 15k facilities with deduplication.

- [ ] `FacilitySaga` — deduplicate → create → map to projects
- [ ] `FacilityClient` — idempotency key: `tenantId:facilityCode`
- [ ] Wire as step 4 of `CampaignCreationSaga`
- [ ] Load test: 15k facilities, 30% already exist → correct deduplication
- [ ] Target: < 3 minutes

**Deliverable:** 15k facilities created safely, zero duplicates.

---

### Phase 6 — Mapping Reconciler Saga (Week 11)

**Goal:** Event-driven mapping convergence (replaces poll loop).

- [ ] `MappingReconcilerSaga` — snapshot → diff → create → demap → converge-check
- [ ] Adoption pre-pass (search health-project before creating, adopt existing)
- [ ] Generation fencing with Redis key (drop stale batches)
- [ ] Wire as step 5 of `CampaignCreationSaga` (after users and facilities complete)
- [ ] Test: partial failure mid-reconcile → resume from correct point
- [ ] Test: convergence within 2 cycles for 100k × 30k

**Deliverable:** Mapping converges reliably at full scale.

---

### Phase 7 — Excel Ingestion Full Embed (Week 12)

**Goal:** excel-ingestion service fully replaced by embedded module.

- [ ] Port all generators (User, Facility, Boundary, Attendance)
- [ ] Port all processors with streaming parser
- [ ] Replace `XSSFWorkbook` load-all with SXSSF streaming throughout
- [ ] `POST /v1/campaigns/_download` — generate and return status Excel
- [ ] Run parallel with old excel-ingestion for 1 week → compare outputs
- [ ] Decommission old excel-ingestion service

**Deliverable:** excel-ingestion removed from deployment.

---

### Phase 8 — Production Hardening (Week 13–14)

**Goal:** Observable, resilient, production-ready at full scale.

- [ ] Micrometer metrics: saga step duration, retry counts, DLQ queue size
- [ ] OpenTelemetry distributed tracing (Jaeger-compatible)
- [ ] `GET /v1/campaigns/{id}/progress` — user-facing saga progress endpoint
- [ ] Full load test: 100k boundaries + 30k users + 15k facilities simultaneously
- [ ] Chaos test suite:
  - Kill HRMS mid-user-saga
  - Kill DB during saga step advance
  - Kafka broker restart mid-campaign
  - Redis eviction of boundary cache mid-saga
- [ ] Write runbook: DLQ resolution, manual saga retry, partial failure recovery
- [ ] Decommission TypeScript project-factory service

**Deliverable:** Production deploy. Old services decommissioned.

---

## 13. Technology Decisions

| Decision | Choice | Reason |
|---|---|---|
| Language | Java 25 | Virtual threads stable, Spring ecosystem, type-safe, CPU-concurrent |
| Saga pattern | Orchestrated (central coordinator) | Visibility, explicit compensation, easy partial retry |
| Concurrency | Virtual threads (Project Loom) | 50–100 concurrent Kafka consumers at ~1MB stack total |
| Excel parsing | SXSSF streaming (Apache POI 5.4+) | O(1) memory — 100k rows = same heap as 100 rows |
| Idempotency | DB registry with TTL | Eliminates ghost creates on retry, 7-day retry window |
| Resilience | Resilience4j | Per-service circuit breakers, standard Spring ecosystem |
| Excel ingestion | Embedded module | No HTTP round-trip, shared transaction, one deployment |
| Caching | Redis (existing) | Boundary context, MDMS schema, same infra |
| Multi-tenancy | Kafka prefix + DB schema routing | Same contract as existing TypeScript service |
| Migrations | Flyway | Versioned, reviewable, reproducible |
| Test infra | Testcontainers (Postgres + Kafka) | Real infra in tests, no mocking of DB/Kafka |
| Metrics | Micrometer + Prometheus | Standard Spring Boot observability |
| Tracing | OpenTelemetry → Jaeger | Distributed tracing across sagas |

---

## 14. Confidence Assessment

| Area | Confidence | Notes |
|---|---|---|
| Scalability (virtual threads, streaming, bulk IDGen, pre-fetch) | 95% | Proven patterns. Math on batch sizing is solid. |
| Saga orchestration design | 85% | Sound pattern. Risk is in edge cases: optimistic lock contention, DB failure mid-step, exact compensation ordering. Needs integration tests. |
| Idempotency eliminating ghost creates | 90% | Reliable IF every create path goes through the registry. 10% risk = a code path that bypasses it. Needs strict code review. |
| Mapping reconciler (event-driven) | 80% | Convergence logic is the most complex part. Generation fencing adds a failure mode. Budget extra time. |
| Excel ingestion embedded | 90% | SXSSF streaming is well-tested. Risk: MDMS-driven dynamic class loading must be wired carefully in Spring. |
| Multi-tenancy Kafka prefix | 88% | Logic well-understood from TypeScript. Port is mechanical but regex subscription + topic pre-creation is subtle. Test with 5+ tenants. |
| Full scale end-to-end (100k/30k/15k) | 75% | Design is solid. Surprises always happen at this scale. HRMS bulk limits unknown. Filestore upload of 100k-row status Excel may timeout. Phase 8 load test is not optional. |
| **Overall** | **85%** | The 15% gap is honest — between design and production-tested system. Closes with Phase 8. |

### Known risks to resolve early (before Phase 4)

1. **HRMS batch limit** — Does health-hrms accept 100 employees in one call? Run a spike test in Week 1.
2. **Saga + DB failure** — If DB goes down after HRMS create but before idempotency registry write, ghost user is created. May need outbox pattern at this specific point.
3. **Boundary cache invalidation** — If a boundary is reassigned during an in-flight saga, cached `boundaryCode → projectId` is stale. TTL must be short enough or a cache-bust event must exist.

---

## 15. Contracts That Must Not Change

The following are consumed by external systems. **Do not break them.**

| Contract | What to preserve |
|---|---|
| REST API paths | `/v1/campaigns/_create`, `/_update`, `/_search`, `/_download` |
| Campaign status values | `DRAFTED`, `CREATING`, `ACTIVE`, `FAILED`, `CANCELLED` (case may change to upper) |
| Excel sheet format | Sheet names, column names, locked README sheet, boundary dropdown structure |
| MDMS config structure | `HCM-ADMIN-CONSOLE.schemas` schema names and processor class names |
| Multi-tenant Kafka prefix | `{tenantId}.{baseTopic}` format |
| fileStoreId contract | Upload returns fileStoreId, download accepts fileStoreId |
| egov-persister topics | Any topic consumed by egov-persister must keep same structure |

---

*This document is the single source of truth for the campaign-management-service migration.  
Update it as implementation decisions are made. Do not let it drift from the actual code.*
