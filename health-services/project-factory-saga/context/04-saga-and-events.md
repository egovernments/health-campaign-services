# 04 — Saga Engine & Events (LLD digest, from doc 03)

## Chosen approach
**Orchestration, not choreography.** A **custom, DB-backed state machine**. The
**`SagaCoordinator` (a Kafka consumer)** is the *only* writer of saga state; transitions
are persisted rows advanced on event arrival; a scheduled **reaper** rescues stuck
instances. (ADR-001: not Temporal/Camunda/Spring StateMachine.)

## Two entry flows (driven by `CampaignDetails.action`)
- **`action="draft"`** — enrich + persist campaign (boundaries/hierarchy/resources).
  If `isGenerationTriggerNeeded` (boundaries/type/source changed & boundaries non-empty)
  → **TEMPLATE_GENERATE** mini-saga. **No entity creation.** Admin drafts *repeatedly*.
  - `isUnifiedSheet` (=`additionalDetails.isUnifiedCampaign`): true → 1 unified workbook
    (internal excel module); false → separate boundary/user/facility workbooks
    (microplan: boundary + facilityWithBoundary).
  - own-your-writes removes the old 4× DB poll before generating.
- **`action="create"`** — everything draft does **plus** the full campaign-creation saga.
  Admin creates *once* (filled sheet → real entities).

## Registry-driven phase DAG (NOT a fixed step list)
Step set is data-driven by a **resource-type registry** (`orchestration/registry/`).
Each type declares `phase`, `dependsOn`, `isRequired`, `parentType`,
`sharedAcrossCampaignFamily`, dispatch `kafkaKey`. Verified registry:

| type | phase | dependsOn | required | parent | shared |
|---|---|---|---|---|---|
| facility | 1 | — | ✅ | — | — |
| user | 1 | — | ✅ | — | — |
| boundary (=project) | 1 | — | ✅ | — | — |
| attendanceRegister | 2 | projectCreation | ❌ | — | ✅ |
| attendanceRegisterAttendee | 3 | attendanceRegisterCreation | ❌ | attendanceRegister | ✅ |

- **mapping** = separate sibling flow (topic `start-admin-console-mapping-task`), after
  phase-1 trio, own retry budget on `eg_cm_campaign_mapping_data`.
- **worker-registry** = a **sub-step inside CREATE_USERS** (not a step): after each HRMS
  user batch, same worker waits for Individual searchability
  (`waitForIndividualsSearchable`, fail-open, bounded, observable metric
  `pf_individual_consistency_wait`), bulk-upserts workers (idempotent), writes `workerId`
  back. Partial failure non-fatal (marked FAILED at user level, not campaign-blocking).
- **attendance** = separate **upload-triggered** flow (`ATTENDANCE_PROVISION`), phases
  2→3 via same engine; register by `serviceCode`, attendee by
  `registerServiceCode_username_sheetType`; batched direct REST (register 100, attendee 50).

## Core create saga
`VALIDATE_INPUT → (CREATE_FACILITIES ∥ CREATE_USERS ∥ CREATE_PROJECTS) → MAP_ENTITIES → FINALIZE → COMPLETED`.
Failure path: any step budget-exhausted/non-retryable → `COMPENSATING` (reverse
compensations) → `FAILED`. Compensation targets **campaign association** (unmap/
deactivate), NOT global delete (registers/attendees are shared across campaign family).

## States
`PENDING → VALIDATED → CREATING → MAPPING → COMPLETED`; plus `COMPENSATING`, `FAILED`.
`CREATING` is composite: per-entity progress in `eg_cm_campaign_data(status)` keyed by
`(campaignNumber, type, uniqueIdentifier)` — each of 70K users retried independently. A
step "completes" when per-entity counts reconcile.

## Three drivers (nothing else mutates saga state)
| Driver | Trigger | Home |
|---|---|---|
| **SagaCoordinator** | `pf.saga.command`, `pf.saga.reply`, `hcm-processing-result` | worker/orchestrator (partitioned by campaignNumber ⇒ serial per campaign) |
| **Step Workers** | `hcm-*-create-batch` | worker pods, virtual threads; do work, emit reply, never write saga state |
| **Reaper** | scheduled (~30s) | orchestrator, leader-elected; re-drives RUNNING-past-SLA |

**Invariant:** SagaCoordinator is single writer of `eg_cm_saga_execution`/`_step`.
Partition-by-`campaignNumber` serializes; `SELECT ... FOR UPDATE` on the saga row guards
coordinator vs reaper. Coordinator loop = one local TX: lock saga → resolve transition
(state table) → run action → set next state → save → `inbox.record(key)` (same TX);
commit ⇒ outbox rows + saga state atomic; relay publishes after commit.

## Retry SM (per entity/step)
`READY → IN_FLIGHT → (DONE | FAILED)`; FAILED with attempts<max → `BACKING_OFF` (R4j
exp+jitter) → READY; attempts≥max / non-retryable / CB-open-terminal → `DEAD_LETTER` →
`pf.dlq.<step>`. Retryable: 5xx, timeout, conn reset, 429 (honor Retry-After).
Non-retryable: 4xx validation/business. Retry counters persist on `eg_cm_campaign_data`
(`retryCount`, `lastError`) — extend the pattern already on `_mapping_data`.

## Event envelope (all events)
`eventId, eventType, schemaVersion, occurredAt, tenantId, campaignNumber (=partition key),
correlationId (=W3C traceparent), idempotencyKey, payload`. Coordinator dedups on
`idempotencyKey` via inbox. Additive fields only within a major `schemaVersion`.

## Event catalog (key ones)
| eventType | topic | idempotencyKey |
|---|---|---|
| CampaignDrafted | save-project-campaign-details | campaignNumber:draft:<rev> |
| TemplateGenerationRequested | pf.saga.command | campaignNumber:GEN:<boundarySetHash> |
| GeneratedResourceReady | internal + notification-email | campaignNumber:GEN:<type>:<hash> |
| CampaignCreated | save-project-campaign-details | campaignNumber |
| RowsValidated | hcm-processing-result | referenceId |
| UserBatchCreated / FacilityCreated / ProjectCreated | pf.saga.reply | campaignNumber:<STEP>:batch-N / <id> |
| MappingCompleted | pf.saga.reply | campaignNumber:MAP:batch-N |
| WorkerBatchSynced (sub-step of USERS) | pf.saga.reply | campaignNumber:WORKER:batch-N |
| AttendanceResourceUploaded (trigger) | start-admin-console-task | campaignNumber:ATT:<resourceId> |
| CampaignCompleted | notification-email + internal | campaignNumber:COMPLETE |
| CampaignFailed | hcm-campaign-mark-failed | campaignNumber:FAILED |
| StepDeadLettered | pf.dlq.<step> | campaignNumber:<step>:dlq:<entityId> |

**Existing topic names (`hcm-*`, `save/update-*`) are preserved** during migration so
current consumers/persister keep working.
