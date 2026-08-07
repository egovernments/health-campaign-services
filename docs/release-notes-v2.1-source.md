# Health Campaign Services — v2.1 detailed change record (release-notes source)

<!-- NOT the migration guide — that is docs/migration-v2.0-to-v2.1.md (official short format). -->
<!-- This is the detailed v2.0→v2.1 change record, kept as source material for the v2.1 -->
<!-- release notes / internal runbook. Branch names and image tags below are a UAT-time -->
<!-- snapshot; everything has since merged into the respective masters. -->
<!-- From: v2.0  →  To: v2.1   Target env: unified-uat   Breaking: yes -->
<!-- Product/repo-level: covers every changed service across health-services/ and -->
<!-- core-services/, plus DB, configs, MDMS, backbone and infra in one document. -->
<!-- Sources: v2.0..HEAD diff; nigeria-go-deep / nigeria-go-deep-2 branches; -->
<!-- the "Builds for Nigeria go deep changes" UAT build tracker (2026-06-11); -->
<!-- live MDMS on the unified-uat cluster (2026-06-11); .gemini DataAccessException summary. -->

This guide upgrades the Health Campaign Services platform from **v2.0** to **v2.1**
(the `nigeria-go-deep-2` line). It is run by DevOps + the release owner against a
target environment (validated on **unified-uat**), and covers application services,
database migrations, and the config/Helm/infra changes that live in the companion repos.
The end state is v2.1 of all changed services, **four new services** deployed
(**worker-registry**, **egov-notification-push**, **health-notification-service**,
**airflow-trigger-service**), the project-factory campaign-resources data migrated out of
JSONB into a dedicated table, attendance register/attendee support enabled, the
**VAPT security hardening** (DB-error masking + PII encryption) carried over from `master`,
the **DIGIT-Works** attendance/payments services (from the `nigeria-go-deep` branch)
deployed alongside, and the matching **UI + multi-architecture** builds rolled out.

> Scope is drawn from: the `v2.0..HEAD` diff; the `nigeria-go-deep` / `nigeria-go-deep-2`
> branches; the **UAT build tracker** ("Builds for Nigeria go deep changes", 2026-06-11)
> which records the exact image tags and the config/devops PRs applied to unified-uat;
> **live MDMS on the unified-uat cluster** (queried 2026-06-11); and the VAPT changes
> already in `master`. Items that still need a human to confirm against the running env are
> flagged in **Open Questions / Needs Confirmation**.

## Table of Contents

