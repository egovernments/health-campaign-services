# 05 — Migration Plan, Modules, DB, Tenancy (docs 02/04/06/07/08)

## Principle
**Strangler-fig, zero-downtime, data-preserving.** Stand up Java PF beside TS PF; move
flows one at a time behind **per-tenant feature flags**; never break existing APIs or
tables (additive DDL only); every phase independently reversible.

## Phased roadmap
| Phase | Objective | Reversible via |
|---|---|---|
| **P0 — Foundations** ← *we are here* | Java 25 skeleton, observability, tenancy plumbing, Flyway for new tables, CI/CD, 3 runtime roles | not serving traffic |
| **P1 — Read parity + shadow** | read adapters, shadow consumer subgroup, parity/reconcile reports | shadow only |
| **P2 — Streaming Excel engine** | one POI SXSSF/SAX engine + cascading dropdowns, golden-file tests | per-type flag → TS engine |
| **P3 — Own-your-writes + outbox** | local-TX writes + outbox/inbox + dual-write to persister + reconcile job | disable flag → persister source |
| **P4 — Saga core** | coordinator, saga tables, retry/backoff, DLQ, compensation, reaper | old topic-chain handlers stay |
| **P5 — Entity flows onto saga** | users → facilities → projects → mapping; BatchExecutor (virtual threads), ConcurrencyGovernor | per-flow, per-tenant flag |
| **P6 — Ingestion absorption** | route `/excel-ingestion/*` internally; decommission standalone | route back |
| **P7 — SaaS hardening** | quotas, onboarding runbook, isolation tests, billing events | flags per tenant |
| **P8 — Cleanups** | remove Data Manager; retire dual-write; sunset TS PF | final, gated on zero residual traffic |

Dependency chain: **P0,P1 → P2/P3 → P4 → P5 → P6,P7 → P8.**

## HLD modules (doc 02 §5) — ArchUnit-enforced dependency direction
```
api ──► campaign ──► orchestration ──► integration ──► persistence
                 └──► excel ──► validation
eventing, configuration, monitoring: cross-cutting (depend on nothing domain-specific)
- persistence depends only on configuration
- integration must NOT depend on api/campaign (adapters reusable)
- excel/validation must NOT depend on integration (streams + config only)
```
> In this module these map to packages under `org.egov.projectfactory` (see README).
> One deployable, three **runtime roles by Spring profile**: `api` (HTTP),
> `orchestrator` (coordinator + reaper, leader-elected), `worker` (step/batch consumers).
> Outbox relay = small leader-elected component.

## Database (doc 04) — additive only
- **New tables:** `eg_cm_saga_execution`, `eg_cm_saga_step`, `eg_cm_outbox`,
  `eg_cm_inbox` (created in `V20260715120000__saga_infra_ddl.sql`).
- **New columns (later, additive):** `eg_cm_campaign_details.sagaid` (nullable);
  `eg_cm_campaign_data.retrycount/lasterror/createdtime`.
- **Partitioning:** HASH by `campaignnumber` (16 partitions) for `campaign_data` /
  `_mapping_data` — done online during dual-write window, PK preserved.
- **Indexes:** `(campaignnumber, type, status)` (completion-count query), outbox partial
  `(status, createdtime) WHERE status='NEW'`, saga `(status)` + `(campaignnumber)`.
- **TTL/cleanup (incremental, bounded batches):** sheet_data_temp ~24h, outbox SENT 7d,
  inbox 30d, saga_step terminal 90d, campaign_data archived 6–12mo.
- Autovacuum tuned per hot table; HikariCP sized `(cores×2)+spindles`, separate pools for
  relay/reaper; read replica for status/search.

## Multi-tenancy (doc 06) — build on existing mechanism, don't reinvent
- **Data isolation:** `MultiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId)`
  with `{schema}.table`. Gated by `is.environment.central.instance`.
- **Messaging isolation:** tenant-prefixed topics via 3-arg `Producer.push` /
  `getStateSpecificTopicName`; consumers use `@KafkaListener(topicPattern=...)`. **Apply
  uniformly on EVERY topic** (individual is inconsistent — PF must not be).
- **Schema provisioning is EXTERNAL** (DIGIT platform ops). PF does NOT create schemas or
  run per-tenant Flyway; it assumes the schema exists (like individual/excel-ingestion).
- **New components:** ConcurrencyGovernor per-`(downstream, tenant)`, `FeatureResolver`
  (tenant/campaign flags), tenant config store (could be MDMS masters). Resolution order:
  static defaults < plan/tier < tenant overrides < campaign additionalDetails.
- Admission control per tenant (`maxConcurrentCampaigns`, `maxRowsPerSheet`) → 429/queued
  with localized ETA, never silent failure. Billing = metered outbox events (PF feeds,
  doesn't bill).

## Key ADRs (doc 07)
- 001 custom saga (own Postgres+Kafka) · 002 Java 25 (virtual threads, POI already Java)
  · 003 POI SXSSF/SAX (cascading dropdowns already exist in POI) · 004 transactional
  outbox (Kafka TX can't span external DB) · 005 own-your-writes (dual-write + reconcile)
  · 006 modular monolith · 007 orchestration over choreography · 008 partition by
  `campaignNumber`.

## Risk register highlights
R1 Excel dropdown fidelity (golden-file tests) · R2 own-your-writes divergence (reconcile
gates P4+) · R3 coordinator correctness (single-writer + FOR UPDATE + inbox + chaos
tests) · R10 hidden coupling in 2.5K-line TS handler (shadow parity surfaces gaps).

## Definition of done (per flow)
Parity holds in shadow · golden tests pass · saga drives end-to-end with
retry/compensation exercised in a game-day · observability shows 100% of injected
failures with cause+trace · load tests meet SLOs → then flip the per-tenant flag.
