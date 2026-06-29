# Campaign Orchestrator

Unified Java microservice for campaign creation and management in HCM (Health Campaign Management).

**Replaces:**
- `project-factory` (TypeScript/Node.js) — campaign orchestration
- `excel-ingestion` (Java, separate service) — Excel template generation and parsing

**Target scale:** 100,000 boundaries · 30,000 users · 15,000 facilities per campaign

---

## HCM Platform Context

This service is part of the eGov Health Campaign Management platform. It follows the same patterns as `household`, `individual`, `project`, and `health-notification-service`.

### Shared Libraries Used

| Library | Version | What it provides |
|---|---|---|
| `health-services-common` | 1.1.3-SNAPSHOT | `Producer`, `GenericRepository`, `ServiceRequestClient`, `MultiStateInstanceUtil`, `IdGenService` |
| `health-services-models` | 1.0.30-SNAPSHOT | Shared domain models: `RequestInfo`, `AuditDetails`, `Individual`, `Project`, `Facility`, etc. |
| `digit-models` | 1.0.0-SNAPSHOT | MDMS request/response contracts |
| `tracer` | 2.9.2-SNAPSHOT | `CustomKafkaTemplate`, `TracerConfiguration`, `CustomException` |

### Key Platform Rules

**HTTP:** Use `ServiceRequestClient` from `health-services-common`. Never create a `RestTemplate` directly — it loses distributed tracing and DIGIT standard request headers.

**Kafka:** Extend `Producer` from `health-services-common`, mark `@Primary`. Use `producer.push(tenantId, topic, payload)` for all tenant-scoped messages. Never use `KafkaTemplate` directly.

**Multi-tenancy:** Handled by `MultiStateInstanceUtil`. All SQL queries use `{schema}` placeholder. All Kafka topics optionally prefixed with tenant schema name in central instance mode.

---

## Architecture

### Two-phase Campaign Lifecycle

**Phase 1 — Draft** (`action=draft`)  
Only stores campaign configuration. The only background work is generating an Excel template when boundaries are selected. No projects, users, or facilities are created.

**Phase 2 — Create** (`action=create`)  
Triggers the full `CampaignCreationSaga` which orchestrates:
1. Project creation (one per boundary node)
2. User creation (HRMS employees, worker registry, project-staff mappings)
3. Facility creation (with deduplication)
4. Mapping reconciliation (convergence between desired and actual state in `health-project`)
5. Resource assignment
6. Campaign finalization + status Excel generation

### Saga Pattern

The service uses an **orchestrated saga** (not choreography). A central `SagaOrchestrator` drives each step explicitly, persisting state in `cms_saga_instance`. Every step has:
- An `execute()` action (forward)
- A `compensate()` action (rollback on failure)
- Configurable retry with exponential backoff
- Idempotency via `cms_idempotency_registry`

### Scalability

| Mechanism | Why |
|---|---|
| Virtual threads (Java 21) | 30 concurrent user-creation consumers at ~1MB stack vs ~480MB for platform threads |
| SXSSF streaming Excel | O(1) memory for 100k-row files (vs OOM with XSSFWorkbook) |
| Bulk IDGen call | 1 API call for all usernames instead of 1 per batch |
| Boundary context pre-fetch | ~60 API calls for 100k boundaries instead of 30,000 per-row calls |
| Idempotency registry | Ghost-create-safe retry at every step |
| Resilience4j circuit breakers | Per-downstream-service, configurable failure thresholds |

---

## Central Instance Support

When `is.environment.central.instance=true`, one deployment manages multiple states.

**DB:** All queries use `{schema}` placeholder. `MultiStateInstanceUtil.replaceSchemaPlaceholder(sql, tenantId)` resolves it to the correct PostgreSQL schema (e.g. `ng`, `ba`).

**Kafka produce:** `producer.push(tenantId, baseTopic, payload)` automatically prefixes the topic:  
`"ng"` + `"cms.campaign.command.create"` → `"ng-cms.campaign.command.create"`

**Kafka consume:** All `@KafkaListener` use `topicPattern` with regex so one annotation matches all tenant variants:  
`".*cms\\.campaign\\.command\\.create"` matches `"cms.campaign.command.create"` and `"ng-cms.campaign.command.create"` and `"ba-cms.campaign.command.create"`

**Config:**
```properties
is.environment.central.instance=true
state.level.tenantid.length=1
state.schema.index.position.tenantid=0
```

---

## Project Structure

