# CDL Index Catalog

This file describes all Elasticsearch indexes available for the Conversational Data Layer.
The LLM uses the INDEX NAME and DESCRIPTION to auto-select the right index for a user query.
Modify descriptions, field lists, and enum values to match your actual ES mappings.

---

## 1. Project Task Index (Denormalized)

```
INDEX NAME: oy-project-task-index-v1
DESCRIPTION: Denormalized index combining task delivery data with individual/beneficiary details, product info, and boundary hierarchy. Use this for questions about deliveries, administration status, beneficiary demographics, task counts by location, and campaign delivery performance.

FIELDS:
- Data.@timestamp | date | Record timestamp
- Data.id | keyword | Unique record ID
- Data.taskId | keyword | Task identifier
- Data.taskClientReferenceId | keyword | Client-side task reference ID
- Data.clientReferenceId | keyword | Client reference ID
- Data.individualId | keyword | Individual/beneficiary identifier
- Data.projectId | keyword | Project/campaign identifier
- Data.projectType | keyword | Type of project
- Data.projectTypeId | keyword | Project type identifier
- Data.tenantId | keyword | Tenant identifier

# Individual/Beneficiary fields
- Data.nameOfUser | text | Name of the field worker
- Data.userName | text | Username of the field worker
- Data.role | keyword | Role of the user (e.g., field worker, supervisor)
- Data.gender | keyword | Gender of the beneficiary
- Data.age | long | Age of the beneficiary in months
- Data.dateOfBirth | long | Date of birth (epoch ms)

# Delivery/Status fields
- Data.status | keyword | Task delivery status | ADMINISTRATION_FAILED, ADMINISTRATION_SUCCESS, BENEFICIARY_REFUSED, CLOSED_HOUSEHOLD, DELIVERED, NOT_ADMINISTERED, INELIGIBLE
- Data.administrationStatus | keyword | Administration status
- Data.taskType | keyword | Type of task
- Data.isDelivered | boolean | Whether delivery was completed
- Data.deliveredTo | text | Who the delivery was made to
- Data.deliveryComments | text | Comments on the delivery

# Product fields
- Data.productName | keyword | Name of the product delivered
- Data.productVariant | keyword | Variant of the product
- Data.quantity | long | Number of units delivered

# Audit fields
- Data.createdBy | keyword | User who created the record
- Data.createdTime | long | Creation timestamp (epoch ms)
- Data.lastModifiedBy | keyword | User who last modified the record
- Data.lastModifiedTime | long | Last modification timestamp (epoch ms)
- Data.syncedDate | date | Date the record was synced
- Data.syncedTime | long | Sync timestamp (epoch ms)
- Data.syncedTimeStamp | date | Sync timestamp (date format)
- Data.taskDates | date | Date of the task

# Geo fields
- Data.latitude | float | GPS latitude
- Data.longitude | float | GPS longitude
- Data.geoPoint | geo_point | Geo coordinates for geo queries
- Data.locationAccuracy | float | GPS accuracy in meters
- Data.previousGeoPoint | float | Previous geo coordinates
- Data.localityCode | keyword | Locality boundary code

# Boundary hierarchy (names)
- Data.boundaryHierarchy.country | keyword | Country name
- Data.boundaryHierarchy.state | keyword | State name
- Data.boundaryHierarchy.lga | keyword | LGA name
- Data.boundaryHierarchy.ward | keyword | Ward name
- Data.boundaryHierarchy.community | keyword | Community name
- Data.boundaryHierarchy.healthFacility | keyword | Health facility name

# Boundary hierarchy (codes)
- Data.boundaryHierarchyCode.country | keyword | Country code
- Data.boundaryHierarchyCode.state | keyword | State code
- Data.boundaryHierarchyCode.lga | keyword | LGA code
- Data.boundaryHierarchyCode.ward | keyword | Ward code
- Data.boundaryHierarchyCode.community | keyword | Community code
- Data.boundaryHierarchyCode.healthFacility | keyword | Health facility code

# Additional details
- Data.additionalDetails.name | text | Beneficiary name (from additional details)
- Data.additionalDetails.age | keyword | Beneficiary age (from additional details)
- Data.additionalDetails.gender | keyword | Beneficiary gender (from additional details)
- Data.additionalDetails.uniqueBeneficiaryId | keyword | Unique beneficiary ID
- Data.additionalDetails.individualClientReferenceId | keyword | Individual client reference ID
- Data.additionalDetails.deliveryStrategy | keyword | Delivery strategy used
- Data.additionalDetails.deliveryType | keyword | Type of delivery
- Data.additionalDetails.doseIndex | keyword | Dose number/index
- Data.additionalDetails.cycleIndex | keyword | Campaign cycle index
- Data.additionalDetails.taskStatus | keyword | Task status from additional details
- Data.additionalDetails.reAdministered | keyword | Whether re-administered
- Data.additionalDetails.ineligibleReasons | keyword | Reasons for ineligibility
- Data.additionalDetails.latitude | keyword | Latitude from additional details
- Data.additionalDetails.longitude | keyword | Longitude from additional details
- Data.additionalDetails.dateOfAdministration | keyword | Date product was administered
- Data.additionalDetails.dateOfDelivery | keyword | Date of delivery
- Data.additionalDetails.dateOfVerification | keyword | Date of verification
```

