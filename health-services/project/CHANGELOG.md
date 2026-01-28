# Change Logs

All notable changes to this module will be documented in this file.

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