```
campaign-orchestrator/
├── CMS-Java-Migration-Design.md     ← Full design document (read before implementing)
├── CLAUDE.md                        ← Claude context (patterns, rules, anti-patterns)
├── pom.xml
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/org/egov/campaign/
    │   │   ├── CampaignOrchestratorApplication.java
    │   │   ├── api/                         ← HTTP in/out. No business logic.
    │   │   │   ├── campaign/
    │   │   │   ├── excel/
    │   │   │   └── admin/
    │   │   ├── domain/                      ← Zero framework deps. Pure Java.
    │   │   │   ├── campaign/                ← Campaign, CampaignStatus, Boundary, etc.
    │   │   │   ├── saga/
    │   │   │   │   ├── core/                ← Saga, SagaStep, SagaContext interfaces
    │   │   │   │   └── flows/               ← CampaignCreationSaga, UserCreationSaga, etc.
    │   │   │   ├── excel/
    │   │   │   │   ├── generation/          ← SheetGenerator interface + implementations
    │   │   │   │   └── processing/          ← SheetProcessor interface + implementations
    │   │   │   └── mapping/                 ← MappingDesiredState, MappingDiff, etc.
    │   │   ├── application/                 ← Orchestrates domain. No HTTP/Kafka details.
    │   │   │   ├── CampaignApplicationService.java
    │   │   │   ├── SagaOrchestrator.java
    │   │   │   ├── ExcelApplicationService.java
    │   │   │   └── IdempotencyService.java
    │   │   ├── infrastructure/
    │   │   │   ├── kafka/
    │   │   │   │   ├── config/              ← KafkaConsumerConfig (virtual thread factories)
    │   │   │   │   ├── producer/            ← CampaignEventProducer (extends Producer)
    │   │   │   │   └── consumer/            ← CampaignCommandConsumer, ResourceWorkerConsumer
    │   │   │   ├── persistence/             ← CampaignRepository, SagaRepository, etc.
    │   │   │   ├── clients/                 ← HrmsClient, ProjectClient, BoundaryClient, etc.
    │   │   │   └── cache/                   ← BoundaryContextCache, MdmsSchemaCache
    │   │   └── config/
    │   │       ├── MainConfiguration.java   ← Spring beans, Redis, ObjectMapper
    │   │       └── ServiceConfiguration.java ← All @Value injections
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/
    │           ├── V001__create_campaign_tables.sql
    │           ├── V002__create_saga_tables.sql
    │           └── V003__create_idempotency_and_mapping_tables.sql
    └── test/
        ├── integration/
        └── unit/
```

---

## Database Tables

| Table | Purpose |
|---|---|
| `cms_campaign` | Campaign metadata (replaces `eg_cm_campaign_details`) |
| `cms_campaign_resource` | Generated Excel files / status files |
| `cms_campaign_row_data` | Row-level data from Excel uploads |
| `cms_campaign_row_error` | Per-row validation errors |
| `cms_process` | Process/step tracking per campaign |
| `cms_saga_instance` | Saga state machine (one row per active saga) |
| `cms_saga_event` | Immutable audit log of saga step transitions |
| `cms_saga_dead_letter` | Unrecoverable failures requiring manual intervention |
| `cms_idempotency_registry` | Prevents ghost creates on retry |
| `cms_mapping` | Desired mapping state (replaces `eg_cm_campaign_mapping_data`) |
| `cms_excel_job` | Excel generation/processing job tracking |
| `cms_excel_row_staging` | Temporary staging for parsed Excel rows |

---

## Kafka Topics

All base topic names start with `cms.`. In central instance mode they are prefixed with the state schema (e.g. `ng-cms.campaign.command.create`).

| Topic | Direction | Consumer Group |
|---|---|---|
| `cms.campaign.command.draft` | inbound | `cms-campaign-saga-coordinator` |
| `cms.campaign.command.create` | inbound | `cms-campaign-saga-coordinator` |
| `cms.resource.command.project.create` | inbound | `cms-project-provisioner` |
| `cms.resource.command.user.create` | inbound | `cms-user-provisioner` |
| `cms.resource.command.facility.create` | inbound | `cms-facility-provisioner` |
| `cms.resource.command.mapping.apply` | inbound | `cms-mapping-reconciler` |
| `cms.excel.command.generate` | inbound | `cms-excel-generator` |
| `cms.excel.command.process` | inbound | `cms-excel-processor` |
| `cms.dlq` | outbound | — |

---

## Local Setup

### Prerequisites
- Java 25
- Maven 3.8+
- PostgreSQL 14+
- Kafka 3.x
- Redis 7+

### Run locally
```bash
cd campaign-orchestrator
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Run tests
```bash
mvn test                    # unit tests only
mvn test -Pintegration-tests  # includes Testcontainers integration tests (needs Docker)
```

### Build Docker image
```bash
mvn clean package -DskipTests
docker build -t campaign-orchestrator:latest .
```

---

## Implementation Status

See `CMS-Java-Migration-Design.md` for the full 8-phase roadmap.

| Phase | Scope | Status |
|---|---|---|
| 0 | Foundation — structure, config, Kafka wiring, DB migrations | ✅ Done |
| 1 | Campaign CRUD + Draft flow | Pending |
| 2 | Saga engine core | Pending |
| 3 | Project creation saga | Pending |
| 4 | User creation saga | Pending |
| 5 | Facility creation saga | Pending |
| 6 | Mapping reconciler saga | Pending |
| 7 | Excel ingestion embedded | Pending |
| 8 | Production hardening | Pending |
