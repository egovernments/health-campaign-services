# CHANGELOG
All notable changes to this module will be documented in this file.

## 1.2.0 - 2025-03-15

- Upgraded Java version from 8 to 17 and updated pom.xml accordingly.
- Integrated MultiStateInstanceUtil for schema-based multi-tenancy across repository, producer, and query builder layers.
- Added InvalidTenantIdException handling for robust multi-tenant error validation.
- Refactored tests to align with Java 17 and multi-tenant support changes.
- Updated migration script logic for schema support.
- Updated the persister file to handle multiple schemas.

## 1.0.0

- Base version

## 1.1.0