---

## 2. Project Index

```
INDEX NAME: oy-project-index-v1
DESCRIPTION: Stores project/campaign configuration data including targets, duration, product variants, and geographic assignment. Use this for questions about campaign setup, targets, project duration, and which products are assigned to which locations.

FIELDS:
- Data.id | keyword | Unique record ID
- Data.projectId | keyword | Project/campaign identifier
- Data.projectBeneficiaryType | keyword | Type of beneficiary (e.g., HOUSEHOLD, INDIVIDUAL)
- Data.overallTarget | long | Total target count for the project
- Data.targetPerDay | long | Daily target count
- Data.campaignDurationInDays | long | Duration of campaign in days
- Data.startDate | long | Campaign start date (epoch ms)
- Data.endDate | long | Campaign end date (epoch ms)
- Data.productVariant | keyword | Product variant assigned to this project target
- Data.productName | keyword | Name of the product
- Data.targetType | keyword | Type of target (e.g., household, individual)
- Data.tenantId | keyword | Tenant identifier
- Data.projectType | keyword | Type of project
- Data.projectTypeId | keyword | Project type identifier
- Data.subProjectType | keyword | Sub-project type
- Data.localityCode | keyword | Locality boundary code
- Data.taskDates | date | List of task dates

# Boundary hierarchy (names)
- Data.boundaryHierarchy.country | keyword | Country name
- Data.boundaryHierarchy.state | keyword | State name
- Data.boundaryHierarchy.lga | keyword | LGA name
- Data.boundaryHierarchy.ward | keyword | Ward name
- Data.boundaryHierarchy.community | keyword | Community name
- Data.boundaryHierarchy.healthFacility | keyword | Health facility name

# Boundary hierarchy (codes)
- Data.boundaryHierarchyCode.country | keyword | Country code
- Data.boundaryHierarchyCode.state | keyword | State code
- Data.boundaryHierarchyCode.lga | keyword | LGA code
- Data.boundaryHierarchyCode.ward | keyword | Ward code
- Data.boundaryHierarchyCode.community | keyword | Community code
- Data.boundaryHierarchyCode.healthFacility | keyword | Health facility code

# Audit fields
- Data.createdBy | keyword | User who created the record
- Data.createdTime | long | Creation timestamp (epoch ms)
```

---

## 3. Project Staff Index

```
INDEX NAME: oy-project-staff-index-v1
DESCRIPTION: Tracks staff assignments to projects and their geographic posting. Use this for questions about which staff are assigned to which projects, staff distribution across locations, staff roles, and staff onboarding timelines.

FIELDS:
- Data.id | keyword | Unique record ID
- Data.userId | keyword | Staff user identifier
- Data.projectId | keyword | Project/campaign the staff is assigned to
- Data.userName | text | Username of the staff member
- Data.nameOfUser | text | Full name of the staff member
- Data.role | keyword | Role of the staff member (e.g., field worker, supervisor)
- Data.userAddress | text | Address of the staff member
- Data.tenantId | keyword | Tenant identifier
- Data.projectType | keyword | Type of project
- Data.projectTypeId | keyword | Project type identifier
- Data.localityCode | keyword | Locality boundary code
- Data.taskDates | date | List of task dates
- Data.isDeleted | boolean | Soft delete flag

# Boundary hierarchy (names)
- Data.boundaryHierarchy.country | keyword | Country name
- Data.boundaryHierarchy.state | keyword | State name
- Data.boundaryHierarchy.lga | keyword | LGA name
- Data.boundaryHierarchy.ward | keyword | Ward name
- Data.boundaryHierarchy.community | keyword | Community name
- Data.boundaryHierarchy.healthFacility | keyword | Health facility name

# Boundary hierarchy (codes)
- Data.boundaryHierarchyCode.country | keyword | Country code
- Data.boundaryHierarchyCode.state | keyword | State code
- Data.boundaryHierarchyCode.lga | keyword | LGA code
- Data.boundaryHierarchyCode.ward | keyword | Ward code
- Data.boundaryHierarchyCode.community | keyword | Community code
- Data.boundaryHierarchyCode.healthFacility | keyword | Health facility code

# Audit fields
- Data.createdBy | keyword | User who created the record
- Data.createdTime | long | Creation timestamp (epoch ms)
```

---

## 4. Service Index (Checklists/Supervision)