1. [Overview of Changes](#1-overview-of-changes)
2. [Scope & Impact](#2-scope--impact)
3. [Prerequisites](#3-prerequisites)
4. [Breaking Changes](#4-breaking-changes)
5. [Migration Steps](#5-migration-steps)
6. [Verification](#6-verification)
7. [Rollback Plan](#7-rollback-plan)
8. [Service Image Reference](#8-service-image-reference)
9. [MDMS & Config Reference (unified-uat)](#9-mdms--config-reference-unified-uat)
10. [Related Documents](#10-related-documents)
11. [Open Questions / Needs Confirmation](#11-open-questions--needs-confirmation)

## 1. Overview of Changes

| Category | What changed in v2.1 |
| -------- | -------------------- |
| New services | **worker-registry** (worker registry for attendance & payments); **egov-notification-push** (Firebase Cloud Messaging push service); **health-notification-service** (health-domain notifications); **airflow-trigger-service** (Airflow DAG trigger service — new in this release) |
| Updated services | project-factory, excel-ingestion, referralmanagement, stock, project, household, individual, facility (health-services); egov-hrms, pgr-services, beneficiary-idgen, service-request (core-services). Microplanning line also rolled together: plan-service, census-service, resource-generator, boundary-management, transformer, product |
| UI / multi-arch | workbench-ui (nigeria changes — HCMPRE-3928), dashboard-ui, payments-ui (HCMPRE-3928), health-ui (multi-arch), microplan-ui (arm64), health-service-request (multi-arch). See §8 |
| Shared libraries | `health-services-models` → `1.0.35-SNAPSHOT` (new USER ACTION enums, new task statuses, `campaignNumber`); `health-services-common` → `1.1.5-SNAPSHOT` |
| DB schema | 18 new Flyway migrations (see §5 Step 1). Notably: project-factory **campaign resources moved from `campaigndetails` JSONB into `eg_cm_resource_details`** with a one-time data backfill; worker-registry tables created; egov-notification-push device-token tables created; `campaignNumber` added to stock |
| Persister / indexer configs | Updated/added in the `configs/` repo (`configs/health/egov-persister` + `egov-indexer`). New: egov-notification-push, health-notification persisters + health-notification indexer; stock `campaignNumber`; project-factory resource tables; referral `projectId`. Applied via **config PRs #3811–#3840** on unified-uat (§9) |
| MDMS masters | **enc-client `DataSecurity.SecurityPolicy`** is the hard dependency (§3/§9). Attendance roles are **standard RBAC** (`ACCESSCONTROL-ROLES`), not a service-level MDMS read. No `FormConfig` master is used by the attendance backend — that earlier assumption was incorrect (§9) |
| Backbone | Kafka: GZIP compression + `max.message.bytes` raised to 4 MB on large-payload topics; topics auto-created at startup; consumer fetch size raised; central-instance topic-prefix handling |
| Infrastructure | Kubernetes health-probe endpoint added to project-factory; OpenJDK base image updated (build); multi-architecture (amd64/arm64) images introduced for several UIs/services. Helm/devops changes applied via **devops PRs #4480–#4579** on unified-uat (§9) |
| Localization | New keys for attendance/worker/validation flows — **not stored in `configs/` or `health-campaign-config/`** (owned by egov-localization seed data). Still needs confirmation (§11) |
| Security (VAPT, from `master`) | DB-error responses no longer leak SQL/prepared statements (DataAccessException → root cause only). In the health vertical this was delivered via a **per-service `DataAccessExceptionHandler`** (ticket **HCMPRE-1870**), **not** a tracer bump — tracer stays `2.9.0-SNAPSHOT`/`2.9.1-SNAPSHOT` (§4.6). PII fields encrypted (enc-client) |
| External (DIGIT-Works) | Attendance & payments provided by DIGIT-Works services built from the `nigeria-go-deep` branch, deployed in the `health` namespace as `health-attendance`, `health-muster-roll`, `health-expense`, `health-expense-calculator` |

## 2. Scope & Impact

| Area | Change | Impact |
| ---- | ------ | ------ |
| Service(s) | 4 new (worker-registry, egov-notification-push, health-notification-service, airflow-trigger-service) + updated health/core/microplanning services | New deployments + rolling upgrades; brief per-service unavailability during restart |
| UI / multi-arch | workbench-ui, dashboard-ui, payments-ui, health-ui, microplan-ui, health-service-request | Front-end + multi-arch image rollout; verify node arch (arm64/amd64) matches the build |
| DB schema | 18 Flyway migrations; one data backfill (resources JSONB→table) | Run on boot via Flyway; backfill is idempotent but should run during the window |
| MDMS masters | enc-client `DataSecurity.SecurityPolicy`; RBAC roles for attendance/works | **SecurityPolicy must cover every encrypted model for the tenant** or the service NPEs at boot; works/attendance RBAC roles must exist for the operating tenant (§9) |
| Kafka topics | New large-payload topics; gzip + 4 MB max message; new notification/push topics | **Broker `message.max.bytes` must allow ≥ 4 MB** or large campaign payloads are rejected |
| Config / persister-indexer | New tables / downsync need persist+index configs | Applied via config PRs #3811–#3840 (§9); restart persister/indexer consumers |
| Helm values / secrets | New service + new env/config keys | Applied via devops PRs #4480–#4579 (§9); worker-registry + airflow chart/values + FCM secret needed |
| Infrastructure (Terraform) | No code-visible Terraform change in this repo | Likely none for in-place upgrade (**confirm** — §11) |
| Push notifications | egov-notification-push device-token tables (5 migrations); FCM credentials | New DB + FCM service-account secret required |
| PII encryption (enc-client) | PII fields encrypted; needs MDMS `DataSecurity.SecurityPolicy` | **On unified-uat, the `mz` tenant is missing 5 SecurityPolicy models that `pg` has** (§9) — bank-account/payments encryption would NPE on `mz`. Reconcile before deploy |
| External (DIGIT-Works) | `health-attendance`, `health-muster-roll`, `health-expense`, `health-expense-calculator` (from `nigeria-go-deep`) | Separate repo + deploy; VAPT handlers also landed in attendance/expense/expense-calculator/muster-roll (per-service) |

## 3. Prerequisites

- Database backups for every affected service schema (esp. project-factory `eg_cm_*`).
- MDMS + configs snapshot for the target tenant.
- Maintenance / downtime window (the project-factory resources backfill should run with campaign creation paused).
- Access: target cluster + namespace, container registry, `configs/` + `DIGIT-DevOps/` repos.
- Tooling: `kubectl`/`helm`/`helmfile`, Flyway (bundled in service migration images).
- Confirm Kafka broker `message.max.bytes` (and `replica.fetch.max.bytes`) ≥ 4 MB before deploy.
- **MDMS `DataSecurity.SecurityPolicy` master must exist for the target tenant and cover every encrypted model** before deploying enc-client-using services — otherwise they fail to boot (NPE, "attributes is null"). On **unified-uat** the `pg` state tenant has 15 policy models and `mz` has 10 — **`mz` is missing `BankAccountDecrypt`, `BankAccountEncrypt`, `BankAccountHolderNameEncrypt`, `BankAccountNumberEncrypt`, and `Organisation`** (§9). Worker/Individual/User encryption is present under both. If payments/bank-account encryption runs on `mz`, add those policies first.
- Confirm the **works/attendance RBAC roles** exist for the operating tenant. On unified-uat, `pg` carries `WORKS_*`, `MUSTER_ROLL_*`, `SANITATION_WORKER`; `mz` carries only `HEALTH_FACILITY_WORKER` (§9).
- FCM service-account credentials available as a secret for egov-notification-push (do not commit the JSON to the repo).
- DIGIT-Works `nigeria-go-deep` builds (attendance/payments) available in the registry for the target env.

## 4. Breaking Changes

### 4.1 Campaign resources moved out of JSONB into a dedicated table (project-factory)

- **What changed:** Campaign resources previously lived inside the `campaigndetails`
  JSONB column. v2.1 introduces the `eg_cm_resource_details` table (migration
  `V20260317100000` adds `parentresourceid`, `filename`, `isactive`) and a one-time
  idempotent backfill (`V20260317100001`) that copies existing resources into it. A
  dedicated resource CRUD API (persister topics `create-resource-details` /
  `update-resource-details` → `health.eg_cm_resource_details`, see §9) replaces
  JSONB-embedded resource handling.
- **Why it matters:** Existing campaigns' resource data must be migrated; downstream
  reads of resources change. Backward-compat shims were added for the old resource APIs.
- **Action required:** Run Step 1 migrations during the window; verify backfill row
  counts (§5 Step 1 / §6) before resuming campaign creation.

### 4.2 `campaignNumber` introduced as a linking identifier

- **What changed:** Attendance registers are now linked by **campaign number** instead
  of campaign id; `campaignNumber` was added to stock APIs/models (migration
  `V20260422000000`), persisted via the stock persister, and to `health-services-models`.
- **Why it matters:** Clients/integrations keyed on campaign id for these flows must
  switch to campaign number.
- **Action required:** Rebuild consumers against `health-services-models 1.0.35`;
  confirm integrations send/expect `campaignNumber`.

### 4.3 Shared model/common version bump

- **What changed:** `health-services-models` → `1.0.35-SNAPSHOT` (new USER ACTION enums,
  new task statuses), `health-services-common` → `1.1.5-SNAPSHOT`.
- **Why it matters:** All consuming services must build/deploy against these versions
  together to avoid enum/status deserialization mismatches.
- **Action required:** Deploy the dependent services as a set (Step 3).

### 4.4 Kafka large-payload + topic-creation behavior

- **What changed:** GZIP compression and `max.message.bytes=4MB` on large-payload
  topics; topics auto-created at startup; raised consumer fetch size; central-instance
  topic-prefix handling.
- **Why it matters:** Brokers must permit ≥ 4 MB messages; topic auto-creation needs the
  right ACLs/config.
- **Action required:** Align broker config (§3) before deploying project-factory.

### 4.5 Stock user-action update validation relaxed

- **What changed:** Row-version validation disabled for user-action updates and the
  search limit cap lifted to support bulk stock-count updates.
- **Why it matters:** Concurrent-update protection differs; bulk operations now allowed.
- **Action required:** Note for QA/ops; no migration step, but validate bulk flows.

### 4.6 VAPT security hardening (carried from `master`)

- **What changed:**
  - **DB-error masking:** a `DataAccessException` handler now returns only the root-cause
    message (code `QUERY_EXECUTION_ERROR`, HTTP 500) instead of echoing the full SQL /
    prepared-statement in API error responses. **In the health vertical and DIGIT-Works
    this was delivered by adding a per-service `DataAccessExceptionHandler`
    `@ControllerAdvice` directly in each service** (ticket **HCMPRE-1870**) — **tracer was
    NOT bumped.** Tracer stays `2.9.0-SNAPSHOT` (most HCM services) / `2.9.1-SNAPSHOT`,
    and `2.9.0-SNAPSHOT` across DIGIT-Works. (The tracer-version-bump approach
    — `2.9.0-data-access-error-SNAPSHOT` / `2.1.2-data-access-error-SNAPSHOT` — was used
    only for the Digit-Core central services egov-otp / egov-workflow-v2 / MDMS-v2 /
    egov-user, which are **not** part of this HCM release.)
  - **PII encryption (enc-client):** personally identifiable fields are encrypted; APIs
    return encrypted values unless explicitly decrypted.
- **Why it matters:** closes information-disclosure findings (leaked queries, plaintext
  PII). enc-client depends on the MDMS `DataSecurity.SecurityPolicy` master for the
  tenant — **without a policy for an encrypted model, those services NPE at startup.**
- **Action required:** ensure each affected service ships with its in-service handler
  (HCMPRE-1870); upload/verify the `DataSecurity.SecurityPolicy` MDMS master for every
  encrypted model before deploy (§3/§9).
- **HCM services carrying the in-service handler (confirmed in tree):** plan-service,
  project, census-service, egov-survey-services, service-request, egov-hrms, pgr-services
  (plus household / attendance per the HCMPRE-1870 build tags).

## 5. Migration Steps

> Run top to bottom. Each phase ends with its own verification.

### Step 1 — Database migrations

Flyway runs these on service boot. New migrations in v2.1:

| Service | Migration | Purpose |
| ------- | --------- | ------- |
| worker-registry | `V20260226120000__worker_registry_create_ddl.sql` | **Create** worker-registry tables (new service) |
| worker-registry | `V20260331120000__worker_registry_add_beneficiary_code.sql` | Add beneficiary code |
| egov-notification-push | `V20260225120000__create_device_token_table_ddl.sql` | **Create** device-token table (new service) → `health.eg_push_device_tokens` |
| egov-notification-push | `V20260225130000__remove_active_and_update_unique_constraint_ddl.sql` | Drop active flag; update unique constraint |
| egov-notification-push | `V20260316120000__add_facilityid_column_ddl.sql` | Add facility id |
| egov-notification-push | `V20260325120000__update_unique_constraint_for_multi_facility_ddl.sql` | Multi-facility unique constraint |
| egov-notification-push | `V20260402120000__add_userroles_column_ddl.sql` | Add user roles |
| project-factory | `V20260317100000__add_parent_resource_id_filename_isactive.sql` | Add columns to `eg_cm_resource_details` |
| project-factory | `V20260317100001__migrate_resources_jsonb_to_table.sql` | **Data backfill** JSONB → table (idempotent, `NOT EXISTS` guard) |
| stock | `V20260422000000__add_campaignNumber_to_stock.sql` | Add `campaignNumber` |
| excel-ingestion | `V20260530120000__add_sheet_data_temp_search_index_ddl.sql` | Search index for large sheets |
| referralmanagement | `V20250108120000__hf_referral_projectfacilityid_index.sql` | Index on project facility id |
| referralmanagement | `V20250109120000__hf_referral_add_localitycode_ddl.sql` | Add locality code |
| referralmanagement | `V20260211164600__referral_project_id_create_ddl.sql` | Add referral `projectId` |
| referralmanagement | `V20260223150400__referral_project_id_index_ddl.sql` | Index referral `projectId` |
| referralmanagement | `V20260423100000__downsync_generation_audit_ddl.sql` | Downsync generation audit |
| referralmanagement | `V20260425130000__downsync_category_filesize_ddl.sql` | Downsync category file size |
| referralmanagement | `V20260426140000__household_address_mv_indexes_ddl.sql` | Household address MV indexes |

- **Verify:** Flyway `schema_version`/migration history shows all applied with no
  failures; `SELECT count(*) FROM eg_cm_resource_details` matches the resource count
  previously embedded in `campaigndetails` JSONB; worker-registry and
  `eg_push_device_tokens` tables exist.
- **health-notification-service:** confirm its DB migrations from the
  `health-notification-service` branch (its persister writes to `health.scheduled_notification`, §9). **(Confirm — §11)**

### Step 2 — Deploy new services

- **worker-registry** (new): deploy chart + DB migration. Provides worker registry for
  attendance and payments; integrated with project-factory user/worker flows and
  egov-hrms. Image: `worker-registry:master-nigeria-go-deep-2-2a12aa3` (§8). Reads no MDMS.
- **egov-notification-push** (new): deploy with the FCM service-account secret + DB
  migrations (device-token tables). Firebase Cloud Messaging push to app/mobile users.
  Images: app `egov-notification-push:egov-notification-push-e12e436`, db
  `egov-notification-push-db:egov-notification-push-e12e436` (§8).
- **health-notification-service** (new): deploy from the `health-notification-service`
  branch (health-domain notifications). Image
  `health-notification-service:egov-notification-push-e12e436`. Persister/indexer in §9.
- **airflow-trigger-service** (new): `airflow-trigger-service:master-nigeria-go-deep-2-19579e5`
  (§8). Triggers Airflow DAGs; confirm its config/Helm + the Airflow config/devops PRs
  (config #3825, devops #4562) are applied (§9). **(Confirm topics/secrets — §11)**
- **DIGIT-Works attendance/payments** (separate repo, `nigeria-go-deep`): ensure the
  `health`-namespace builds are deployed — `health-attendance`, `health-muster-roll`,
  `health-expense`, `health-expense-calculator` (§8).
- **Verify:** all new pods healthy in the `health` namespace; worker search/create
  reachable; a push notification delivers; an attendance register flows end-to-end.

### Step 3 — Update existing services

Roll out together (shared model/common bump — §4.3). Image tags: see §8.

- **health-services:** project-factory, excel-ingestion, referralmanagement, stock,
  project, household, individual, facility
- **core-services:** egov-hrms (adds OpenTelemetry; listens to first attendance log
  events for worker signature/photo), pgr-services, beneficiary-idgen, service-request
- **microplanning line (rolled together on unified-uat):** plan-service, census-service,
  resource-generator, boundary-management, transformer, product (see §8 for tags;
  confirm whether all belong to this release window — §11)
- **UI / multi-arch:** workbench-ui, dashboard-ui, payments-ui, health-ui, microplan-ui,
  health-service-request (§8). Match the image arch (arm64/amd64) to the node pool.
- **Verify:** each pod on the new version; project-factory `/health` probe green; smoke a
  campaign search and a stock/referral search; UIs load against the upgraded APIs.

### Step 4 — Update configs (persister / indexer)

Persister/indexer YAMLs live in the **`configs/` repo** (`configs/health/egov-persister`
and `configs/health/egov-indexer`). The v2.1 changes (full map in §9) were applied to
unified-uat via **config PRs #3811–#3840**. Restart persister/indexer consumers after
applying.

- **Verify:** records persist/index for the new flows (push device tokens, scheduled
  notifications, stock `campaignNumber`, project-factory resources, referral `projectId`).

### Step 5 — Update Helm environment config & secrets

In **DIGIT-DevOps**. Applied to unified-uat via **devops PRs #4480–#4579**. Expect:
worker-registry + airflow-trigger-service values/charts, the FCM secret for
egov-notification-push, new env keys for configurable batch sizes / consumer group ids /
attendance paths, Kafka producer (gzip, max message) and consumer (fetch size) settings.

- **Verify:** pods pick up config; no boot errors.

### Step 6 — Backbone / infrastructure

- Kafka: confirm broker `message.max.bytes` ≥ 4 MB; topic auto-creation enabled/ACLs OK.
- OpenJDK base image updated in build; multi-arch (amd64/arm64) images introduced —
  ensure the registry has the arch your nodes run.
- **Verify:** large campaign upload (tens of thousands of rows) succeeds end-to-end;
  no `RecordTooLarge` errors in producer logs.

### Step 7 — Terraform / infra-as-code

No Terraform/infra-as-code change is visible in this repo's diff. For an in-place
cluster upgrade this step is likely **not required**.

- **Verify:** N/A unless DevOps confirms otherwise. **(Confirm — §11)**

## 6. Verification

- All Flyway migrations applied; resources backfill row count reconciled.
- worker-registry reachable; attendance register + attendee template generation works.
- Campaign creation completes for a large user batch (retry/large-payload paths).
- Stock & referral search return expected results (incl. `campaignNumber`, downsync).
- Push notification delivers via egov-notification-push (FCM); scheduled notification
  persists to `health.scheduled_notification` and indexes to `scheduled-notification-v1`.
- No DB-detail leakage in API error responses (DataAccessException → `QUERY_EXECUTION_ERROR`).
- No errors in project-factory / excel-ingestion logs for large-dataset processing.

## 7. Rollback Plan

1. Helm-revert each updated service to its **UAT-backup image** (the prior tag recorded in
   the build tracker — see §8 "rollback tag" column); scale down the four new services.
2. Database: the resources backfill is additive (new table). To roll back, point
   project-factory v2.0 at the JSONB source again; **do not drop `eg_cm_resource_details`
   until v2.0 is confirmed stable** (data is also still derivable from JSONB). New-service
   tables (worker-registry, `eg_push_device_tokens`, `scheduled_notification`) can remain.
3. Revert persister/indexer config (config PRs #3811–#3840) and restart consumers.
4. Revert Helm values/secrets and Kafka broker config changes (devops PRs #4480–#4579).
5. Note the recorded reverts already in the ledger: config #3826 reverted #3824, config
   #3827 reverted #3826 (§9) — confirm the net config state before rollback.

## 8. Service Image Reference

Tags from the **UAT build tracker** ("Builds for Nigeria go deep changes", 2026-06-11),
as deployed to unified-uat. "Rollback tag" is the prior/backup image recorded for that
service. Reconcile against the CI pipeline before promoting to another environment.

### New services

| Service | v2.1 image tag | DB-migration image |
| ------- | -------------- | ------------------ |
| worker-registry | `worker-registry:master-nigeria-go-deep-2-2a12aa3` | (in service image) |
| egov-notification-push | `egov-notification-push:egov-notification-push-e12e436` | `egov-notification-push-db:egov-notification-push-e12e436` |
| health-notification-service | `health-notification-service:egov-notification-push-e12e436` | — (confirm §11) |
| airflow-trigger-service | `airflow-trigger-service:master-nigeria-go-deep-2-19579e5` | — |

### Updated health / core services

| Service | v2.1 image tag | Rollback tag (UAT backup) |
| ------- | -------------- | ------------------------- |
| project-factory | `project-factory:nigeria-go-deep-2-97ed978` | `project-factory-db:user-batch-reduce-934c542` |
| stock | `stock:nigeria-go-deep-2-97ed978` | `stock:master-eb25d83` |
| referralmanagement | `referralmanagement:nigeria-go-deep-2-97ed978` | `referralmanagement:downysnc-file-upgrade-nigeria-3d94577` |
| health-project | `health-project:nigeria-go-deep-2-97ed978` | `health-project:featurefacilitysearch-f20c740` |
| excel-ingestion | `excel-ingestion:master-nigeria-go-deep-2-fb85cab` | — |
| household | `household:HCMPRE-1870-errorHandlingForQueryIssues-34e8b4c` | — |
| health-individual | `health-individual-db:Individual-master-register-studio-869b3c3` | `health-individual-db:Individual-master-register-studio-d33351a` |
| facility | `facility:master-eb25d83` | — |
| product | `product:master-eb25d83` | — |
| beneficiary-idgen | `beneficiary-idgen:master-eb25d83` | — |
| boundary-management | `boundary-management:master-aa57e79` | — |
| health-hrms | `health-hrms:HCMPRE-4022-HRMS-MOBILE-NUMBER-FIX-67925fa` | — |
| health-pgr-services | `health-pgr-services:master-pgr-user-type-fix-d99672c` | — |
| health-service-request | `health-service-request:multiarch-changes-digit-studio-3fd88be` | — |

### Microplanning line (rolled together on unified-uat)

| Service | image tag |
| ------- | --------- |
| plan-service | `plan-service:microplanning-dev-c29e502` |
| census-service | `census-service:microplanning-dev-c29e502` |
| resource-generator | `resource-generator:microplanning-dev-0aca221` |
| transformer | `transformer:bauchi-transformer-fixes-32f27fa` |

### DIGIT-Works (attendance / payments — `nigeria-go-deep`)

| Service | v2.1 image tag | Rollback tag (UAT backup) |
| ------- | -------------- | ------------------------- |
| health-attendance | `health-attendance:nigeria-go-deep-78c40be` | `health-attendance-db:HCMPRE-1870-DataAccessErrorHandling-a7b1e6b` |
| health-muster-roll | `health-muster-roll:nigeria-go-deep-78c40be` | `health-muster-roll:master-89c74d7` |
| health-expense | `health-expense:nigeria-go-deep-payments-expense-15b5a23` | `health-expense:nigeria-go-deep-78c40be` |
| health-expense-calculator | `health-expense-calculator:nigeria-go-deep-78c40be` | — |

### UI / multi-architecture

| UI | image tag | Notes |
| -- | --------- | ----- |
| workbench-ui | `workbench-ui:HCMPRE-3928-ebf4eea` | nigeria changes; `workbench-ui:master-7a66fbb` carries multi-hierarchy |
| dashboard-ui | `dashboard-ui:HCMPRE-3928-9c9985f` | |
| payments-ui | `payments-ui:HCMPRE-3928-fd0ddab` | |
| health-ui | `health-ui:health-ui-multiarch` | multi-arch |
| microplan-ui | `microplan-ui:console-56ef678-arm64` | arm64 |

## 9. MDMS & Config Reference (unified-uat)

Captured from the live unified-uat cluster (MDMS-v2, 2026-06-11) and the `configs/` /
`health-campaign-config/` repos. Two state tenants exist on unified-uat: **`pg`**
(demo state — `pg`, `pg.citya/b/c`) and **`mz`** (Mozambique). Per the known drift,
services resolve master data from **`pg`** while campaigns run as **`mz`** — so the
tenant the masters live under matters.

### 9.1 enc-client `DataSecurity.SecurityPolicy` (PII encryption)

Module `DataSecurity`, master `SecurityPolicy`. Policy-model coverage:

| Tenant | # models | Models |
| ------ | -------- | ------ |
| `pg` | 15 | BankAccount{Decrypt,Encrypt,HolderNameEncrypt,NumberEncrypt}, Individual{Decrypt,Encrypt,SearchEncrypt,SearchIdentifierEncrypt,SearchMobileNumberEncrypt}, Organisation, User, UserSelf, Worker{Decrypt,Encrypt,SearchEncrypt} |
| `mz` | 10 | Individual{Decrypt,Encrypt,SearchEncrypt,SearchIdentifierEncrypt,SearchMobileNumberEncrypt}, User, UserSelf, Worker{Decrypt,Encrypt,SearchEncrypt} |

> **`mz` is missing 5 models vs `pg`:** `BankAccountDecrypt`, `BankAccountEncrypt`,
> `BankAccountHolderNameEncrypt`, `BankAccountNumberEncrypt`, `Organisation`. Worker /
> Individual / User encryption is present under both, so worker-registry & individual
> encryption are covered — but **bank-account/payments encryption would NPE on `mz`.**
> enc-client loads SecurityPolicy once at startup; uploading a missing policy needs a
> service restart to take effect.

### 9.2 RBAC roles (`ACCESSCONTROL-ROLES`, master `roles`)

| Tenant | Works/attendance roles present |
| ------ | ------------------------------ |
| `pg` | `SANITATION_WORKER`, `WORKS_MASTER_CREATOR`, `WORKS_ADMINISTRATOR`, `WORKS_APPROVER`, `WORKS_BILL_CREATOR`, `WORKS_FINANCIAL_APPROVER`, `MUSTER_ROLL_APPROVER`, `MUSTER_ROLL_VERIFIER` |
| `mz` | `HEALTH_FACILITY_WORKER` only |

If attendance/payments are operated under the `mz` tenant, the corresponding role &
role-action mappings must be added there. Confirm whether the health flow reuses the
`WORKS_*`/`MUSTER_ROLL_*` roles or uses health-specific roles (§11).

### 9.3 MDMS facts that correct earlier assumptions

- **No `FormConfig` master** is used by the attendance backend — probing `FormConfig`,
  `ATTENDANCE`, `works`, `WORKS-ATTENDANCE` modules returned nothing. The attendance
  service's `MDMSUtils` reads **only `tenant.tenants`** (tenant validation).
- **worker-registry reads no MDMS** masters.
- Only **10 MDMS-v2 schema definitions** exist for `pg`; none are attendance/health —
  the relevant one is `FSM.SanitationWorkerFunctionalRoles`. The attendance/works masters
  are **legacy v1 MDMS** (module/master JSON), not v2 schema-based.
- MDMS master data is **not** stored in `configs/` or `health-campaign-config/` — it
  lives in a separate MDMS data repo (not cloned in this workspace).

### 9.4 Persister / indexer (in `configs/health/`)

| File (in `configs/health/egov-persister`) | Topics → table |
| ------------------------------------------ | -------------- |
| `egov-notification-push-persister.yml` (new) | `save/delete/unregister-push-device-token-health` → `health.eg_push_device_tokens` (upsert/delete) |
| `health-notification-persister.yml` (new) | `save/update-scheduled-notification-topic-health` → `health.scheduled_notification` |
| `project-factory-persister.yml` | `create/update-resource-details` → `eg_cm_resource_details`; `create-resource-activity` → `eg_cm_resource_activity`; generated-resource + campaign-details/process/data/mapping topics |
| `stock-persister.yml` | `save/update/delete-stock-health-topic` → `health.STOCK` (now includes `campaignNumber`) |
| `referral-management-persister.yml` | `save/update/delete-referral-health-topic` → `health.REFERRAL` (now includes `projectId`); side-effect; hf_referral (`localitycode`) |

| File (in `configs/health/egov-indexer`) | Topic → ES index |
| ---------------------------------------- | ---------------- |
| `health-notification-service-indexer.yml` (new) | `save/update-scheduled-notification-topic-health` → `scheduled-notification-v1` |
| `referral-management-indexer.yml` | referral/side-effect/hf-referral → `*-index-v1` (+ `user-sync-index-v1`) |
| `stock-indexer.yml` | bulk stock → `stock-index-v1` (campaignNumber flows in `$.Data`) |

> Note: there is **no project-factory or egov-notification-push indexer** — those flows
> do not index to Elasticsearch. The `-health` topic suffix was added in config #3835.
> `health-campaign-config/` carries the attendance/muster persisters & a parallel
> referral persister but **not** the notification configs (those are only in `configs/`).

### 9.5 Config / DevOps PR ledger (unified-uat)

From the build tracker's "Changes" sheet. All on **Unified UAT**.

- **Config repo PRs:** #3811, #3818, #3824, #3825 (Airflow), #3826 (reverted #3824),
  #3827 (reverted #3826), #3828–#3834 (#3834 = Stock), #3836 (Push notification),
  #3837, #3838 (Stock), #3840.
- **DevOps PRs:** #4480, #4522, #4556, #4558, #4562 (Airflow), #4563, #4564, #4565,
  #4568 (Push notification), #4569, #4570 (Push notification), #4575, #4577 (performance
  testing), #4579.

## 10. Related Documents

- Release notes for v2.1 (`nigeria-go-deep-2`).
- Per-service technical docs under `docs/services/`.
- project-factory: `CAMPAIGN_CREATION_OVERVIEW.md`, `CAMPAIGN_CREATION_TECHNICAL.md`,
  `CAMPAIGN_CREATION_FAULT_TOLERANCE.md`, `CAMPAIGN_RESUMABLE_RETRY_DESIGN.md`,
  `USER_RETRY_IMPLEMENTATION.md`.
- DIGIT deployment runbooks (DIGIT-DevOps).
- DIGIT-Works `nigeria-go-deep` branch (attendance & payments services).
- VAPT / DataAccessException handling change summary (`.gemini/data_access_exception_changes_summary.md`).
- UAT build tracker: "Builds for Nigeria go deep changes" (image tags + PR ledger).

## 11. Open Questions / Needs Confirmation

Remaining items not fully resolvable from code/config/MDMS in this workspace:

- **Localization keys** for attendance/worker/validation flows — not stored in `configs/`
  or `health-campaign-config/` (owned by egov-localization seed data). Confirm source + keys.
- **`mz`-tenant MDMS gaps** (§9): confirm whether the missing 5 `SecurityPolicy` models
  and the absent `WORKS_*`/`MUSTER_ROLL_*` roles on `mz` are intentional, or must be added
  before payments/attendance run on `mz`.
- **health-notification-service** DB migrations + Kafka topic list (persister/index known;
  confirm migrations from its branch).
- **airflow-trigger-service** config/Helm, Kafka topics, and any secrets/connection to the
  Airflow instance (config #3825 / devops #4562 applied — confirm completeness).
- **FCM service-account** secret wiring for egov-notification-push (secret name/mount).
- **Microplanning line** (plan-service, census-service, resource-generator, transformer,
  product on `microplanning-dev`/`bauchi` tags): confirm these belong to this v2.1 window
  vs. a separate microplanning release.
- **Whether any Terraform / cluster** change is required for the target env.
- **Exact registry image tags** for a non-UAT target — §8 tags are the unified-uat build
  tracker snapshot; reconcile with the CI pipeline for prod/other envs.
- **Multi-arch** coverage: confirm the target node pool arch (arm64/amd64) matches the
  available images (health-ui, microplan-ui, health-service-request).
</content>
</invoke>
