# 03 — Existing Services Map (what we port from)

Reference trees (read-only), all under `health-services/`:
- `project/` — Java pattern reference (structure, config, tenancy, tracer).
- `excel-ingestion/` — Java, **being absorbed**; source of the POI streaming engine.
- `project-factory/` — TypeScript, the service **being rewritten**.

## project (org.egov.project) — pattern reference
```
config/      MainConfiguration (ObjectMapper/Redis beans), ProjectConfiguration (@Value props)
consumer/    ProjectBeneficiary/Facility/Resource/Staff/Task/UserAction Consumer (@KafkaListener)
repository/  *Repository + querybuilder/ + rowmapper/
service/     Project/ProjectTask/ProjectStaff/... Service (+ enrichment/)
validator/   per-domain validators (beneficiary/facility/project/resource/staff/task/useraction)
util/        MDMSUtils, BoundaryUtil, BoundaryV2Util, ProjectServiceUtil, ResponseInfoFactory, *Constants
web/         controllers/ (ProjectApiController, ...), models/
```
- `ProjectApplication`: `@SpringBootApplication @EnableCaching @Import(TracerConfiguration)`.
- `MainConfiguration`: `@ComponentScan("org.egov")`, dual ObjectMapper beans
  (`objectMapper`, `redisObjectMapper`), Jedis Redis, timezone from `app.timezone`.
- pom: Java 17, Spring Boot 3.2.2, health-services-common 1.1.3-SNAPSHOT,
  health-services-models 1.0.35-SNAPSHOT, flyway 9.22.3, jedis, OTel BOM.

## excel-ingestion (org.egov.excelingestion) — the engine we absorb
```
config/      ApplicationConfig (RestTemplate+ObjectMapper), ExcelIngestionConfig, KafkaTopicConfig,
             PoiConfig (hardening), ErrorConstants, ProcessingConstants, ValidationConstants
consumer/    GenerationInitConsumer
generator/   ISheetGenerator, IExcelPopulatorSheetGenerator, SchemaBasedSheetGenerator,
             Boundary/Facility/User/AttendanceRegister(+Attendee)SheetGenerator
processor/   IWorkbookProcessor, ISheetDataProcessor, Boundary/Facility/User/Attendance... Processors
service/     Async/Config-based Generation & Processing services, Boundary/Campaign/MDMS/
             Localization/FileStore/Crypto/SchemaValidation/SheetData services, ExcelWorkflowService
util/        HierarchicalBoundaryUtil (cascading dropdowns!), CellProtectionManager, ColumnDefMaker,
             ExcelDataPopulator, ExcelStyleHelper, ExcelUtil, Boundary/ErrorColumn util, Enrichment,
             RequestInfo utils, SchemaColumnDefUtil, LocalizationUtil
exception/   CustomExceptionHandler, GlobalExceptionHandler, ValidationExceptionHandler
web/         controller/ + models/ (excel, localization, filestore, mdms)
```
- `ExcelIngestionApplication`: adds `@EnableAsync`, a Caffeine `CacheManager` with named
  caches (localization/boundaryHierarchy/... TTLs), and a `taskExecutor` ThreadPool.
- pom: Java 17, SB 3.2.2, POI 5.4.1 (poi, poi-ooxml, poi-ooxml-lite, poi-ooxml-full),
  commons-collections4, caffeine, spring-kafka, health-services-common 1.1.4-SNAPSHOT.
- **CLAUDE.md** here defines the strict conventions (see 02-conventions.md).

## project-factory (TypeScript) — logic being rewritten
Key files referenced by the design docs (verify line numbers before relying on them):
- `campaignUtils.ts` — `processBasedOnAction` (~3004), `tryTriggerGenerateIfBoundariesSynced`
  (~3024, the 4× DB poll — removed by own-your-writes).
- `generateUtils.ts` — `isGenerationTriggerNeeded` (~219), `isUnifiedSheet` decision (~120).
- `config/generateQuery.ts` — `action` enum `"draft" | "create"` (~22).
- `config/resourceTypeRegistry.ts` — the phase/dependsOn DAG (facility/user/boundary
  phase 1; attendanceRegister phase 2; attendee phase 3).
- `userBatchHandler.ts` (~257-318), `workerRegistryUtils.ts` — worker-registry sub-step,
  `waitForIndividualsSearchable` (HRMS→Individual consistency poll, fail-open).
- `processingResultHandler.ts` (~1692) — attendance excluded from create;
  `resourceDetails.controller` → `triggerIfCampaignCreated`.

## What maps where (target module in this service)
| Source concern | Target module here |
|---|---|
| exceljs generation / POI generation | `excel/generator/` (POI SXSSF) |
| sheet parsing / validation | `excel/processor/` + `validator/` (SAX streaming) |
| cascading boundary dropdowns | `excel/` (port `HierarchicalBoundaryUtil`) |
| campaign action/draft/create orchestration | `orchestration/` + `service/` |
| resource-type registry | `orchestration/registry/` |
| downstream calls (MDMS/HRMS/Facility/...) | `integration/` (Resilience4j adapters) |
| Kafka persistence + events | `eventing/` (outbox/inbox) + `consumer/` |
| existing tables + new saga/outbox/inbox | `repository/` + Flyway `db/migration/main` |
