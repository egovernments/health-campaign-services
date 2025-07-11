# Changelog
All notable changes to this module will be documented in this file.

## 1.4.0 - 2025-03-15

- Upgraded Java version from 8 to 17 and updated pom.xml accordingly.
- Integrated MultiStateInstanceUtil for schema-based multi-tenancy across repository, producer, and query builder layers.
- Added InvalidTenantIdException handling for robust multi-tenant error validation.
- Refactored tests to align with Java 17 and multi-tenant support changes.
- Updated migration script logic for schema support.
- Updated multiple model classes with minor adjustments.
- Updated the persister file to handle multiple schemas.

## 1.3.1 - 2025-06-11
- Added the total Count logic in search based on uuids
- Fixed the PaginationClause

## 1.2.7 - 2024-05-29
- Integrated Boundary v2 functionality
- Individual model copied and replicated to the 2.9 version
- Upgraded Flyway base image version to 10.7.1 for DB Migration

## 1.2.6 - 2024-03-06
- Added client Referenceid to Individual to avoid errors during down sync in APK

## 1.2.5 - 2023-02-02

- Transition from 1.2.5-beta version to 1.2.5 version

## 1.2.5-beta - 2022-03-02
- Added security fix for restricting employee search from citizen role

## 1.2.4 - 2022-01-13
- Updated to log4j2 version 2.17.1

## 1.2.3 - 2021-07-26
 - Fixed RAIN-3056: Able to re-activate employee by selecting the previous date

## 1.2.2 - 2021-05-11
 - VUL-WEB-L008
 - Added @SafeHtml annotaion on string fields
 - Updated POM to add safeHtml validator libraries

## 1.2.1 - 2021-02-26
- Updated domain name in application.properties

## 1.2.0 - 2021-01-12
- Added employee reactivation feature

## 1.1.0 - 2020-05-27

- Upgraded to `tracer:2.0.0-SNAPSHOT`
- Upgraded to `Spring boot 2.2.6`
- Renamed `ReferenceType` enum to `EmployeeDocumentReferenceType`
- Added typescript interface generation plugin

## 1.0.0

- Base version
