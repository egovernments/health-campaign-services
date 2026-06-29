# campaign-orchestrator

> **For Claude:** Read this file before touching any code. Update it when patterns or contracts change. Keep under 600 lines.

---

## What This Service Does

Unified Java service replacing two TypeScript/Java services:
- `project-factory` (TypeScript/Node.js) — campaign orchestration
- `excel-ingestion` (Java, separate service) — Excel template generation and parsing

**Target scale:** 100,000 boundaries · 30,000 users · 15,000 facilities per campaign  
**Full design:** See `CMS-Java-Migration-Design.md` in this folder.

---

## HCM Shared Libraries — What We Use and Why

### 1. `health-services-common` (org.egov.common)

The most important library. Provides:

#### `Producer` — THE only Kafka producer to use
```java
// CORRECT — always extend Producer, mark @Primary
@Component("campaignEventProducer")
@Primary
public class CampaignEventProducer extends Producer {
    public CampaignEventProducer(CustomKafkaTemplate<String, Object> kafkaTemplate,
                                  MultiStateInstanceUtil multiStateInstanceUtil) {
        super(kafkaTemplate, multiStateInstanceUtil);
    }
}

// CORRECT usage — include tenantId for tenant-scoped messages
producer.push(tenantId, config.getCampaignCommandCreate(), payload);

// CORRECT usage — no tenantId for non-tenant-scoped messages (DLQ, admin)
producer.push(config.getDlqTopic(), payload);

// WRONG — never use KafkaTemplate directly
kafkaTemplate.send("topic", payload);  // NO — loses tracing + tenant routing
```

#### `MultiStateInstanceUtil` — central instance routing
```java
// Schema replacement in SQL queries
String sql = "SELECT * FROM {schema}.cms_campaign WHERE tenant_id = :tenantId";
sql = multiStateInstanceUtil.replaceSchemaPlaceholder(sql, tenantId);
// central ON + tenantId="ng.kaduna" + position=0 → "SELECT * FROM ng.cms_campaign ..."
// central OFF                                    → "SELECT * FROM cms_campaign ..."

// Topic resolution (done automatically inside Producer.push(tenantId, topic, value))
String resolvedTopic = multiStateInstanceUtil.getStateSpecificTopicName(tenantId, baseTopic);
// central ON + tenantId="ng" → "ng-cms.campaign.command.create"
// central OFF                → "cms.campaign.command.create"

// State-level tenant check
boolean isStateLevel = multiStateInstanceUtil.isTenantIdStateLevel(tenantId);
// Used to decide if a search should scope to state or district level
```

#### `ServiceRequestClient` — THE only HTTP client to use
```java
// CORRECT — inject ServiceRequestClient, never create RestTemplate
@Autowired
private ServiceRequestClient serviceRequestClient;

SomeResponse response = serviceRequestClient.fetchResult(
    new StringBuilder(config.getHrmsHost() + config.getHrmsEmployeeSearchUrl() + "?tenantId=" + tenantId),
    requestBody,
    SomeResponse.class
);

// WRONG — never do this
RestTemplate restTemplate = new RestTemplate();   // NO — loses tracing, no DIGIT headers
HttpHeaders headers = new HttpHeaders();           // NO — use ServiceRequestClient
```

`ServiceRequestClient` wraps `RestTemplate` with:
- Distributed tracing (correlation-id propagation)
- DIGIT standard request headers (RequestInfo forwarding)
- Tracer instrumentation (from `tracer` library)

#### `GenericRepository<T>` — base repository with schema placeholder support
```java
// Our repositories extend GenericRepository for cache + Kafka-push pattern
public class CampaignRepository extends GenericRepository<Campaign> {
    // save() → producer.push(tenantId, topic, entities) — async via Kafka persister
    // findById() → Redis cache → DB fallback
    // Schema placeholder replaced automatically via MultiStateInstanceUtil
}
```

#### `IdGenService` — ID generation
```java
// Available from health-services-common
// Use for campaign numbers, not for user bulk-generate (use IdGenClient directly for bulk)
List<String> ids = idGenService.getIdList(requestInfo, tenantId, "campaign.id", "", count);
```

### 2. `tracer` (org.egov.services)

Provides:
- `CustomKafkaTemplate<String, Object>` — instrumented Kafka template (injected into Producer)
- `TracerConfiguration` — Spring config to import at application level
- `CustomException` — DIGIT standard exception with errorCode + errorMessage

```java
// CORRECT exception pattern (same as household, individual services)
throw new CustomException("CAMPAIGN_NOT_FOUND", "Campaign not found for id: " + campaignId);

// Import TracerConfiguration in main app class
@Import({TracerConfiguration.class, MultiStateInstanceUtil.class})
@SpringBootApplication
public class CampaignOrchestratorApplication { ... }
```

### 3. `health-services-models` (org.egov.common.models)

Shared domain models — use these instead of creating duplicates:
- `RequestInfo`, `ResponseInfo` — DIGIT standard request/response envelope
- `AuditDetails` — `createdBy`, `createdTime`, `lastModifiedBy`, `lastModifiedTime`
- `Individual`, `IndividualSearch`, `IndividualBulkResponse`
- `Project`, `ProjectStaff`, `ProjectFacility`, `ProjectResource`
- `Facility`, `FacilitySearch`
- `Error`, `ErrorDetails` — validation error models

