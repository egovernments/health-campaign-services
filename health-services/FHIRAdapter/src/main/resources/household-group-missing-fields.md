# Household ↔ Group — Missing & Future Fields

This document covers the mapping between eGov Household/HouseholdMember and FHIR Group resource, including fields that require additional work.

---

> **IMPORTANT — HouseholdMember Mapping:**
> FHIR Group contains `member[]` entries that reference other resources (Patient/Practitioner). In eGov, HouseholdMember is a separate model with its own API (`/household/member/v1/_search`, `_create`, etc.). The current config maps **Household → Group** for the top-level fields. Mapping HouseholdMember → Group.member[] requires **two eGov API calls** per FHIR Group request:
> 1. Fetch Household from `/household/v1/_search`
> 2. Fetch HouseholdMembers from `/household/member/v1/_search` using the householdId
> 3. Combine both into a single FHIR Group response
>
> This multi-call orchestration is **not yet implemented** in the adapter. Currently only the Household-level fields are mapped. Group.member[] will be empty until the adapter supports composite resource building.

---

## 1. HouseholdMember → Group.member[] (Not Yet Implemented)

Each HouseholdMember would become a `member[]` entry in the Group:

### Proposed Mapping

```json
"member[].entity.reference"       → "Patient/{individualId}" or "Practitioner/{individualId}"
"member[].involvement[0].coding"  → isHeadOfHousehold ? {code:"HEAD"} : {code:"MEMBER"}
"member[].inactive"               → isDeleted
"member[].period.start"           → auditDetails.createdTime (epochToIso)
```

### Proposed Extensions on member[]

```json
{ "url": "https://digit.org/extensions/householdMember/id", "valueString": "<id>" },
{ "url": "https://digit.org/extensions/householdMember/clientReferenceId", "valueString": "<clientReferenceId>" },
{ "url": "https://digit.org/extensions/householdMember/householdClientReferenceId", "valueString": "<householdClientReferenceId>" },
{ "url": "https://digit.org/extensions/householdMember/individualClientReferenceId", "valueString": "<individualClientReferenceId>" },
{ "url": "https://digit.org/extensions/householdMember/rowVersion", "valueInteger": 1 }
```

### eGov HouseholdMember API

| Operation | Endpoint | Request Wrapper | Response Wrapper |
|-----------|----------|-----------------|------------------|
| Create | `/household/member/v1/_create` | `HouseholdMember` | `HouseholdMember` |
| Bulk Create | `/household/member/v1/bulk/_create` | `HouseholdMembers[]` | `HouseholdMembers[]` |
| Search | `/household/member/v1/_search` | `HouseholdMember` | `HouseholdMembers[]` + `TotalCount` |
| Update | `/household/member/v1/_update` | `HouseholdMember` | `HouseholdMember` |
| Delete | `/household/member/v1/_delete` | `HouseholdMember` | `HouseholdMember` |

### Code Changes Required

To support Group.member[], the adapter needs:
1. A **composite resource builder** that makes multiple eGov API calls and merges results
2. The `egovToFhir` method would need to accept multiple eGov responses (Household + HouseholdMembers)
3. On create, the adapter would need to create the Household first, then create HouseholdMember records for each member entry

---

## 2. Address Mapping via Extensions

FHIR Group has **no native address field**. The Household address is mapped entirely via extensions. All address fields are in `extensionMappings[]` in the config.

If a future FHIR profile adds an address extension with the standard FHIR Address datatype, the extensions can be consolidated into a single `valueAddress` extension:

```json
{
  "url": "https://digit.org/extensions/household/address",
  "valueAddress": {
    "line": ["234 Main Street, Apt 4"],
    "city": "agarthala",
    "district": "West Tripura",
    "state": "TR",
    "postalCode": "799001",
    "country": "India"
  }
}
```

This would require a `toAddress` transform that builds a FHIR Address object from the flat eGov address fields.

---

## 3. Unmappable Household Fields

| eGov Field | Why | Potential Solution |
|-----------|-----|-------------------|
| `clientReferenceId` | Offline-specific ID | Extension |
| `clientAuditDetails` | Offline audit trail | Extension |
| `applicationId` | eGov-specific workflow ID | Extension |
| `hasErrors` | eGov validation state | Extension |
| `source` | eGov source system | Extension or `meta.source` |
| `address.locality` (Boundary) | Complex object with geometry | Only `locality.code` mapped as extension; geometry not mappable |

---

## 4. Unmappable HouseholdMember Fields

| eGov Field | Why | Potential Solution |
|-----------|-----|-------------------|
| `householdClientReferenceId` | Offline-specific | Extension on member[] |
| `individualClientReferenceId` | Offline-specific | Extension on member[] |
| `clientReferenceId` | Offline-specific | Extension on member[] |
| `clientAuditDetails` | Offline audit trail | Extension on member[] |
| `applicationId` | eGov-specific | Extension on member[] |
| `hasErrors` | eGov validation state | Extension on member[] |
| `source` | eGov source system | Extension on member[] |

---

## 5. FHIR Group Fields Not Used

These FHIR Group fields have no eGov equivalent and are not mapped:

| FHIR Field | Description | Why Not Used |
|-----------|-------------|-------------|
| `code` | Group classification code | Could set to `{code:"household"}` via staticValue if needed |
| `managingEntity` | Entity maintaining the group | Could map to tenantId but semantics differ |
| `characteristic[]` | Traits shared by members | No equivalent in eGov Household |
| `description` | Natural language explanation | No description field in eGov |
| `url` | Canonical identifier | Not applicable |
| `version` | Version identifier | eGov uses `rowVersion` (integer), FHIR uses string |
