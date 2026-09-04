# Change Logs

All notable changes to this module will be documented in this file.

## Unreleased - 2026-09-03

Backward-compatibility change set. Every new gate defaults to the OLD behaviour.

- Added `project.required.link.validation` (default `false`). Gates `PtRequiredLinkValidator` and `PbRequiredLinkValidator`, which were previously unconditional. **Corrects the 1.2.4 entry below, which described them as always on regardless of the relationship-validation flag — they are now gated by this separate flag.**

## 1.2.4 - 2026-07-20

- Cross-entity existence validation is now gated behind a new config flag `project.relationship.validation` (default `false` = disabled) for the task and beneficiary create/update chains, so records referencing a not-yet-persisted parent are accepted (offline-first / persister-queue-tolerant). Set `true` to enforce (`PtProjectIdValidator`, `PtProductVariantIdValidator`, `PtProjectBeneficiaryIdValidator` DB lookup, `PbProjectIdValidator`, `BeneficiaryValidator`).
- Added two always-on structural validators `PtRequiredLinkValidator` and `PbRequiredLinkValidator` (error code `REQUIRED_LINK_MISSING`) that reject records missing `clientReferenceId` or a beneficiary link, regardless of the relationship-validation flag — prevents orphan / NOT-NULL-violating rows.
- Within-batch duplicate `clientReferenceId` no longer drops the entire bulk batch: first occurrence kept, each subsequent duplicate flagged individually as a uniqueness error so valid records still persist (task, beneficiary and user-action bulk paths — `PtExistentEntityValidator`, `PbExistentEntityValidator`, `UaExistentEntityValidator`).
- Added an NPE guard in bulk task create enrichment (`ProjectTaskEnrichmentService.enrichAddressesForCreate` now skips tasks with a null address).
- Upgraded `health-services-common` to 1.1.6-SNAPSHOT.

## 1.2.3 - 2026-06-24

- Disabled row-version validation on user-action (stock-count) updates and lifted the search limit cap to support bulk stock-count updates.
- Added project-facility search by boundary type, returning a map of boundary type to facility ids, with query performance tuning.
- Added new task statuses for referral handling and no-resource tasks.
- Added an `includeImmediateChildren` flag to project search (returns only direct children instead of the full descendant subtree).
- Included the linked project-facility object in project search responses.
- Fixed missing request info in the project update Kafka message.
- Null-pointer guarding and dedupe in the boundary-service validation step.

## 1.2.2 - 2026-03-04

- Upgraded tracer to 2.9.2 for `DataAccessException` handling via tracer's `ExceptionAdvise`.
- Removed direct tracer dependency; tracer is now inherited transitively via `health-services-common` 1.1.3.
- Added OpenTelemetry BOM and Instrumentation BOM dependency management and OTEL exporter configuration.

## 1.2.1 - 2025-07-15
- Enabled Redis caching for project-create-cache-{id} after project creation.
- Optimized project ID validation in ProjectStaff and ProjectFacility using Redis (fallback to DB).
- Added logs for cache hits, misses, and Redis errors.

## 1.2.0 - 2025-05-07
* Implemented tenant-based schema handling across repository and SQL layers.
* Required tenant ID across repository methods with validation logic.
* Made cache usage tenant-aware.
* Upgraded health-services-common to 1.0.23-dev-SNAPSHOT.
* Updated migration script logic for schema support.

## 1.1.8 - 2025-06-16

- Upgraded to health models 1.0.27

## 1.1.7 - 2025-03-04

- Upgraded to health models 1.0.26

## 1.1.6 - 2025-01-27

- Added isAncestorProjectId param for search projects API to support search projects with ancestor project id as well

## 1.1.5 - 2024-08-07

- Added UserAction functionality with support for Location capture.

## 1.1.4 - 2024-05-29

- Integrated Core 2.9LTS
- Integrated Boundary v2 functionality
- Upgraded to health models 1.0.20 and health common 1.0.16
- Boundary v2 Integration
- MDMS v2 integration
- Beneficiary Tag null check in update
- Upgraded PostgresSQL Driver version to 42.7.1
- Upgraded Flyway base image version to 10.7.1 for DB Migration
- Upgraded Flyway-Core to 9.22.3
- Added `ExistentEntityValidator` fixes

## 1.1.2 - 2024-02-26

- Implemented validation for updating project start date and end date.
- Added numberOfSessions field in additional details for attendance registry.

## 1.1.1 - 2023-11-15

- Added tag in project beneficiary

## 1.1.1-beta 19-10-2023

- Added support for multi round, Added new validator for project task.

## 1.1.0

- models library version update

## 1.0.0

- Base version
