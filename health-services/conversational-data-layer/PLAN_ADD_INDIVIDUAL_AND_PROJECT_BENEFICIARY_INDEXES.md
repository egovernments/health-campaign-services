# Plan: Add Individual and Project Beneficiary Indexes to CDL

## Overview
Add 2 new Elasticsearch index schemas to `SchemaService.java` for the **Individual** and **Project Beneficiary** entities, following the existing pattern used by the 16 currently registered indexes.

## File to Modify
- `src/main/java/digit/service/SchemaService.java`

## Changes

### 1. Register new schemas in `initializeSchemas()`
Add two lines before the log statement:
```java
registerSchema(buildIndividualSchema());
registerSchema(buildProjectBeneficiarySchema());
```

### 2. Add `buildIndividualSchema()` method

**Index Name:** `oy-individual-index-v1`

**Description:** "Individual/person records with demographic details including name, date of birth, gender, contact information, identifiers, and skills. Use for individual lookups, demographic queries, contact searches, identifier lookups."

**Fields (from `Individual.java` + parent models):**

| Field Name | Type | Description                                                                      | Enum Values |
|---|---|----------------------------------------------------------------------------------|---|
| `Data.id` | keyword | Unique individual record ID                                                      | |
| `Data.individualId` | keyword | Individual identifier                                                            | |
| `Data.tenantId` | keyword | Tenant identifier                                                                | |
| `Data.clientReferenceId` | keyword | Client-side reference ID                                                         | |
| `Data.userId` | keyword | Associated user ID                                                               | |
| `Data.userUuid` | keyword | Associated user UUID                                                             | |
| `Data.name.givenName` | text | First/given name                                                                 | |
| `Data.name.familyName` | text | Family/last name                                                                 | |
| `Data.name.otherNames` | text | Other/middle names                                                               | |
| `Data.dateOfBirth` | date | Date of birth (dd/MM/yyyy)                                                       | |
| `Data.gender` | keyword | Gender of the individual                                                         | MALE, FEMALE, OTHER, TRANSGENDER |
| `Data.bloodGroup` | keyword | Blood group                                                                      | B+, B-, A+, A-, AB+, AB-, O-, O+ |
| `Data.mobileNumber` | keyword | Mobile phone number                                                              | |
| `Data.altContactNumber` | keyword | Alternate contact number                                                         | |
| `Data.email` | keyword | Email address                                                                    | |
| `Data.fatherName` | text | Father's name                                                                    | |
| `Data.husbandName` | text | Husband's name                                                                   | |
| `Data.relationship` | keyword | Relationship type                                                                | |
| `Data.photo` | keyword | Photo reference                                                                  | |
| `Data.isDeleted` | boolean | Soft delete flag                                                                 | |
| `Data.isSystemUser` | boolean | Whether individual is a system user, is thi is true then it is not a beneficiary | |
| `Data.isSystemUserActive` | boolean | Whether system user account is active                                            | |
| `Data.address` | nested | List of addresses (max 3)                                                        | |
| `Data.identifiers` | nested | Identity documents (type + ID)                                                   | |
| `Data.skills` | nested | Skills (type, level, experience)                                                 | |
| `Data.auditDetails.createdBy` | keyword | User who created the record                                                      | |
| `Data.auditDetails.createdTime` | long | Creation timestamp (epoch ms)                                                    | |
| `Data.auditDetails.lastModifiedBy` | keyword | User who last modified                                                           | |
| `Data.auditDetails.lastModifiedTime` | long | Last modification timestamp (epoch ms)                                           | |

> **NOTE:** The actual ES index field names and nesting depend on how the data is indexed (denormalized vs raw). Review the actual ES index mapping to confirm field paths. The `Data.` prefix and boundary fields should be added if this index follows the denormalized pattern with boundary hierarchy.

### 3. Add `buildProjectBeneficiarySchema()` method

**Index Name:** `oy-project-beneficiary-index-v1`

**Description:** "Registration of beneficiaries (individuals/households) to projects/campaigns. Links beneficiary entities to specific projects with registration dates. Use for beneficiary enrollment, registration counts, project-beneficiary lookups, registration date queries."

**Fields (from `ProjectBeneficiary.java` + parent models):**

| Field Name | Type | Description | Enum Values |
|---|---|---|---|
| `Data.id` | keyword | Unique record ID | |
| `Data.projectId` | keyword | Project/campaign identifier | |
| `Data.beneficiaryId` | keyword | Beneficiary entity ID | |
| `Data.clientReferenceId` | keyword | Client-side reference ID | |
| `Data.beneficiaryClientReferenceId` | keyword | Client-side beneficiary reference ID | |
| `Data.dateOfRegistration` | long | Registration date (epoch ms) | |
| `Data.tenantId` | keyword | Tenant identifier | |
| `Data.tag` | keyword | Beneficiary tag/label | |
| `Data.isDeleted` | boolean | Soft delete flag | |
| `Data.auditDetails.createdBy` | keyword | User who created the record | |
| `Data.auditDetails.createdTime` | long | Creation timestamp (epoch ms) | |
| `Data.auditDetails.lastModifiedBy` | keyword | User who last modified | |
| `Data.auditDetails.lastModifiedTime` | long | Last modification timestamp (epoch ms) | |

> **NOTE:** If this index is denormalized (like most others), it may also include boundary hierarchy fields and field worker details. Add `addBoundaryFields(fields, "Data")` and user fields (`Data.userName`, `Data.nameOfUser`, `Data.role`, etc.) if applicable.

---

## Questions to Resolve Before Implementation

1. **Index names** - Are the actual ES index names `oy-individual-index-v1` and `oy-project-beneficiary-index-v1`? Or do they follow a different naming convention?

yes index names are correct
2. **Denormalized fields** - Do these indexes include denormalized boundary hierarchy and field worker details like most other indexes? If yes, the following should be added to both:
   - `addBoundaryFields(fields, "Data")` (country, state, lga, ward, community, healthFacility)
   - `Data.userName`, `Data.nameOfUser`, `Data.role`, `Data.userAddress`
   - `Data.taskDates`, `Data.syncedDate`, `Data.syncedTimeStamp`
No they dont have any of the above fields

3. **Individual address/identifiers/skills** - Are these stored as nested objects in ES, or flattened? This affects whether we use `nested` type or individual flattened fields.

they are nested exactly as the java object

4. **Any additional computed/denormalized fields** not in the Java model (e.g., `Data.age`, `Data.geoPoint`)?

noplease implement the 
---

## Implementation Steps

1. **User reviews and updates** the index field definitions above
2. Add `buildIndividualSchema()` method to `SchemaService.java`
3. Add `buildProjectBeneficiarySchema()` method to `SchemaService.java`
4. Register both in `initializeSchemas()`
5. Build and verify compilation
