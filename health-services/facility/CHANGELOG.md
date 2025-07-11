All notable changes to this module will be documented in this file.

# 1.2.0 - 2025-05-07
* Added tenant-aware schema resolution using MultiStateInstanceUtil and schema placeholders in queries.
* Updated repository methods to require tenant ID and handle InvalidTenantIdException.
* Enhanced validators for multi-tenant support.
* Added clientReferenceId to ADDRESS table; updated SQL and persister configurations.
* Removed tracer dependency.
* Updated migrate.sh and added SQL for clientReferenceId.

## 1.1.2 - 2024-05-29
- Integrated Core 2.9LTS
- Upgraded to health models 1.0.20 and health common 1.0.16
- Integrated Boundary v2 functionality
- Upgraded PostgresSQL Driver version to 42.7.1
- Upgraded Flyway base image version to 10.7.1 for DB Migration
- Upgraded Flyway-Core to 9.22.3

## 1.0.0

- Base version

## 1.1.0
