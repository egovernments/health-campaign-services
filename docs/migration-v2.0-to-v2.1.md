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

## Related Documents

- v2.1 release notes (detailed change record: `docs/release-notes-v2.1-source.md`)
- Per-service technical docs, refer README.md of each service.
