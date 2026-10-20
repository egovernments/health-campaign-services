# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.0.0] - 2026-07-03

### Added
- Initial project setup — Spring Boot 3.2.2 / Java 17 worker registry service for attendance and payments.
- Worker registry schema (`eg_hcm_worker_registry`) and worker↔individual mapping schema (`eg_hcm_worker_individual_map`) via Flyway migrations.
- Bulk worker APIs: `POST /worker/v1/bulk/_create`, `POST /worker/v1/bulk/_update`, `POST /worker/v1/_search`.
- Worker↔individual bulk mapping API: `POST /worker/v1/individual/bulk/_create`.
- Individual-id validation against the Individual service, plus configuration to toggle it.
- PII encryption/decryption of worker payment and personal fields via enc-client.
- IDGen integration for worker id generation.
- Redis caching of worker and mapping records.
- Multi-tenancy / central-instance support (tenant-id topic prefix on the attendance listener).
- `beneficiaryCode` field on the worker record and additional validation for worker fields.
- Kafka listener for first attendance-log events to auto-capture worker signature and photo.

### Changed
- Worker create/update APIs now return readable (decrypted) worker data instead of encrypted values.

### Fixed
- Worker fields no longer silently revert to garbled/encrypted values on partial update — updates now merge incoming fields over the existing record.
- Worker registry search no longer misbehaves when no individual ids are found — the search short-circuits to an empty result.
- Attendance event processing corrected so first-signature and general attendance document events are handled reliably.