```
INDEX NAME: oy-service-task-v1
DESCRIPTION: Stores checklist/supervision service records filled by supervisors during field visits. Use this for questions about supervision visits, checklist completion, supervisor activity, and service definition responses.

FIELDS:
- Data.id | keyword | Unique record ID
- Data.clientReferenceId | keyword | Client-side reference ID
- Data.createdTime | long | Creation timestamp (epoch ms)
- Data.createdBy | keyword | User who created the record
- Data.supervisorLevel | keyword | Level of the supervisor
- Data.checklistName | keyword | Name of the checklist filled
- Data.projectId | keyword | Project/campaign identifier
- Data.serviceDefinitionId | keyword | Service definition identifier
- Data.tenantId | keyword | Tenant identifier
- Data.userId | keyword | User who filled the checklist

# Staff fields
- Data.userName | text | Username of the supervisor
- Data.nameOfUser | text | Full name of the supervisor
- Data.role | keyword | Role of the user
- Data.userAddress | text | Address of the supervisor

# Boundary hierarchy (names)
- Data.boundaryHierarchy.country | keyword | Country name
- Data.boundaryHierarchy.state | keyword | State name
- Data.boundaryHierarchy.lga | keyword | LGA name
- Data.boundaryHierarchy.ward | keyword | Ward name
- Data.boundaryHierarchy.community | keyword | Community name
- Data.boundaryHierarchy.healthFacility | keyword | Health facility name

# Boundary hierarchy (codes)
- Data.boundaryHierarchyCode.country | keyword | Country code
- Data.boundaryHierarchyCode.state | keyword | State code
- Data.boundaryHierarchyCode.lga | keyword | LGA code
- Data.boundaryHierarchyCode.ward | keyword | Ward code
- Data.boundaryHierarchyCode.community | keyword | Community code
- Data.boundaryHierarchyCode.healthFacility | keyword | Health facility code

# Checklist data
- Data.attributes | nested | Checklist attribute responses (attributeCode, value, referenceId)
- Data.transformedChecklist | object | Flattened checklist key-value responses

# Sync fields
- Data.syncedTimeStamp | date | Sync timestamp (date format)
- Data.syncedTime | long | Sync timestamp (epoch ms)
- Data.taskDates | date | Date of the task
- Data.geoPoint | geo_point | Geo coordinates for geo queries
```

---

## 5. Stock Index

```
INDEX NAME: oy-stock-index-v1
DESCRIPTION: Tracks stock transactions (receipts and dispatches) for health campaign products at facilities and warehouses. Use this for questions about stock levels, stock received/dispatched, stock losses, damaged stock, facility-level inventory, and waybill tracking.

FIELDS:
- Data.id | keyword | Unique record ID
- Data.clientReferenceId | keyword | Client-side reference ID
- Data.tenantId | keyword | Tenant identifier

# Facility fields
- Data.facilityId | keyword | Facility identifier
- Data.facilityName | text | Name of the facility
- Data.facilityType | keyword | Type of facility
- Data.facilityLevel | keyword | Level of the facility in hierarchy
- Data.facilityTarget | long | Target stock for the facility
- Data.transactingFacilityId | keyword | Counterpart facility in the transaction
- Data.transactingFacilityName | text | Name of the counterpart facility
- Data.transactingFacilityType | keyword | Type of the counterpart facility
- Data.transactingFacilityLevel | keyword | Level of the counterpart facility

# Product/Transaction fields
- Data.productVariant | keyword | Product variant involved
- Data.productName | keyword | Name of the product
- Data.physicalCount | long | Physical count of stock
- Data.eventType | keyword | Type of stock transaction | RECEIVED, DISPATCHED
- Data.reason | keyword | Reason for the transaction | RECEIVED, RETURNED, LOST_IN_STORAGE, LOST_IN_TRANSIT, DAMAGED_IN_STORAGE, DAMAGED_IN_TRANSIT
- Data.dateOfEntry | long | Date of stock entry (epoch ms)
- Data.waybillNumber | keyword | Waybill/shipment tracking number

# Staff fields
- Data.userName | text | Username of the staff member
- Data.nameOfUser | text | Full name of the staff member
- Data.role | keyword | Role of the user
- Data.userAddress | text | Address of the staff member

# Boundary hierarchy (names)
- Data.boundaryHierarchy.country | keyword | Country name
- Data.boundaryHierarchy.state | keyword | State name
- Data.boundaryHierarchy.lga | keyword | LGA name
- Data.boundaryHierarchy.ward | keyword | Ward name
- Data.boundaryHierarchy.community | keyword | Community name
- Data.boundaryHierarchy.healthFacility | keyword | Health facility name

# Boundary hierarchy (codes)
- Data.boundaryHierarchyCode.country | keyword | Country code
- Data.boundaryHierarchyCode.state | keyword | State code
- Data.boundaryHierarchyCode.lga | keyword | LGA code
- Data.boundaryHierarchyCode.ward | keyword | Ward code
- Data.boundaryHierarchyCode.community | keyword | Community code
- Data.boundaryHierarchyCode.healthFacility | keyword | Health facility code

# Sync/Audit fields
- Data.syncedTimeStamp | date | Sync timestamp (date format)
- Data.syncedTime | long | Sync timestamp (epoch ms)
- Data.syncedDate | date | Date the record was synced
- Data.taskDates | date | Date of the task
- Data.createdBy | keyword | User who created the record
- Data.lastModifiedBy | keyword | User who last modified the record
- Data.createdTime | long | Creation timestamp (epoch ms)
- Data.lastModifiedTime | long | Last modification timestamp (epoch ms)
```

---

## 6. Household Index

