# Changelog

All notable changes to this module will be documented in this file.

## 1.2.4 - 2026-06-24

- Added `includeOnlyUpdatedByOthers` filter to referral search; combined with a changed-since time it excludes records last modified by the calling user (#1991).
- Reworked beneficiary downsync with background pre-generation of per-locality / per-project files to S3, serving fresh bundles and falling back to pre-generated file links for cold/stale syncs.
- Added a master switch `egov.downsync.pregen.enabled` (default `false`) to turn the gzip pre-generated downsync flow on or off; when off, downsync falls back to the original live DB-scan path.
- Made downsync jobs resumable and safe to retry (per-tenant and per-project single-flight locks, interrupted-job cleanup, multi-threaded resume).
- Disabled central instance by default for referral management.
- Upgraded health-services-models dependency.

## 1.2.3 - 2026-03-04

- Upgraded tracer to 2.9.2 for `DataAccessException` handling via tracer's `ExceptionAdvise`.
- Removed direct tracer dependency; tracer is now inherited transitively via `health-services-common` 1.1.3.
- Added OpenTelemetry BOM and Instrumentation BOM dependency management and OTEL exporter configuration.

# 1.2.2 - 2026-02-11

* Added projectId column to REFERRAL table via migration V20260211164600
* Updated Referral model to include projectId field for project association
* Upgraded health-services-models dependency to 1.0.30

# 1.2.1 - 2025-07-01

* Fixed downsync limit issue by fetching the data in batches
* Resolved central instance prefix missing issue in downsync

# 1.2.0 - 2025-05-07

* Enabled multi-schema support using dynamic schema replacement in SQL queries.
* Refactored repositories to validate and apply tenant-specific logic.
* Enhanced error handling and validators for tenant awareness.
* Modified migration scripts for central instance compatibility.

## 1.0.5 - 2025-02-11

- Downsync logic is fixed to use clientReferenceIds instead of ids.

## 1.0.4- 2024-08-09

- Service Request Services support for downsync API
- Upgraded to health models 1.0.27

## 1.0.3 - 2024-08-09

- Upgraded downsync logic.
- Added `ExistentEntityValidator` fixes

## 1.0.2 - 2024-05-29

- Upgraded to Core 2.9LTS
- Client reference ID validation added
- Upgraded to health models 1.0.20 and health common 1.0.16
- Boundary v2 Integration
- MDMS v2 integration
- Upgraded PostgresSQL Driver version to 42.7.1
- Upgraded Flyway base image version to 10.7.1 for DB Migration
- Upgraded Flyway-Core to 9.22.3

## 1.0.1 - 2024-02-28

- Added functionality for referrals handled by health facilities, referred to as "hfreferral".

## 1.0.0 - 2023-11-15

- Added Downsync Feature

## 1.0.0-beta

- Base version
- Added functionality for Side-Effects and Refferal management
