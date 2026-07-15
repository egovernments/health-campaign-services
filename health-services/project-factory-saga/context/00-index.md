# Project Factory (Saga) — Context Index

This folder is the **running knowledge base** for the project-factory → Java/saga
migration. **Read the relevant file before making a change; update
[`09-progress-log.md`](09-progress-log.md) after.** These are distilled from the design
docs at `/home/admin/Desktop/project-factory-docs/` and from studying the existing
`project` and `excel-ingestion` services.

| File | What it holds |
|---|---|
| [01-overview.md](01-overview.md) | The problem, the proposal, targets, non-negotiables (from docs 01/05) |
| [02-conventions.md](02-conventions.md) | Code conventions to follow (DI, constants, structure) + how the shared libs are used |
| [03-existing-services-map.md](03-existing-services-map.md) | Structure & patterns of the current `project` and `excel-ingestion` services |
| [04-saga-and-events.md](04-saga-and-events.md) | Saga engine, state machine, event contract, registry-driven DAG (from doc 03) |
| [05-migration-plan.md](05-migration-plan.md) | Phased strangler-fig roadmap, HLD modules, DB design, tenancy (docs 02/04/06/07/08) |
| [09-progress-log.md](09-progress-log.md) | Chronological log of what has been built and decided |

**Source docs (authoritative, read-only):** `/home/admin/Desktop/project-factory-docs/`
— `01 Executive Summary`, `02 High Level Design`, `03 LLD Saga Engine Event`,
`04 Database Design at`, `05 Capacity Planning`, `06 SaaS Multi Tenancy`,
`07 Architecture Decision`, `08 Migration Strategy`, plus `project-factory-re.md`
(consolidated narrative).

**Reference source trees (read-only):**
- TS project-factory: `../project-factory/`
- Java excel-ingestion (being absorbed): `../excel-ingestion/`
- Java project service (pattern reference): `../project/`
