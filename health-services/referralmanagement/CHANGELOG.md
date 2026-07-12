# Changelog

All notable changes to this module will be documented in this file.

# 1.3.0 - 2026-07-12

* Added Azure Blob Storage as a supported backend for downsync file uploads and presigned URL generation, alongside the existing AWS S3 support.
* Introduced pluggable storage-backend abstraction (`DownsyncStorageBackend`) with AWS S3 and Azure Blob implementations. Backend is selected via the `egov.downsync.storage.backend` property (`s3` | `azure`, default `s3`).
* Added `StorageBackendValidator` — a `@PostConstruct` startup check that fails the pod boot if credentials for the selected backend are missing or blank (no silent fallback, no partially-working pods).
* Preserved backward compatibility: `DownsyncS3Service` remains as a thin facade over the new backend interface — existing callers (`DownsyncFileGenService`, `DownsyncPregenService`) are unchanged, and S3-only deployments run unaffected with default configuration.
* Added `com.azure:azure-storage-blob:12.24.0` dependency. Runtime paths are gated by `@ConditionalOnProperty`, so no Azure code executes for S3-only deployments.
* New required Spring properties for `backend=azure`: `azure.blob.account.name`, `azure.blob.account.key`, `azure.blob.container.name`. Optional: `azure.blob.endpoint` (defaults to `https://<account>.blob.core.windows.net`).
* Added JUnit coverage for backend startup validation, facade delegation, and end-to-end integration tests against MinIO (S3-compatible) and Azurite (Azure Blob emulator), gated by `-Dminio.integration=true` / `-Dazurite.integration=true` respectively.

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
