# Change Logs

All notable changes to this module will be documented in this file.

## Unreleased - 2026-09-03

Backward-compatibility change set. Every new gate defaults to the OLD behaviour. Two deliberate exceptions, both in the *more permissive* direction, so neither can reject a payload the old service accepted: `HOUSEHOLD_ALREADY_HAS_HEAD` stays ungated but runs master's reassignment algorithm rather than the baseline's blunter per-member check, and the post-baseline `HmRelativeExistentValidator` also stays ungated (its `INVALID_RELATED_ENTITY_ID` path only fires when `memberRelationships` is populated, a field old clients do not send).

- Added `household.member.head.strict.validation` (default `false`). Gates the three head-of-household rules added after the old baseline (`HOUSEHOLD_DOES_NOT_HAVE_A_HEAD`, `HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD`, `HOUSEHOLD_HEAD_CANNOT_BE_UNASSIGNED`). The pre-existing `HOUSEHOLD_ALREADY_HAS_HEAD` reassignment protection is **not** gated and still always runs.
- Added `household.member.relationship.type.validation` (default `false`). Gates `HmRelationshipTypeValidator` at the predicate, which also removes the unconditional MDMS round-trip — previously a missing `HCM.HOUSEHOLD_MEMBER_RELATIONSHIP_TYPES` master threw out of `validate()` and killed the entire batch rather than one record.
- Added `household.member.required.link.validation` (default `false`). Gates `HmRequiredLinkValidator`, which was previously unconditional. **Corrects the 1.2.3 entry below, which described that validator as always on — it no longer is.**
- A null `householdType` is now defaulted to `FAMILY` in `HouseholdEnrichmentService.create()`. **Upstreamed from `customize-2.1-household` `5ef888f7c1`** ("added default household type as family while household creation"), matching that branch's shape so master behaves identically. It matters on create: `household.householdtype` is `NOT NULL` and `household-persister.yml` names the column in its INSERT, so a null previously failed `23502` inside the persister behind an already-returned `202`, losing the record silently. On update the persister's SET clause omits the column, so it cannot change a stored type.
- Fixed a mixed-batch `NullPointerException` in `HmHouseholdHeadValidator`: members are now grouped per member by whichever parent key each one carries. Previously a single accessor was chosen for the whole batch from an arbitrary element, so a batch mixing `householdId` and `householdClientReferenceId` produced a null grouping key and discarded the entire batch.

## 1.2.3 - 2026-07-20

- Added `household.member.relationship.validation` flag (default `false`) that unbundles cross-entity **existence** validation (household / individual / relative) for member create/update, so a member is accepted while its parent is still on the persister queue (offline-first). Set `true` to enforce.
- Added `HmRequiredLinkValidator` (always on, error `REQUIRED_LINK_MISSING`, non-recoverable) enforcing presence of the member's own `clientReferenceId`, a household link, and an individual link.
- A within-batch duplicate `clientReferenceId` in a bulk create no longer drops the whole batch: first occurrence is kept, each later duplicate is isolated as a per-record uniqueness error, and valid records still persist (household and member; replaced `Collectors.toMap` with a first-wins loop in `HExistentEntityValidator` / `HmExistentEntityValidator`).
- Added enrichment NPE guards in `HouseholdMemberEnrichmentService` — an empty or partially unresolved household list leaves `householdId` unset and logs a warning instead of throwing `No value present`/NPE.
- Upgraded `health-services-common` to 1.1.6-SNAPSHOT and `health-services-models` to 1.0.35-SNAPSHOT. Service artifact version unchanged at 1.2.2.

## 1.2.2 - 2026-03-10

- Upgraded tracer to 2.9.2 for `DataAccessException` handling via tracer's `ExceptionAdvise`.
- Removed direct tracer dependency; tracer is now inherited transitively via `health-services-common` 1.1.3.
- Added OpenTelemetry BOM and Instrumentation BOM dependency management and OTEL exporter configuration.

## 1.2.1 - 2025-08-11

- Enhanced household head validation to handle multiple head detection, head assignment/unassignment scenarios, and improved error handling

## 1.2.0 - 2025-04-15

- Enabled multi-schema support using dynamic schema replacement in SQL queries.
- Refactored repositories to validate and apply tenant-specific logic.
- Enhanced error handling and validators for tenant awareness.
- Modified migration scripts for central instance compatibility.
- Updated migration script logic for schema support.
- Updated the persister file to handle multiple schemas.

## 1.1.7 - 2025-04-11

- Upgraded health-services-models to 1.0.27
- Added relationship type support for household members

## 1.1.6 - 2025-02-28

- Upgraded to health commons library version 1.0.21

## 1.1.5 - 2025-01-28

- Added householdType column in household table
- Upgraded to heath models 1.0.25

## 1.1.4 - 2024-08-29 

- Added `ExistentEntityValidator` fixes

## 1.1.3 - 2024-05-29 

- Integrated Core 2.9 LTS
- Client reference ID validation added
- Upgraded to health models 1.0.20 and health common 1.0.16
- Boundary v2 Integration
- MDMS v2 integration
- Upgraded PostgresSQL Driver version to 42.7.1
- Upgraded Flyway base image version to 10.7.1 for DB Migration
- Upgraded Flyway-Core to 9.22.3

## 1.1.2 - 2024-05-10

- Integrated Boundary v2 functionality

## 1.1.1 - 2023-11-15

- Added total count for household
- Added a field for HouseholdMember-clientReferenceId

## 1.1.1-beta

- Added proximity based search support

## 1.1.0


## 1.0.0

- Base version

