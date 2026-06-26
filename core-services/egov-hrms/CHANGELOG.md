# Changelog
All notable changes to this module will be documented in this file.

## 1.2.8-SNAPSHOT - 2026-06-17
- Upgraded to Java 25
- Upgraded Spring Boot to 3.4.4 (from 2.2.6.RELEASE)
- Migrated `javax.*` to `jakarta.*`
- Bumped Lombok to 1.18.46 and added the Lombok annotation processor path
- Upgraded eGov libraries: `tracer` 2.9.0, `services-common` 2.9.0, `mdms-client` 2.9.0
- Replaced `hibernate-validator`/`spring-beans` pins with `spring-boot-starter-validation` (managed versions)
- Removed `@SafeHtml` annotations (removed in Hibernate Validator 8)
- Retained `digit-models` (excluding its old `services-common`) for the Individual `UserDetails` contract types

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
