All notable changes to this module will be documented in this file.

## 1.0.2 - 2026-06-17
- Upgraded to Java 25
- Upgraded Spring Boot to 3.4.4 (from 2.2.6.RELEASE)
- Migrated `javax.*` to `jakarta.*`
- Bumped Lombok to 1.18.46 and added the Lombok annotation processor path
- Removed redundant `@Autowired` on `@Bean` methods
- Upgraded eGov libraries: `tracer` 2.9.0, `mdms-client` 2.9.0; dropped `digit-models` (migrated `AuditDetails` to `org.egov.common.contract.models`)
- Replaced `javax.validation:validation-api` with `spring-boot-starter-validation`
- Added `commons-lang:2.6` (no longer transitive)
- Added `flyway-database-postgresql` (Flyway 10+ moved PostgreSQL support to a separate module)

## 1.0.1 - 2024-08-29 

- Added `BOOLEAN` DataType in `AttributeDefinition`

## 1.0.0

- Base version