```
INDEX NAME: oy-household-index-v1
DESCRIPTION: Stores registered household data with member count, location, and registration details. Use this for questions about household registrations, household counts by location, and household-level demographics.

FIELDS:
# Nested from Household object
- Data.household.id | keyword | Unique household ID
- Data.household.clientReferenceId | keyword | Client-side reference ID
- Data.household.tenantId | keyword | Tenant identifier
- Data.household.memberCount | long | Number of members in the household
- Data.household.address | object | Household address details
- Data.household.isDeleted | boolean | Soft delete flag
- Data.household.auditDetails.createdBy | keyword | User who created the record
- Data.household.auditDetails.createdTime | long | Creation timestamp (epoch ms)
- Data.household.auditDetails.lastModifiedBy | keyword | User who last modified
- Data.household.auditDetails.lastModifiedTime | long | Last modification timestamp (epoch ms)

# Staff fields
- Data.userName | text | Username of the field worker
- Data.nameOfUser | text | Full name of the field worker
- Data.role | keyword | Role of the user
- Data.userAddress | text | Address of the field worker

# Boundary hierarchy (names)
- Data.boundaryHierarchy.country | keyword | Country name
- Data.boundaryHierarchy.state | keyword | State name
- Data.boundaryHierarchy.lga | keyword | LGA name
- Data.boundaryHierarchy.ward | keyword | Ward name
- Data.boundaryHierarchy.community | keyword | Community name
- Data.boundaryHierarchy.healthFacility | keyword | Health facility name

# Boundary hierarchy (codes)
- Data.boundaryHierarchyCode.country | keyword | Country code
- Data.boundaryHierarchyCode.state | keyword | State code
- Data.boundaryHierarchyCode.lga | keyword | LGA code
- Data.boundaryHierarchyCode.ward | keyword | Ward code
- Data.boundaryHierarchyCode.community | keyword | Community code
- Data.boundaryHierarchyCode.healthFacility | keyword | Health facility code

# Sync fields
- Data.taskDates | date | Date of the task
- Data.syncedDate | date | Date the record was synced
- Data.syncedTimeStamp | date | Sync timestamp (date format)
- Data.geoPoint | geo_point | Geo coordinates for geo queries
```

---

## 7. Household Member Index

```
INDEX NAME: oy-household-member-index-v1
DESCRIPTION: Links individuals to households with demographic details (age, gender, DOB). Use this for questions about household composition, member demographics, head-of-household status, and member-level data by location.

FIELDS:
# Nested from HouseholdMember object
- Data.householdMember.id | keyword | Unique record ID
- Data.householdMember.householdId | keyword | Household identifier
- Data.householdMember.householdClientReferenceId | keyword | Client-side household reference
- Data.householdMember.individualId | keyword | Individual identifier
- Data.householdMember.individualClientReferenceId | keyword | Client-side individual reference
- Data.householdMember.isHeadOfHousehold | boolean | Whether this member is head of household
- Data.householdMember.tenantId | keyword | Tenant identifier
- Data.householdMember.isDeleted | boolean | Soft delete flag

# Individual demographics
- Data.dateOfBirth | long | Date of birth (epoch ms)
- Data.age | long | Age of the member in months
- Data.gender | keyword | Gender of the member

# Staff fields
- Data.userName | text | Username of the field worker
- Data.nameOfUser | text | Full name of the field worker
- Data.role | keyword | Role of the user
- Data.userAddress | text | Address of the field worker

# Boundary hierarchy (names)
- Data.boundaryHierarchy.country | keyword | Country name
- Data.boundaryHierarchy.state | keyword | State name
- Data.boundaryHierarchy.lga | keyword | LGA name
- Data.boundaryHierarchy.ward | keyword | Ward name
- Data.boundaryHierarchy.community | keyword | Community name
- Data.boundaryHierarchy.healthFacility | keyword | Health facility name

# Boundary hierarchy (codes)
- Data.boundaryHierarchyCode.country | keyword | Country code
- Data.boundaryHierarchyCode.state | keyword | State code
- Data.boundaryHierarchyCode.lga | keyword | LGA code
- Data.boundaryHierarchyCode.ward | keyword | Ward code
- Data.boundaryHierarchyCode.community | keyword | Community code
- Data.boundaryHierarchyCode.healthFacility | keyword | Health facility code

# Sync fields
- Data.localityCode | keyword | Locality boundary code
- Data.taskDates | date | Date of the task
- Data.syncedDate | date | Date the record was synced
- Data.syncedTimeStamp | date | Sync timestamp (date format)
- Data.geoPoint | geo_point | Geo coordinates for geo queries
```

---

## 8. Referral Index

