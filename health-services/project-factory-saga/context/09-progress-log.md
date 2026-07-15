# 09 — Progress Log

Chronological record of what has been built/decided. **Append a dated entry after every
change.** Newest at top.

---

## 2026-07-15 — P0 skeleton created

**Decisions**
- Service name (provisional): `project-factory-saga`; package root `org.egov.projectfactory`.
- **Java 25** (per user direction). JDK 25 confirmed installed at
  `/usr/lib/jvm/java-25-openjdk-amd64`.
- **Spring Boot 3.5.6** parent (NOT 3.2.2 like the siblings). Reason discovered during
  build verification: SB 3.2.2's `spring-boot-maven-plugin:repackage` cannot read Java 25
  bytecode ("Unsupported class file major version 69"). SB 3.5.x is the first line that
  supports Java 25. Sibling services stay on 3.2.2 until they migrate; this service leads.
- **Lombok 1.18.46** (SB 3.5.6 already manages a JDK-25-capable version; the explicit
  pin + `<lombok.version>` keep it deterministic).
- **Annotation processing must be explicit on JDK 23+**: added maven-compiler-plugin
  config with `-proc:full` and Lombok on `annotationProcessorPaths`, otherwise `@Slf4j`
  et al. are silently skipped and `log` is "cannot find symbol". (Root cause of the first
  two failed builds.)
- New **sibling Maven module** under `health-services/` (no aggregator pom in this repo;
  every service is standalone).

**Built (skeleton)**
- `pom.xml` — Java 25; deps: spring web/jdbc/actuator, health-services-common
  1.1.4-SNAPSHOT, health-services-models 1.0.35-SNAPSHOT, flyway, postgresql, redis+jedis,
  spring-kafka, cache+caffeine, resilience4j (spring-boot3/circuitbreaker/retry),
  micrometer-prometheus, POI 5.4.1 (+ commons-collections4), validation, swagger, lombok,
  OTel BOM. `-Prun-tests` profile.
- `ProjectFactorySagaApplication` — `@SpringBootApplication @ComponentScan("org.egov")
  @Import(TracerConfiguration) @EnableCaching @EnableAsync @EnableScheduling`.
- `config/MainConfiguration` — ObjectMapper + redisObjectMapper + Jackson converter +
  Jedis Redis (ported from project service).
- `config/ApplicationConfig` — RestTemplate + lenient ObjectMapper (from excel-ingestion).
- `config/ProjectFactoryConfiguration` — `@Value` properties (downstream hosts, saga
  tunables, tenancy switch).
- `config/ErrorConstants`.
- `exception/GlobalExceptionHandler` — `@RestControllerAdvice`, health-services error shape
  (from excel-ingestion).
- `web/controllers/HealthController` — `GET /project-factory/ping`.
- `application.properties` — server/redis/datasource/flyway/kafka/tenancy/downstream/saga/
  actuator; `spring.threads.virtual.enabled=true`.
- `db/migration/main/V20260715120000__saga_infra_ddl.sql` — new tables
  `eg_cm_saga_execution`, `eg_cm_saga_step`, `eg_cm_outbox`, `eg_cm_inbox` (additive,
  unqualified DDL, `IF NOT EXISTS`).
- Empty package dirs for the target module layout (orchestration/{saga,registry},
  eventing, integration, excel/{generator,processor}, service/enrichment, repository/
  {querybuilder,rowmapper}, validator, util, web/models).
- `README.md`, `.gitignore`, this `context/` folder.

**Build status:** ✅ VERIFIED. `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 mvn clean
package` produces a runnable Spring Boot fat-jar
(`target/project-factory-saga-1.0.0-SNAPSHOT.jar`, ~105 MB), Start-Class
`org.egov.projectfactory.ProjectFactorySagaApplication`, Spring-Boot-Version 3.5.6, app
classes at bytecode major version 69 (Java 25). All deps (health-services-common/models,
POI 5.4.1, resilience4j 2.2.0, caffeine, spring-kafka, micrometer-prometheus) resolved
from the eGov nexus. Not yet run against a live DB/Kafka (skeleton only).

**Follow-ups flagged for the team**
- CI must provision a **JDK 25** toolchain and expect **Spring Boot 3.5.6** for this
  module (diverges from the 3.2.2 siblings). Re-align when siblings migrate to Java 25.
- `pom.xml` also updated the maven-compiler-plugin (see Decisions) — keep this when
  copying the pattern to other Java-25 modules.

**Next candidate steps (P0 → P1)**
- Wire `Producer` (tenant-aware push) + a base Kafka consumer config with `topicPattern`.
- Define event envelope + outbox writer/relay skeleton (`eventing/`).
- Port the resource-type registry as data (`orchestration/registry/`).
- Bring in `health-services-models` campaign models; stub the `/project-type/*` API
  surface (draft vs create) without behavior, for shadow wiring in P1.
- Decide the 3 runtime-role Spring profiles (`api`/`orchestrator`/`worker`).
