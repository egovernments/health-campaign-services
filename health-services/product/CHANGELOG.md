# CHANGELOG
All notable changes to this module will be documented in this file.

## 1.2.1 - 2026-03-04

- Upgraded tracer to 2.9.2 for `DataAccessException` handling via tracer's `ExceptionAdvise`.
- Removed direct tracer dependency; tracer is now inherited transitively via `health-services-common` 1.1.3.
- Added OpenTelemetry BOM and Instrumentation BOM dependency management and OTEL exporter configuration.

## 1.2.0 - 2025-03-15

- Upgraded spring boot version to 3.2.2 and Java from 8 to 17
- Integrated MultiStateInstanceUtil for schema-based multi-tenancy across repository, producer, and query builder layers.
- Refactored tests to align with Java 17, Spring boot 3.2.2 and multi-tenant support changes.
- Updated migration script logic for schema support.

## 1.0.0

- Base version

## 1.1.0