```
INDEX NAME: oy-referral-index-v1
DESCRIPTION: Tracks community-level referrals of beneficiaries between health workers and facilities. Use this for questions about referral counts, referral reasons, referred beneficiary demographics, and referral patterns by location.

FIELDS:
# Nested from Referral object
- Data.referral.id | keyword | Unique referral ID
- Data.referral.clientReferenceId | keyword | Client-side reference ID
- Data.referral.projectBeneficiaryId | keyword | Project beneficiary ID
- Data.referral.projectBeneficiaryClientReferenceId | keyword | Client-side project beneficiary reference
- Data.referral.referrerId | keyword | ID of the referring health worker
- Data.referral.recipientType | keyword | Type of recipient (facility, worker, etc.)
- Data.referral.recipientId | keyword | ID of the recipient
- Data.referral.reasons | keyword | Reasons for referral (list)
- Data.referral.tenantId | keyword | Tenant identifier
- Data.referral.isDeleted | boolean | Soft delete flag
- Data.referral.auditDetails.createdBy | keyword | User who created the record
- Data.referral.auditDetails.createdTime | long | Creation timestamp (epoch ms)

# Beneficiary demographics
- Data.tenantId | keyword | Tenant identifier
- Data.individualId | keyword | Individual identifier
- Data.dateOfBirth | long | Date of birth (epoch ms)
- Data.age | long | Age in months
- Data.gender | keyword | Gender of the beneficiary
- Data.facilityName | text | Name of the referring/receiving facility

# Staff fields
- Data.userName | text | Username of the field worker
- Data.nameOfUser | text | Full name of the field worker
- Data.role | keyword | Role of the user
- Data.userAddress | text | Address of the field worker

# Boundary hierarchy (names)
- Data.boundaryHierarchy.country | keyword | Country name
- Data.boundaryHierarchy.state | keyword | State name
- Data.boundaryHierarchy.lga | keyword | LGA name
- Data.boundaryHierarchy.ward | keyword | Ward name
- Data.boundaryHierarchy.community | keyword | Community name
- Data.boundaryHierarchy.healthFacility | keyword | Health facility name

# Boundary hierarchy (codes)
- Data.boundaryHierarchyCode.country | keyword | Country code
- Data.boundaryHierarchyCode.state | keyword | State code
- Data.boundaryHierarchyCode.lga | keyword | LGA code
- Data.boundaryHierarchyCode.ward | keyword | Ward code
- Data.boundaryHierarchyCode.community | keyword | Community code
- Data.boundaryHierarchyCode.healthFacility | keyword | Health facility code

# Sync fields
- Data.taskDates | date | Date of the task
- Data.syncedDate | date | Date the record was synced
```

---

## 9. HF Referral Index (Health Facility Referral)

```
INDEX NAME: oy-hf-referral-index-v1
DESCRIPTION: Tracks health facility-level referrals including symptom surveys, referral codes, and national-level IDs. Use this for questions about facility referrals, symptom-based referrals, and referral code lookups.

FIELDS:
# Nested from HFReferral object
- Data.hfReferral.id | keyword | Unique HF referral ID
- Data.hfReferral.clientReferenceId | keyword | Client-side reference ID
- Data.hfReferral.tenantId | keyword | Tenant identifier
- Data.hfReferral.projectId | keyword | Project/campaign identifier
- Data.hfReferral.projectFacilityId | keyword | Project facility identifier
- Data.hfReferral.symptom | keyword | Reported symptom
- Data.hfReferral.symptomSurveyId | keyword | Symptom survey identifier
- Data.hfReferral.beneficiaryId | keyword | Beneficiary identifier
- Data.hfReferral.referralCode | keyword | Referral code
- Data.hfReferral.nationalLevelId | keyword | National-level identifier
- Data.hfReferral.isDeleted | boolean | Soft delete flag
- Data.hfReferral.auditDetails.createdBy | keyword | User who created the record
- Data.hfReferral.auditDetails.createdTime | long | Creation timestamp (epoch ms)

# Staff fields
- Data.userName | text | Username of the health worker
- Data.role | keyword | Role of the user
- Data.userAddress | text | Address of the health worker

# Boundary hierarchy (names)
- Data.boundaryHierarchy.country | keyword | Country name
- Data.boundaryHierarchy.state | keyword | State name
- Data.boundaryHierarchy.lga | keyword | LGA name
- Data.boundaryHierarchy.ward | keyword | Ward name
- Data.boundaryHierarchy.community | keyword | Community name
- Data.boundaryHierarchy.healthFacility | keyword | Health facility name

# Boundary hierarchy (codes)
- Data.boundaryHierarchyCode.country | keyword | Country code
- Data.boundaryHierarchyCode.state | keyword | State code
- Data.boundaryHierarchyCode.lga | keyword | LGA code
- Data.boundaryHierarchyCode.ward | keyword | Ward code
- Data.boundaryHierarchyCode.community | keyword | Community code
- Data.boundaryHierarchyCode.healthFacility | keyword | Health facility code

# Sync fields
- Data.taskDates | date | Date of the task
- Data.syncedDate | date | Date the record was synced
```

---

## 10. HF Referral Fever Index

