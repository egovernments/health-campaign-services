# 01 — Overview (the what & why)

## One line
Rewrite Project Factory (PF) from TypeScript to **Java 25**, **absorb the Excel Ingestion
service**, and replace ad-hoc Kafka choreography with a **durable, DB-backed Saga
orchestrator** — so campaign creation is reliable, observable, and recoverable at
10–20× today's scale.

## The problem (today)
PF orchestrates campaign setup (MDMS, Localization, HRMS, Facility, Project, Boundary)
from Excel input. It works at small scale but **fails silently at large scale**:
- downstream failures are swallowed; no true retry/compensation;
- workbooks processed fully in memory; concurrency capped at 5 (semaphore);
- logic split across two services (PF + Excel Ingestion) joined by a single
  fire-and-forget Kafka message — no shared consistency model;
- read-after-write races force hand-tuned polling (`pollUntilCount`, 4× boundary poll).
- Canonical incident: a large user sheet fails with *"Unified console template is not
  valid"* when the real cause is a timeout.

## The proposal (6 pillars)
1. **Java 25 / Spring Boot** — virtual threads + structured concurrency replace the
   semaphore-of-5.
2. **Durable Saga orchestrator** — state in DB, transactional outbox/inbox; per-step
   retry, compensation, idempotency, DLQ.
3. **Single streaming Excel engine** — POI SXSSF (write) + SAX (read); bounded memory.
4. **First-class observability** — OpenTelemetry traces, Micrometer metrics, structured
   logs. Zero silent failures.
5. **Own-your-writes** — PF writes its own DB in local TXs (via outbox), removing
   read-after-write races.
6. **SaaS multi-tenancy** — onboarding, quota, isolation, billing hooks as governed
   platform capability (built on existing `MultiStateInstanceUtil`).

## Non-negotiables (must honor)
- Existing **APIs unchanged**; existing **DB tables intact (additive DDL only)**;
  existing **production data untouched**; **zero-downtime** strangler-fig migration.
- Data Manager (`/v1/data`) removed. No external Excel Ingestion dependency remains.

## Measurable targets
| Goal | Target |
|---|---|
| Scale per campaign | 150K boundaries, 70K users, 40K facilities (~260K rows) |
| Max concurrent campaigns | 10 (headroom to 20) |
| Campaign creation latency | < 20 min P95 (single); < 30 min P95 under 10-campaign contention |
| Availability | 99.9% |
| RPO / RTO | 0 / 15 min |
| Silent failures | Zero — every failure recorded with cause + trace |

**Binding constraint is downstream write TPS** (HRMS ~150/s, Facility ~200/s,
Project ~100/s), governed per-`(downstream, tenant)` so campaigns share budget and
queue fairly rather than overwhelming downstreams. Per-worker heap target **2 GB**
(streaming removes the "load everything" pattern).

## Version note
Design docs say "Java 25". This module targets **Java 25** per user direction; the rest
of the health-services stack is Java 17 today and will migrate to 25. (Earlier memory
note said Java 21 — superseded.)
