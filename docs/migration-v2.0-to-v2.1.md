# HCM Migration Guide — v2.0 → v2.1

<!-- Official DIGIT format: docs.digit.org → Health → Release Notes → Migration Guide -->

This guide upgrades Health Campaign Services from **v2.0** to **v2.1**. The release adds
worker registry, attendance & payments, push and scheduled notifications, moves campaign
resources to a dedicated store, and carries the VAPT security hardening (DB-error masking
and PII encryption).

## HCM Upgrade Guide

### Step 1 — Latest health campaign configurations

Apply the latest persister/indexer configurations from the **`configs`** repo
(`configs/health/egov-persister`, `configs/health/egov-indexer`):

- New persisters for **egov-notification-push** (device tokens) and
  **health-notification-service** (scheduled notifications), plus the
  health-notification indexer.
- Updated: **stock** (`campaignNumber`), **project-factory** (campaign resource tables),
  **referral-management** (`projectId`, downsync, HF-referral).

Restart the persister/indexer consumers after applying.

### Step 2 — Latest DevOps changes

Apply the latest Helm charts/values from **DIGIT-DevOps**:

- Charts and environment values for the four new services (see Step 4).
- FCM service-account secret for **egov-notification-push**.
- Kafka settings: GZIP compression and 4 MB max message size on large-payload topics —
  the broker must allow ≥ 4 MB messages.

### Step 3 — Update seed data & localization

- **Seed data (MDMS):**
  - `DataSecurity.SecurityPolicy` must exist for the target tenant and cover **every
    encrypted model** — services using enc-client fail to boot without it.
  - Attendance/works RBAC roles must be present in `ACCESSCONTROL-ROLES` for the
    operating tenant.
- **Localization:** import the new keys for the attendance, worker and validation flows
  (egov-localization seed data).

### Step 4 — Latest builds

Deploy the latest builds of:

- **New services:** worker-registry, egov-notification-push, health-notification-service,
  airflow-trigger-service — plus the DIGIT-Works attendance & payments services
  (health-attendance, health-muster-roll, health-expense, health-expense-calculator)
  in the health namespace.
- **Updated services:** project-factory, excel-ingestion, referralmanagement, stock,
  project, household, individual, facility, egov-hrms, pgr-services, beneficiary-idgen,
  service-request. Shared libraries move to `health-services-models 1.0.35` /
  `health-services-common 1.1.5` — deploy the consumers together.
- **UIs:** workbench-ui, dashboard-ui, payments-ui, health-ui, microplan-ui.

DB migrations apply automatically on boot (Flyway). project-factory runs a one-time
backfill that moves campaign resources from the `campaigndetails` JSONB into the
`eg_cm_resource_details` table.

> 💡 **Breaking changes to note before deploying:**
> - Attendance registers are now linked by **campaign number** instead of campaign id;
>   `campaignNumber` is added to the stock APIs.
> - Campaign resources move out of JSONB into a dedicated table (one-time backfill).
> - Kafka broker must accept messages of at least 4 MB.
> - DB error responses are masked (`QUERY_EXECUTION_ERROR`) and PII fields are
>   encrypted — the `SecurityPolicy` master from Step 3 is a hard dependency.

## Post-v2.1 — Performance & reliability hardening (`master-perf`)

The **`master-perf`** branch layers a performance/reliability effort on top of v2.1. It ships
**no new Flyway migrations and no new services**; the operator-visible deltas are config flags,
a few default-behaviour changes, and shared-library/tracer bumps. Deploy the health-services
set together (shared-lib bump below).

### Shared libraries & tracing

- `health-services-common` → **`1.1.6-SNAPSHOT`** (from `1.1.5`); `health-services-models`
  stays `1.0.35-SNAPSHOT`. Rebuild/redeploy consumers together.
- **tracer → `2.9.3-SNAPSHOT`** (from `2.9.2`), inherited transitively via
  `health-services-common`. It now propagates **`correlationId` + `tenantId` across Kafka**
  (message headers, with a payload fallback), so async flows keep trace/tenant context. This
  is a *separate* change from the v2.1 VAPT DB-error-masking work.

### Cross-entity validation "unbundled" — default OFF (behaviour change)

Create/update no longer synchronously reject on a **missing parent** by default (offline-first);
structural/uniqueness/link checks still run. Toggle per service (read **live**, no restart):

- `household.member.relationship.validation=false`
- `project.relationship.validation=false`
- `referralmanagement.relationship.validation=false`
- `individual.beneficiary.id.validation.enabled=false`

> **Action:** if an environment relies on synchronous parent-existence rejection, set the
> relevant flag(s) to `true`. With the default (OFF), e.g. `INDIVIDUAL_NOT_FOUND` on member
> create is suppressed.

### excel-ingestion — new server-side gates + OOM/perf fixes

- `egov.excel.immutable-reject-on-change=true` — editing a server-managed / pre-filled template
  cell now **fails the upload** instead of silently reverting.
- `egov.excel.usage-value-validation-enabled=true`, `egov.excel.boundary-selection-validation-enabled=true`
  — stricter Active/Inactive + boundary-code checks.
- `egov.excel.join-mode-sheet-protection-enabled=false` — User/Facility join-mode sheets ship unprotected.
- `excel.ingestion.listener.watchdog.interval.ms=60000` — restarts a stopped/zombie generation listener.
- Async pool serialized + OOM / generation-time fixes for large (~50k-row) sheets.

### boundary throughput (boundary-management + core boundary-service)

- Relationship creation uses a **dedicated bulk Kafka topic** + batch persist. Tunable via
  `BULK_RELATIONSHIP_CHUNK_SIZE` (`100`), `BULK_RELATIONSHIP_RETRY_ATTEMPTS` (`30`),
  `BULK_RELATIONSHIP_RETRY_DELAY_MS` (`2000`), `RELATIONSHIP_CREATE_CONCURRENCY`,
  `PERSISTENCE_DRAIN_TIMEOUT_MS`.
  > The bulk relationship topic must **not** be tenant-prefixed on a central instance.
- boundary-management Node heap raised (`--max-old-space-size` `1024` → `2048`).

### project-factory reliability

- `BOUNDARY_SYNC_RETRY_DELAY_MS=4000` (configurable boundary-sync retry delay).
- HRMS per-user create fallback is bounded + retried with back-off/throttle; a poll-time
  reconciler converges adopted-user rows on retry and surfaces upsert errors; the credential
  sheet shows the existing login username for adopted users. Kafka consumption is at-least-once.

### egov-persister (core-services)

- At-least-once delivery (manual ack); SQLSTATE-based failure classification
  (benign / transient / permanent); **dead-letter + bounded reprocessor + terminal parking
  topics** (`egov-persister-deadletter`, `egov-persister-deadletter-processed`); DB-health
  pause/resume; per-record poison isolation for bulk messages; batch-persist row aggregation.
  > **Ops:** provision & monitor the two new topics; **parking-topic growth is the
  > terminal-poison signal** to alert on.

## Related Documents

- v2.1 release notes (detailed change record: `docs/release-notes-v2.1-source.md`)
- Per-service technical docs, refer README.md of each service.
