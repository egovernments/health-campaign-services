# 02 — Code Conventions (follow these exactly)

These are the enforced conventions across the existing health-services (verified against
`project` and `excel-ingestion`, incl. `excel-ingestion/CLAUDE.md`). **New code in this
module must match them.**

## Hard rules
1. **Constructor injection only. NEVER `@Autowired`.** Fields are `private final`,
   injected via constructor. (excel-ingestion CLAUDE.md rule.)
2. **No hardcoded repeated strings** — put them in a constants class
   (`config/ErrorConstants.java`, `*/…Constants.java`).
3. **Simplicity first**; standard patterns; readable in 5 minutes; one responsibility
   per class/method.
4. **Time complexity: O(n²) forbidden.** Use `HashMap`/`HashSet` for lookups; avoid
   nested loops over large collections. Streaming/bounded memory at scale.
5. **Localization:** any error/message that surfaces from a sheet must be localizable.

## Package root
`org.egov.projectfactory` (this module). The `project` service uses `org.egov.project`;
excel-ingestion uses `org.egov.excelingestion`.

## Standard structure (mirrors project + excel-ingestion)
```
config/      @ConfigurationProperties(@Value), ObjectMapper/Redis/RestTemplate beans, *Constants
consumer/    @KafkaListener classes
repository/  JDBC repos + querybuilder/ + rowmapper/
service/     app services (+ enrichment/)
validator/   validation strategies
util/        helpers
web/         controllers/ + models/
exception/   @RestControllerAdvice global handler
```
Saga-specific additions in this module: `orchestration/` (+`saga/`, `registry/`),
`eventing/`, `integration/`, `excel/` (`generator/`, `processor/`).

## Shared libraries (how they are used)
- **`health-services-common`** (`org.egov.common.*`):
  - `Producer` — Kafka publish. Use the **tenant-aware 3-arg `push(tenantId, topic, value)`**
    (or `getResolvedTopicName`) on **every** topic, not the 2-arg overload. (Doc 06 §0:
    `individual` is inconsistent here; PF must be uniform.)
  - `ResponseInfoFactory` — build `ResponseInfo` for responses.
  - `MultiStateInstanceUtil` — `replaceSchemaPlaceholder(query, tenantId)` for every
    query (`... {schema}.table ...`) and topic prefixing. Driven by
    `is.environment.central.instance`, `state.level.tenantid.length`,
    `state.schema.index.position.tenantid`.
  - models under `org.egov.common.models.*` (project, core, etc.).
- **`health-services-models`** — shared request/response/domain models.
- **tracer** (`org.egov.tracer`): `@Import(TracerConfiguration.class)`,
  `CustomException(code, message)` for errors, `HashMapDeserializer` for Kafka consumers.
- **Lombok** — `@Getter/@Setter/@Builder/@AllArgsConstructor/@NoArgsConstructor`,
  `@Slf4j`.

## Kafka
- Consumers: `HashMapDeserializer` value deserializer; group-id per service.
  For multi-tenant central-instance, use `@KafkaListener(topicPattern=...)` regex so one
  group matches tenant-prefixed topics (doc 06 §0). Set `missing-topics-fatal=false`.
- Producers: tenant-aware push (above); `JsonSerializer` value serializer.

## DB / Flyway
- Flyway single-target `classpath:/db/migration/main`, `baseline-on-migrate=true`,
  `outOfOrder=true`. DDL is **unqualified** (runs in default schema); per-tenant schema
  provisioning is **external** (doc 06 §3.1) — the app does NOT create schemas.
- Migration file naming: `V<yyyyMMddHHmmss>__<snake_desc>_ddl.sql`.
- Batch upserts via multi-row `INSERT ... ON CONFLICT DO UPDATE` (500–1000 rows/stmt),
  never row-by-row (doc 04 §2.3).

## Isolation gotcha (must avoid)
The TS `transformConfigs` singleton-corruption bug (concurrent consumers overwriting
each other's `tenantId`) is eliminated by keeping all tenant config **immutable and
resolved per-request/per-campaign** — never shared mutable static state.

## POI (from excel-ingestion, being absorbed)
Reuse `HierarchicalBoundaryUtil` approach for cascading dropdowns (hidden lookup sheets,
SHA-256-keyed named ranges, helper-column `INDIRECT` validation). Keep `PoiConfig`
hardening (byte-array cap, inflate ratio). Streaming write = SXSSF; streaming read =
XSSFReader + SAX. **Golden-file tests** required to prove parity (risk R1).
