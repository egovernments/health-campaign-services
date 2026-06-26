# Changelog
All notable changes to this module will be documented in this file.

## 1.1.8 - 2026-06-17
- Upgraded to Java 25
- Upgraded Spring Boot to 3.4.4 (from 2.2.6.RELEASE)
- Migrated `javax.*` to `jakarta.*`
- Bumped Lombok to 1.18.46 and added the Lombok annotation processor path
- Removed redundant `@Autowired` on `@Bean` methods
- Upgraded eGov libraries: `tracer` 2.9.0, `services-common` 2.9.0, `mdms-client` 2.9.0
- Replaced `javax.validation:validation-api` with `spring-boot-starter-validation`
- Added `commons-io:2.16.1` (no longer transitive)
- Removed `@SafeHtml` annotations (removed in Hibernate Validator 8)
- Removed stale dependency pins (`spring-beans` 5.2.20, `hibernate-validator` 6.0.16, `logback` 1.2.0)

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