### 4. `digit-models` (org.egov)

- `MdmsV2SearchRequest`, `MdmsV2Response` — MDMS v2 API contracts
- DIGIT standard request/response wrappers

---

## Central Instance Support — Complete Rules

### What "central instance" means
One deployed service instance manages multiple states (e.g. ng, ba, ko).
Each state has its own:
- **DB schema** — `ng.cms_campaign`, `ba.cms_campaign` (PostgreSQL schemas)
- **Kafka topics** — `ng-cms.campaign.command.create`, `ba-cms.campaign.command.create`

### Configuration
```properties
# Enable central instance
is.environment.central.instance=true

# How many dot-segments = state level. "ng" = 1, "ng.kaduna" = 2
state.level.tenantid.length=1

# Which dot-segment to use as DB schema name. 0 = first segment.
# "ng.kaduna" with position=0 → schema="ng"
state.schema.index.position.tenantid=0
```

### DB Queries — Search level
```java
// Always use {schema} placeholder. Never hardcode schema name.
String sql = """
    SELECT * FROM {schema}.cms_campaign
    WHERE tenant_id = :tenantId
    AND is_deleted = :isDeleted
    """;
// Replace BEFORE executing
sql = multiStateInstanceUtil.replaceSchemaPlaceholder(sql, tenantId);
// central ON  + tenantId="ng" → "SELECT * FROM ng.cms_campaign ..."
// central OFF                  → "SELECT * FROM cms_campaign ..."
```

### DB Queries — Insert level
```java
// Same placeholder pattern for INSERT
String sql = "INSERT INTO {schema}.cms_campaign (id, tenant_id, ...) VALUES (:id, :tenantId, ...)";
sql = multiStateInstanceUtil.replaceSchemaPlaceholder(sql, tenantId);
```

### DB Queries — Update level
```java
String sql = "UPDATE {schema}.cms_campaign SET status = :status WHERE id = :id AND tenant_id = :tenantId";
sql = multiStateInstanceUtil.replaceSchemaPlaceholder(sql, tenantId);
```

### Kafka Producer — central instance
```java
// ALWAYS pass tenantId for tenant-scoped data
// Producer.push(tenantId, baseTopic, payload) internally calls:
//   multiStateInstanceUtil.getStateSpecificTopicName(tenantId, baseTopic)
//   → "ng-cms.campaign.command.create" when central=true, tenantId="ng"
//   → "cms.campaign.command.create"    when central=false

producer.push(tenantId, config.getCampaignCommandCreate(), campaignPayload);
```

### Kafka Consumer — central instance
```java
// Use topicPattern (regex) so one @KafkaListener catches all tenant-prefixed variants.
// The pattern ".*cms\\.campaign\\.command\\.create" matches:
//   - "cms.campaign.command.create"       (central=false)
//   - "ng-cms.campaign.command.create"    (central=true, schema=ng)
//   - "ba-cms.campaign.command.create"    (central=true, schema=ba)

@KafkaListener(
    topicPattern = ".*cms\\.campaign\\.command\\.create",
    containerFactory = "sagaCoordinatorContainerFactory",
    groupId = "cms-campaign-saga-coordinator"
)
public void onCampaignCreate(ConsumerRecord<String, Object> record) {
    // Extract tenantId from the payload, not from the topic name
    // tenantId is always in the message body
}
```

---

## Kafka Patterns

### Topic naming convention
```
{tenantPrefix}.cms.{domain}.{entity}.{action}
```
Base names (without tenant prefix) are in `application.properties` under `cms.*`.

### Producer — always use ServiceConfiguration for topic names
```java
// CORRECT
producer.push(tenantId, config.getCampaignCommandCreate(), payload);

// WRONG — never inline topic strings
producer.push(tenantId, "cms.campaign.command.create", payload);
```

### Consumer groups (defined in application.properties + KafkaConsumerConfig)
| Group ID | Factory | Concurrency | Handles |
|---|---|---|---|
| `cms-campaign-saga-coordinator` | `sagaCoordinatorContainerFactory` | 5 | Saga state transitions |
| `cms-project-provisioner` | `projectProvisionerContainerFactory` | 20 | Project chunk creates |
| `cms-user-provisioner` | `userProvisionerContainerFactory` | 30 | User chunk creates |
| `cms-facility-provisioner` | `facilityProvisionerContainerFactory` | 20 | Facility chunk creates |
| `cms-mapping-reconciler` | `mappingReconcilerContainerFactory` | 15 | Mapping convergence |
| `cms-excel-processor` | `excelProcessorContainerFactory` | 10 | Excel parsing |
| `cms-excel-generator` | `excelGeneratorContainerFactory` | 5 | Template generation |

**Naming rule:** Never use "worker" in group IDs, bean names, or topic segments — that term is reserved for the `worker-registry` service (a separate HCM service). Use: `provisioner` (creates entities), `reconciler` (converges state), `coordinator` (saga state machine), `processor` (parses data), `generator` (produces output).