```
INDEX NAME: oy-hf-referral-fever-index
DESCRIPTION: Stores health facility referral service/checklist data with malaria testing results when referred for fever. Use this for questions about malaria testing at facilities, positive/negative results, anti-malarial treatment, and serious illness admissions.

FIELDS:
- Data.id | keyword | Unique record ID
- Data.supervisorLevel | keyword | Level of the supervisor
- Data.checklistName | keyword | Name of the checklist
- Data.createdTime | long | Creation timestamp (epoch ms)
- Data.createdBy | keyword | User who created the record
- Data.projectId | keyword | Project/campaign identifier
- Data.tenantId | keyword | Tenant identifier
- Data.syncedTime | long | Sync timestamp (epoch ms)
- Data.taskDates | date | Date of the task
- Data.syncedTimeStamp | date | Sync timestamp (date format)

# Staff fields
- Data.userName | text | Username of the health worker
- Data.role | keyword | Role of the user
- Data.userAddress | text | Address of the health worker

# Malaria-specific fields
- Data.testedForMalaria | keyword | Whether tested for malaria
- Data.malariaResult | keyword | Malaria test result (positive/negative)
- Data.admittedWithSeriousIllness | keyword | Whether admitted with serious illness
- Data.negativeAndAdmittedWithSeriousIllness | keyword | Negative result but admitted with serious illness
- Data.treatedWithAntiMalarials | keyword | Whether treated with anti-malarials
- Data.nameOfAntiMalarials | keyword | Name of anti-malarials used

# Boundary hierarchy (codes)
- Data.boundaryHierarchyCode.country | keyword | Country code
- Data.boundaryHierarchyCode.state | keyword | State code
- Data.boundaryHierarchyCode.lga | keyword | LGA code
- Data.boundaryHierarchyCode.ward | keyword | Ward code
- Data.boundaryHierarchyCode.community | keyword | Community code
- Data.boundaryHierarchyCode.healthFacility | keyword | Health facility code
```

---

## 11. HF Referral Drug Reaction Index

```
INDEX NAME: oy-hf-referral-drug-reaction-index
DESCRIPTION: Stores referral service task checklists with malaria testing data broken down by service level (US/APE). Use this for questions about children tested at US vs APE level, malaria positivity rates by service level, and referral checklist completion.

FIELDS:
- Data.id | keyword | Unique record ID
- Data.supervisorLevel | keyword | Level of the supervisor
- Data.checklistName | keyword | Name of the checklist
- Data.ageGroup | keyword | Age group of the patient
- Data.createdTime | long | Creation timestamp (epoch ms)
- Data.createdBy | keyword | User who created the record
- Data.projectId | keyword | Project/campaign identifier
- Data.tenantId | keyword | Tenant identifier
- Data.syncedTime | long | Sync timestamp (epoch ms)
- Data.taskDates | date | Date of the task
- Data.syncedTimeStamp | date | Sync timestamp (date format)

# Staff fields
- Data.userName | text | Username of the health worker
- Data.role | keyword | Role of the user
- Data.userAddress | text | Address of the health worker

# US (Unidade Sanitaria) malaria fields
- Data.childrenPresentedUS | long | Children presented at US level
- Data.malariaPositiveUS | long | Malaria positive at US level
- Data.malariaNegativeUS | long | Malaria negative at US level

# APE (Agente Polivalente Elementar) malaria fields
- Data.childrenPresentedAPE | long | Children presented at APE level
- Data.malariaPositiveAPE | long | Malaria positive at APE level
- Data.malariaNegativeAPE | long | Malaria negative at APE level

# Boundary hierarchy (codes)
- Data.boundaryHierarchyCode.country | keyword | Country code
- Data.boundaryHierarchyCode.state | keyword | State code
- Data.boundaryHierarchyCode.lga | keyword | LGA code
- Data.boundaryHierarchyCode.ward | keyword | Ward code
- Data.boundaryHierarchyCode.community | keyword | Community code
- Data.boundaryHierarchyCode.healthFacility | keyword | Health facility code
```

---

## 12. Side Effects Index

```
INDEX NAME: oy-side-effect-index-v1
DESCRIPTION: Tracks adverse side effects reported after product administration, linked to beneficiaries and tasks. Use this for questions about side effects, adverse reactions, symptoms reported, and side effect rates by demographics or location.

FIELDS:
# Nested from SideEffect object
- Data.sideEffect.id | keyword | Unique side effect ID
- Data.sideEffect.clientReferenceId | keyword | Client-side reference ID
- Data.sideEffect.taskId | keyword | Associated task ID
- Data.sideEffect.taskClientReferenceId | keyword | Client-side task reference
- Data.sideEffect.projectBeneficiaryId | keyword | Project beneficiary ID
- Data.sideEffect.projectBeneficiaryClientReferenceId | keyword | Client-side project beneficiary reference
- Data.sideEffect.symptoms | keyword | List of reported symptoms
- Data.sideEffect.tenantId | keyword | Tenant identifier
- Data.sideEffect.isDeleted | boolean | Soft delete flag
- Data.sideEffect.auditDetails.createdBy | keyword | User who created the record
- Data.sideEffect.auditDetails.createdTime | long | Creation timestamp (epoch ms)

# Beneficiary demographics
- Data.individualId | keyword | Individual identifier
- Data.dateOfBirth | long | Date of birth (epoch ms)
- Data.age | long | Age in months
- Data.gender | keyword | Gender of the beneficiary
- Data.symptoms | text | Reported symptoms (flattened)

# Staff fields
- Data.userName | text | Username of the field worker
- Data.nameOfUser | text | Full name of the field worker
- Data.role | keyword | Role of the user
- Data.userAddress | text | Address of the field worker

# Boundary hierarchy (names)
- Data.boundaryHierarchy.country | keyword | Country name
- Data.boundaryHierarchy.state | keyword | State name
- Data.boundaryHierarchy.lga | keyword | LGA name
- Data.boundaryHierarchy.ward | keyword | Ward name
- Data.boundaryHierarchy.community | keyword | Community name
- Data.boundaryHierarchy.healthFacility | keyword | Health facility name

# Boundary hierarchy (codes)
- Data.boundaryHierarchyCode.country | keyword | Country code
- Data.boundaryHierarchyCode.state | keyword | State code
- Data.boundaryHierarchyCode.lga | keyword | LGA code
- Data.boundaryHierarchyCode.ward | keyword | Ward code
- Data.boundaryHierarchyCode.community | keyword | Community code
- Data.boundaryHierarchyCode.healthFacility | keyword | Health facility code

# Sync fields
- Data.localityCode | keyword | Locality boundary code
- Data.taskDates | date | Date of the task
- Data.syncedDate | date | Date the record was synced
```

