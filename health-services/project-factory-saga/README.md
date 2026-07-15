# Project Factory (Saga)

Java rewrite of the TypeScript **project-factory** service, which also absorbs the
**excel-ingestion** service, on a durable, DB-backed **Saga orchestrator**.

> Status: **P0 — Foundations** (skeleton). Not yet serving traffic. See the phased
> migration plan in [`context/05-migration-plan.md`](context/05-migration-plan.md).

## Why

The current TS PF orchestrates campaign setup (MDMS, Localization, HRMS, Facility,
Project, Boundary) from Excel input via ad-hoc Kafka choreography. It fails silently at
scale. This service replaces that with virtual-thread concurrency, a custom saga engine
(state in Postgres, transactional outbox/inbox), one streaming Excel engine (POI
SXSSF/SAX), and first-class observability. Full rationale in [`context/`](context/).

## Tech

- **Java 25** (virtual threads + structured concurrency), **Spring Boot 3.5.6**
  (3.2.2 like the siblings cannot repackage Java 25 bytecode; 3.5.x is the first that can)
- `health-services-common` (Producer, tracer, MultiStateInstanceUtil), `health-services-models`
- PostgreSQL + Flyway · Kafka · Redis · Caffeine · Resilience4j · Apache POI · Micrometer/OTel

## Build

Requires a **JDK 25** toolchain. The rest of the health-services stack is Java 17 today
and is slated to migrate to 25.

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
mvn clean package                 # skips tests by default (matches excel-ingestion)
mvn clean package -Prun-tests     # runs tests
```

## Conventions

This module follows the existing health-services conventions — see
[`context/02-conventions.md`](context/02-conventions.md). Key rules: **constructor
injection only** (no `@Autowired`), **constants for repeated strings**, simplicity first,
O(n) over O(n²). Read `context/` before making changes and update
[`context/09-progress-log.md`](context/09-progress-log.md) after.

## Package layout

```
org.egov.projectfactory
├── config/         @ConfigurationProperties, ObjectMapper/Redis/RestTemplate beans, constants
├── consumer/       Kafka listeners (saga command/reply, batch workers)
├── orchestration/  SagaCoordinator, step executor, compensation, reaper; registry/ = resource-type DAG
├── eventing/       outbox writer + relay, inbox guard, event contracts
├── integration/    downstream adapters (MDMS/Loc/HRMS/Facility/Project/Boundary/Attendance) + governor
├── excel/          streaming generators (SXSSF) + processors (SAX) — absorbed excel-ingestion
├── service/        application services (+ enrichment/)
├── validator/      streaming validation pipeline
├── repository/     JDBC repositories (querybuilder/, rowmapper/)
├── util/           helpers
├── web/            controllers + models
└── exception/      global exception handler
```