All factories use virtual thread executors (`Executors.newVirtualThreadPerTaskExecutor()`).

---

## HTTP Client Rules

**Rule: never create RestTemplate. Always use ServiceRequestClient.**

```java
// ServiceRequestClient is autowired from health-services-common
// It handles: tracing, DIGIT standard headers, correlation-id

// Pattern used in household.IndividualService (follow this exactly)
return serviceRequestClient.fetchResult(
    new StringBuilder(config.getHrmsHost()
        + config.getHrmsEmployeeSearchUrl()
        + "?tenantId=" + tenantId),
    requestBody,
    HrmsEmployeeBulkResponse.class
);
```

All outbound service clients live in `infrastructure/clients/`. One file per external service.

---

## Layering Rules (strict — same as household service)

| Layer | Owns | Must not |
|---|---|---|
| `api/` | Parse HTTP, call application service, return response | Business logic, DB, Kafka produce |
| `application/` | Orchestrate domain for one use case | Import Spring MVC objects |
| `domain/` | Business logic, saga definitions, Excel logic | Import Spring, Kafka, JDBC |
| `infrastructure/kafka/` | Connect, route to application service | Business decisions |
| `infrastructure/persistence/` | SQL only, schema placeholder | Business logic |
| `infrastructure/clients/` | HTTP to one external service | Business decisions |
| `infrastructure/cache/` | Read/write Redis | Business logic |

---

## Batch/Chunk Size Rules

**Every batch size is an application.properties property. Never hardcode a number.**

```java
// CORRECT
Lists.partition(users, config.getUserChunkSize()).forEach(chunk -> ...);

// WRONG
Lists.partition(users, 100).forEach(chunk -> ...);   // NO — not configurable
```

All batch sizes are in `ServiceConfiguration` loaded from `application.properties`.

---

## Idempotency Rules

Every downstream create MUST go through idempotency check first.

```
Key format:
  USER:     "{tenantId}:{mobileNumber}"
  FACILITY: "{tenantId}:{facilityCode}"
  PROJECT:  "{tenantId}:{campaignNumber}:{boundaryCode}"
  MAPPING:  "{tenantId}:{entityUuid}:{projectId}"

Flow:
  1. Check cms_idempotency_registry by key
  2. If found → return existing entityId, skip create
  3. If not found → create downstream → register key + entityId (TTL=7 days)
```

---

## Saga Rules

- All saga state lives in `cms_saga_instance`
- Step transitions use optimistic locking on `version` column
- Dead letter failures go to `cms_saga_dead_letter`
- Compensation must be idempotent (safe to call multiple times)
- Status values: `STARTED` → `RUNNING` → `COMPLETED` or `COMPENSATING` → `FAILED`

---

## Database Rules

- All tables prefixed `cms_`
- All queries use `{schema}` placeholder — NEVER hardcode schema
- Soft delete only — `is_deleted = true`, never `DELETE`
- `row_version` incremented on every update (optimistic lock for REST-layer entities)
- Parameterized queries only — never string interpolation in SQL

---

## File Locations

| What | Path |
|---|---|
| Main class | `CampaignOrchestratorApplication.java` |
| Spring config (beans, Redis) | `config/MainConfiguration.java` |
| All @Value properties | `config/ServiceConfiguration.java` |
| Kafka producer | `infrastructure/kafka/producer/CampaignEventProducer.java` |
| Kafka consumer factories | `infrastructure/kafka/config/KafkaConsumerConfig.java` |
| Campaign command consumers | `infrastructure/kafka/consumer/CampaignCommandConsumer.java` |
| Resource provisioner consumers | `infrastructure/kafka/consumer/ResourceProvisionerConsumer.java` |
| DB migrations | `src/main/resources/db/migration/V00*__*.sql` |
| Full design doc | `CMS-Java-Migration-Design.md` |

---

## Implementation Phases (current status)

- [x] Phase 0: Foundation — project structure, config, Kafka wiring, DB migrations
- [ ] Phase 1: Campaign CRUD + Draft flow
- [ ] Phase 2: Saga engine core
- [ ] Phase 3: Project creation saga
- [ ] Phase 4: User creation saga
- [ ] Phase 5: Facility creation saga
- [ ] Phase 6: Mapping reconciler saga
- [ ] Phase 7: Excel ingestion embedded
- [ ] Phase 8: Production hardening

---

## Anti-Patterns (forbidden)

| Forbidden | Why |
|---|---|
| Raw `KafkaTemplate` usage | Loses tracing + central instance routing |
| Own `RestTemplate` bean | Loses DIGIT headers + tracing |
| Hardcoded topic strings | Bypasses central instance prefix |
| Hardcoded schema in SQL | Breaks multi-tenant deployment |
| Hardcoded batch sizes | Not configurable for scale tuning |
| `DELETE` SQL | Use `is_deleted=true` (soft delete) |
| String interpolation in SQL | SQL injection risk |
| Skipping idempotency check | Creates ghost records on retry |
| Using `XSSFWorkbook` for large files | OOM at 50k rows — use SXSSF streaming |