---

## 13. Stock Reconciliation Index

```
INDEX NAME: oy-stock-reconciliation-index-v1
DESCRIPTION: Tracks stock reconciliation events comparing physical counts vs calculated counts at facilities. Use this for questions about stock discrepancies, reconciliation history, physical vs expected counts, and facility-level stock accuracy.

FIELDS:
# Nested from StockReconciliation object
- Data.stockReconciliation.id | keyword | Unique reconciliation ID
- Data.stockReconciliation.clientReferenceId | keyword | Client-side reference ID
- Data.stockReconciliation.tenantId | keyword | Tenant identifier
- Data.stockReconciliation.facilityId | keyword | Facility identifier
- Data.stockReconciliation.productVariantId | keyword | Product variant identifier
- Data.stockReconciliation.referenceId | keyword | Reference identifier
- Data.stockReconciliation.referenceIdType | keyword | Type of reference ID
- Data.stockReconciliation.physicalCount | long | Actual physical count of stock
- Data.stockReconciliation.calculatedCount | long | System-calculated expected count
- Data.stockReconciliation.commentsOnReconciliation | text | Comments on the reconciliation
- Data.stockReconciliation.dateOfReconciliation | long | Date of reconciliation (epoch ms)
- Data.stockReconciliation.isDeleted | boolean | Soft delete flag
- Data.stockReconciliation.auditDetails.createdBy | keyword | User who created the record
- Data.stockReconciliation.auditDetails.createdTime | long | Creation timestamp (epoch ms)

# Facility details
- Data.facilityName | text | Name of the facility
- Data.facilityType | keyword | Type of facility
- Data.facilityLevel | keyword | Level of the facility in hierarchy
- Data.facilityTarget | long | Target stock for the facility
- Data.productName | keyword | Name of the product

# Staff fields
- Data.userName | text | Username of the staff member
- Data.nameOfUser | text | Full name of the staff member
- Data.role | keyword | Role of the user
- Data.userAddress | text | Address of the staff member

# Boundary hierarchy (names)
- Data.boundaryHierarchy.country | keyword | Country name
- Data.boundaryHierarchy.state | keyword | State name
- Data.boundaryHierarchy.lga | keyword | LGA name
- Data.boundaryHierarchy.ward | keyword | Ward name
- Data.boundaryHierarchy.community | keyword | Community name
- Data.boundaryHierarchy.healthFacility | keyword | Health facility name

# Boundary hierarchy (codes)
- Data.boundaryHierarchyCode.country | keyword | Country code
- Data.boundaryHierarchyCode.state | keyword | State code
- Data.boundaryHierarchyCode.lga | keyword | LGA code
- Data.boundaryHierarchyCode.ward | keyword | Ward code
- Data.boundaryHierarchyCode.community | keyword | Community code
- Data.boundaryHierarchyCode.healthFacility | keyword | Health facility code

# Sync fields
- Data.localityCode | keyword | Locality boundary code
- Data.syncedTimeStamp | date | Sync timestamp (date format)
- Data.syncedTime | long | Sync timestamp (epoch ms)
- Data.syncedDate | date | Date the record was synced
- Data.taskDates | date | Date of the task
```

---

## 14. Attendance Log Index

