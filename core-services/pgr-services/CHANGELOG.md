# Changelog
All notable changes to this module will be documented in this file.

### 1.2.0 - 2025-05-07
* Upgraded Java version from 8 to 17 and updated pom.xml accordingly.
* Integrated MultiStateInstanceUtil for schema-based multi-tenancy across repository, producer, and query builder layers.
* Added InvalidTenantIdException handling for robust multi-tenant error validation.
* Modified workflow configurations and updated multiple model classes with minor adjustments.
* Refactored tests to align with Java 17 and multi-tenant support changes.

## 1.1.7 - 2023-02-01

- Transition from 1.1.7-beta version to 1.1.7 version

## 1.1.7-beta - 2022-11-03

- Incorporated privacy decryption for notification flow

## 1.1.6 - 2022-08-03
- Added channel based notification

## 1.1.4 - 2022-01-13
- Updated to log4j2 version 2.17.1

## 1.1.3 - 2021-07-23
- Fixed HRMS multi-tenant department validation

## 1.1.2 - 2021-05-11
- Fixed security issue of untrusted data pass as user input.

## 1.1.1 - 2021-02-26
- Updated domain name in application.properties.
- Fixed security issue for throwable statement.

## 1.1.0 - 2020-01-15
- PGR v2 API integration with PGR UI/UX revamp

## 1.0.0 - 2020-09-01
- Baseline version released
