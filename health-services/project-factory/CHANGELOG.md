# Changelog
All notable changes to this module will be documented in this file.

## 0.1.0 - 2024-05-28
#### Base ProjectFactory service version 0.1
  1. ProjectFactory Service manages campaigns: creation, updating, searching, and data generation.
  2. Project Mapping : In campaign creation full project mapping is done with staff, facility and resources along with proper target values.
  3. Create Data: Validates and creates resource details of type facility,user and boundary.
  4. Generate Data: Generates sheet data of type facility,user and boundary.
  5. Boundary and Resource Validation: Validates boundaries and resources during campaign creation and updating.

## 0.2.0 - 2024-08-7
#### ProjectFactory service version 0.2
   1. Timeline integration for workflow of campaign.
   2. Call user, facility and boundary generate when boundaries changed in campaign update flow
   3. Generate target template based on delivery conditions changed to anything from default.

## 0.3.0 - 2024-12-03
#### ProjectFactory service version 0.3
  1.  Campaign Details Table Updates -> added new columns: parentId and active,removed unique constraint on campaignName.
  2.  Update Ongoing Campaign (can add new boundaries , edit facilities , user and target sheet).
  3.  Boundary Management Apis added.
  4.  Microplan Integration api (fetch-from-microplan api) added.


## 0.3.1 - 2025-02-13
#### ProjectFactory service version 0.3.1
  1. Facility creation changed from bulk to individual api for better error handling.
  2. Fixed the template generation cache to support multiple language template at same time.  
  3. Introduced the template validation based on meta data like locale & camapign id
  4. Target to dashboard mapping was introduced for additional configurations
  5. Boundary Bulk creation patch by trimming the boundary names if it exceeds the max limit


## 0.4.0 - 2025-07-14
#### ProjectFactory service version 0.4.0
  1. V2 data process flow completed for data creation and validation
  2. V2 template generation implemented
  3. Data processing and generation logic modularized and loosely coupled
  4. App config and localization creation handled from backend
  5. Retry functionality integrated from backend


  ## 1.0.0 - 2026-01-29
#### ProjectFactory service version 1.0.0
  1. Excel handling logic got updated
  2. Integrated with Simplified consolidated sheet for campaign creation
  3. MDMS-Based Configuration
  4. Unified Excel Support
  5. Enhanced Error Reporting
  6. Boundary Management clean up 

## 1.0.1 - 2026-06-24
#### ProjectFactory service version 1.0.1
  1. Reliable startup Kafka topic creation and connection (including per-state prefixed topics for central instance) so campaigns no longer get stuck failing to start.
  2. GZIP-compressed Kafka messages and raised consumer fetch size (4MB) to handle large campaign payloads without running out of memory.
  3. Facility-to-boundary mapping keyed on facility id / boundary code instead of name, fixing facilities mapping to the wrong boundary.
  4. All batch / chunk sizes made configurable, and MAX_CONCURRENT made configurable.
  5. Resources deactivated when the campaign hierarchy or boundaries change.
  6. Campaign user (worker) creation now waits for newly created individuals to become searchable before the worker bulk-create call, fixing rows intermittently failing with `INDIVIDUAL_NOT_FOUND` (a read-after-write race between HRMS individual creation and worker-registry).
  7. Attendance Register hardening (PR #2018): guarded crashes on a missing parent boundary during template generation and on out-of-range row indexes in register validation; removed user PII from generation logs; made attendee username de-duplication O(n); the HTTP client timeout is now NaN/blank-safe and falls back to a 5-minute default (an explicit 0 still disables it).
  8. De-enrollment date is now sent on attendee/staff create and staff delete, with zero-clamped dates skipped.
  9. attendanceRegister / attendee resource search now resolves across the whole campaign family via the shared `campaignNumber`.
  10. User mappings are de-mapped only for phone numbers explicitly present in the uploaded sheet, preserving externally-created project staff on target-only updates.
  11. Mapping reconciliation reworked into a convergence-driven reconciler with retry budgets and direction-aware failure tracking.

## 1.0.2 - 2026-07-20
#### Reliability hardening
  1. Kafka consumer switched from at-most-once to at-least-once delivery: the fire-and-forget semaphore was removed and `eachMessage` now awaits the handler, so the offset commits only after processing completes and a crash/restart/rebalance mid-processing redelivers the message instead of silently dropping it. Consumer concurrency is bounded by `partitionsConsumedConcurrently = KAFKA_CONSUMER_MAX_CONCURRENT` (default 5).
  2. Handlers made idempotent for redelivery: facility batch re-reads live DB status and creates only not-yet-completed rows; task and legacy mapping-task short-circuit when their process is already `completed`; per-boundary project creation adopts an existing project for the same boundary + `campaignNumber` before creating.
  3. Adopted-user reconciler: a `pending` user row whose phone already exists in HRMS is marked terminally completed (sheet status `EXISTING`, existing login id surfaced on the credential sheet) so partially-created campaigns converge to zero pending rows instead of the completion poller timing out.
  4. HRMS per-user create fallback now runs in bounded-concurrency windows with retry + backoff for transient errors (e.g. `Failed to obtain JDBC Connection`) instead of an unbounded parallel batch that exhausted the downstream DB pool; permanent errors (already-exists / validation) are not retried.
  5. Producer pacing for user-create batches: pause `USER_KAFKA_PRODUCE_WINDOW_DELAY_MS` after every `USER_KAFKA_PRODUCE_WINDOW_SIZE` batches (delay `0` = no pacing) so small batch sizes don't flood the topic instantly.
  6. Boundary sync-retry delay made configurable via `BOUNDARY_SYNC_RETRY_DELAY_MS` (default 4000ms), replacing the hardcoded 1000ms in `tryTriggerGenerateIfBoundariesSynced`.
  7. Blank / non-ASCII localization fallback: `getLocalizedName` returns the code when the localized value is missing or blank so a not-yet-localized name never renders as an empty Unified Template cell.
  8. `decrypt()` guards non-encrypted / undefined input (adopted users carry a plaintext username and no generated password) instead of crashing during credential-sheet generation.
  9. Skipped user-boundary mappings are revived to `toBeMapped` on retry once the user exists, so the user is actually assigned to the project.
  10. New / changed config keys: `BOUNDARY_SYNC_RETRY_DELAY_MS`, `USER_KAFKA_PRODUCE_WINDOW_SIZE`, `USER_KAFKA_PRODUCE_WINDOW_DELAY_MS`, `USER_HRMS_FALLBACK_CONCURRENCY`, `USER_HRMS_FALLBACK_MAX_RETRIES`, `USER_HRMS_FALLBACK_BACKOFF_MS`, `USER_HRMS_FALLBACK_WINDOW_DELAY_MS`, `USER_SEARCH_CONCURRENCY`; default `USER_KAFKA_CREATE_BATCH_SIZE` changed 30→20.