```
INDEX NAME: oy-transformer-save-attendance-log
DESCRIPTION: Tracks individual attendance log entries for health campaign workers, linked to attendance registers. Use this for questions about worker attendance, attendance times, register-level attendance tracking, and worker presence by location.

FIELDS:
# Nested from AttendanceLog object
- attendanceLog.id | keyword | Unique log ID
- attendanceLog.registerId | keyword | Attendance register ID
- attendanceLog.individualId | keyword | Individual (attendee) ID
- attendanceLog.tenantId | keyword | Tenant identifier
- attendanceLog.time | long | Attendance timestamp
- attendanceLog.type | keyword | Attendance type (ENTRY, EXIT)
- attendanceLog.status | keyword | Attendance status
- attendanceLog.auditDetails.createdBy | keyword | User who created the record
- attendanceLog.auditDetails.createdTime | long | Creation timestamp (epoch ms)

# Attendee info
- givenName | text | Given name of the attendee
- familyName | text | Family name of the attendee
- attendanceTime | date | Formatted attendance time
- registerServiceCode | keyword | Service code of the register
- registerName | text | Name of the attendance register
- registerNumber | keyword | Register number

# Staff fields
- userName | text | Username of the field worker
- nameOfUser | text | Full name of the field worker
- role | keyword | Role of the user
- userAddress | text | Address of the field worker

# Boundary hierarchy (names)
- boundaryHierarchy.country | keyword | Country name
- boundaryHierarchy.state | keyword | State name
- boundaryHierarchy.lga | keyword | LGA name
- boundaryHierarchy.ward | keyword | Ward name
- boundaryHierarchy.community | keyword | Community name
- boundaryHierarchy.healthFacility | keyword | Health facility name

# Boundary hierarchy (codes)
- boundaryHierarchyCode.country | keyword | Country code
- boundaryHierarchyCode.state | keyword | State code
- boundaryHierarchyCode.lga | keyword | LGA code
- boundaryHierarchyCode.ward | keyword | Ward code
- boundaryHierarchyCode.community | keyword | Community code
- boundaryHierarchyCode.healthFacility | keyword | Health facility code
```

---

## 15. Attendance Register Index

```
INDEX NAME: oy-transformer-save-attendance-register
DESCRIPTION: Stores attendance register metadata with attendee lists and staff info. Use this for questions about attendance registers, register periods, registered attendees, and register-level summaries.

FIELDS:
# Nested from AttendanceRegister object
- attendanceRegister.id | keyword | Unique register ID
- attendanceRegister.tenantId | keyword | Tenant identifier
- attendanceRegister.registerNumber | keyword | Register number
- attendanceRegister.name | text | Register name
- attendanceRegister.referenceId | keyword | Reference identifier
- attendanceRegister.serviceCode | keyword | Service code
- attendanceRegister.startDate | long | Register start date (epoch ms)
- attendanceRegister.endDate | long | Register end date (epoch ms)
- attendanceRegister.status | keyword | Register status
- attendanceRegister.auditDetails.createdBy | keyword | User who created the record
- attendanceRegister.auditDetails.createdTime | long | Creation timestamp (epoch ms)

# Attendee info
- attendeesInfo | object | Map of attendee IDs to names (givenName, familyName)
- transformerTimeStamp | date | Transformer processing timestamp
```

---

## 16. PGR Index (Public Grievance Redressal)

```
INDEX NAME: oy-pgr-services
DESCRIPTION: Stores public grievance/complaint records including service requests, status, and resolution details. Use this for questions about complaints, grievances, service request status, and complaint patterns by location.

FIELDS:
# Nested from Service (PGR) object
- service.id | keyword | Unique service request ID
- service.tenantId | keyword | Tenant identifier
- service.serviceCode | keyword | Service/complaint code
- service.serviceRequestId | keyword | Service request identifier
- service.description | text | Description of the complaint
- service.accountId | keyword | Account identifier
- service.rating | long | Rating given
- service.applicationStatus | keyword | Status of the complaint
- service.source | keyword | Source of the complaint
- service.active | boolean | Whether the complaint is active
- service.auditDetails.createdBy | keyword | User who created the record
- service.auditDetails.createdTime | long | Creation timestamp (epoch ms)

# Staff fields
- userName | text | Username of the handler
- nameOfUser | text | Full name of the handler
- role | keyword | Role of the user
- userAddress | text | Address of the handler

# Boundary hierarchy (names)
- boundaryHierarchy.country | keyword | Country name
- boundaryHierarchy.state | keyword | State name
- boundaryHierarchy.lga | keyword | LGA name
- boundaryHierarchy.ward | keyword | Ward name
- boundaryHierarchy.community | keyword | Community name
- boundaryHierarchy.healthFacility | keyword | Health facility name

# Boundary hierarchy (codes)
- boundaryHierarchyCode.country | keyword | Country code
- boundaryHierarchyCode.state | keyword | State code
- boundaryHierarchyCode.lga | keyword | LGA code
- boundaryHierarchyCode.ward | keyword | Ward code
- boundaryHierarchyCode.community | keyword | Community code
- boundaryHierarchyCode.healthFacility | keyword | Health facility code

# Other fields
- localityCode | keyword | Locality boundary code
- taskDates | date | Date of the task
```

---

## What to do next

1. **Fill in actual ES index names** — Replace all `<TODO>` placeholders with real index names
2. **Remove indexes you don't use** — Delete sections for indexes not deployed in your environment
3. **Verify field types** — Cross-check against actual ES mappings (`GET /<index>/_mapping`)
4. **Add missing enum values** — For keyword fields with fixed values, add them (e.g., status enums)
5. **Adjust descriptions** — Refine the DESCRIPTION to better guide the LLM on when to pick each index
