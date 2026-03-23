# Patient ↔ Individual Mapping — Spec vs Implementation

**Date:** 2026-03-18

---

## Summary

| Category | Count |
|----------|-------|
| Matching (spec = service) | 10 |
| Improved in service (exceeds spec) | 4 |
| Spec differs from implementation | 3 |
| Not yet implemented | 4 |

---

## Table 1: Spec vs Implementation — Field-by-Field Comparison

| # | eGov Field | Spec (Google Sheet) | Implemented (Service) | Status | Difference |
|---|-----------|--------------------|-----------------------|--------|------------|
| 1 | `id` / `individualId` | `Patient.id` = GUID; `identifier[egovIndividualId]` = business id | `Patient.id` = `id` direct; `identifier[0]` = `individualId` with system, use, type MR | **Match** | Both work. eGov already generates UUIDs for `id`. `individualId` correctly placed as business identifier. |
| 2 | ABHA | `identifier[abha]` (one or two slices) | ABHA Address + ABHA Number as separate identifier entries with type SB + privacy masking. Also Aadhaar (NI) and PAN (TAX). | **Improved** | Service adds 5 identifier types with masking and excludeFromResponse. Spec only mentions ABHA. |
| 3 | `tenantId` | `meta.tag` with `urn:egov:tenant` | URL param + request body. Search `_tag` → `tenantId`. **Not in FHIR response.** | **Differs** | Service doesn't set `meta.tag` in response. Needs `toTag` transform. |
| 4 | `name` | `Patient.name[0]` with family, given, text | `name[0].use`=official (staticValue), `text` (concatFields), `given` (joinArray preserves all), `family` (direct) | **Improved** | Service adds `use`, `text`, handles given as proper array. Spec only mentions basic mapping. |
| 5 | `dateOfBirth` | `Patient.birthDate` direct YYYY-MM-DD | `epochToDate` transform handles epoch, DD/MM/YYYY, and ISO formats | **Improved** | Service handles multiple input formats. |
| 6 | `gender` | Normalize to allowed codes | `codeMap`: MALE→male, FEMALE→female, TRANSGENDER→other, OTHER→other (bidirectional) | **Match** | Both normalize. Service includes TRANSGENDER. |
| 7 | `mobileNumber` | `telecom[mobile]` system=phone, use=mobile | `telecomEntry` filter-based: matches by system+use regardless of array order | **Improved** | Service is order-independent. Spec assumes positional. |
| 8 | `altContactNumber` | `telecom[altPhone]` with correct use | `telecomEntry` system=phone, use=work | **Match** | Both map to second phone entry. |
| 9 | `email` | `telecom[email]` system=email | `telecomEntry` system=email | **Match** | Identical. |
| 10 | `photo` | `Patient.photo[0]` Attachment with url/data+contentType | Not mapped | **Missing** | Needs `toAttachment` transform. |
| 11 | `isDeleted` | `active` = !isDeleted + extension for original flag | `active` = negate(isDeleted). Extension config exists but not processed. | **Differs** | `active` works. Extension for exact isDeleted semantics not yet applied. |
| 12 | `source` | `meta.source` as provenance hint | Not mapped | **Missing** | Needs `prefixUri` transform. |
| 13 | `auditDetails.lastModifiedTime` | `meta.lastUpdated` | `meta.lastUpdated` with `epochToIso` | **Match** | Identical. |
| 14 | `auditDetails.createdTime/By/lastModifiedBy` | Spec leaves blank (suggests Provenance) | Config exists in `extensionMappings` but code doesn't process it | **Differs** | Both agree on extensions. Code needs to iterate `extensionMappings`. |
| 15 | `additionalFields` | `Patient.extension[]` each field as typed extension | Not mapped | **Missing** | Needs generic extension builder. |
| 16 | `address` | Not detailed in spec | Full nested: line(joinArray), city, district, state, postalCode, country, use(codeMap), lat/long | **Extra** | Service covers address comprehensively. Spec doesn't mention it. |
| 17 | `Aadhaar / PAN` | Mentioned as privacy concern in comments | Full identifier mappings with masking patterns and excludeFromResponse | **Extra** | Service implements what spec only raises as a concern. |

---

## Table 2: Current Implemented Mapping (Verified by JUnit)

| eGov Individual Field | FHIR Patient Path | Transform | Direction | Example |
|----------------------|-------------------|-----------|-----------|---------|
| `id` | `Patient.id` | Direct | Both | `"a1b2c3d4-e5f6-..."` |
| `individualId` | `identifier[].value` | identifierMapping | eGov→FHIR | `{system:"urn:egov:individual", type:{code:"MR"}, use:"official"}` |
| `identifiers[ABHA_ADDRESS]` | `identifier[].value` | identifierMapping | eGov→FHIR | `{system:"https://healthid.abdm.gov.in/abha-address", type:{code:"SB"}}` |
| `identifiers[ABHA_NUMBER]` | `identifier[].value` | identifierMapping | eGov→FHIR | `{type:{code:"SB"}, privacy: masked}` |
| `identifiers[AADHAR]` | `identifier[].value` | identifierMapping | eGov→FHIR | `{type:{code:"NI"}, excludeFromResponse}` |
| `identifiers[PAN]` | `identifier[].value` | identifierMapping | eGov→FHIR | `{type:{code:"TAX"}, masked}` |
| `auditDetails.lastModifiedTime` | `meta.lastUpdated` | epochToIso | eGov→FHIR | `"2024-03-09T16:00:00Z"` |
| `isDeleted` | `active` | negate | Both | `false → true` |
| — | `name[0].use` | staticValue | eGov→FHIR | `"official"` |
| `name (givenName+familyName)` | `name[0].text` | concatFields | eGov→FHIR | `"Ramu Singh Binny"` |
| `name.givenName` | `name[0].given` | joinArray (space) | Both | `["Ramu","Singh"] ↔ "Ramu Singh"` |
| `name.familyName` | `name[0].family` | Direct | Both | `"Binny"` |
| `dateOfBirth` | `birthDate` | epochToDate | Both | `"17/02/1973" ↔ "1973-02-17"` |
| `gender` | `gender` | codeMap | Both | `MALE ↔ male` |
| `mobileNumber` | `telecom[]` | telecomEntry | Both | `{system:"phone", use:"mobile", value:"7776543210"}` |
| `email` | `telecom[]` | telecomEntry | Both | `{system:"email", value:"ramu@example.com"}` |
| `altContactNumber` | `telecom[]` | telecomEntry | Both | `{system:"phone", use:"work", value:"9988776655"}` |
| `address.addressLine1` | `address[].line` | joinArray (comma) | Both | `["234 Main St","Apt 4"] ↔ "234 Main St, Apt 4"` |
| `address.city` | `address[].city` | Direct | Both | `"agarthala"` |
| `address.district` | `address[].district` | Direct | Both | `"West Tripura"` |
| `address.state` | `address[].state` | Direct | Both | `"TR"` |
| `address.pincode` | `address[].postalCode` | Direct | Both | `"799001"` |
| `address.country` | `address[].country` | Direct | Both | `"India"` |
| `address.type` | `address[].use` | codeMap | Both | `PERMANENT ↔ home` |
| `address.latitude` | `address[].extension[geolocation]` | Extension path | Both | `23.8315` |
| `address.longitude` | `address[].extension[geolocation]` | Extension path | Both | `91.2868` |

---

## Table 3: Remaining Gaps

| # | eGov Field | Spec Target | What's Needed | Effort |
|---|-----------|-------------|---------------|--------|
| 1 | `tenantId` | `meta.tag` | New `toTag` transform | Code + Config |
| 2 | `source` | `meta.source` | New `prefixUri` transform | Code + Config |
| 3 | `photo` | `Patient.photo[0]` | New `toAttachment` transform | Code + Config |
| 4 | `isDeleted` (extension) | `extension[egov-isDeleted]` | Process `extensionMappings` in TransformService | Code only |
| 5 | `auditDetails.*` (extensions) | `extension[audit/*]` | Same as #4 | Code only |
| 6 | `additionalFields` | `Patient.extension[]` | Generic extension builder | Code + Config |

---

## Key Findings from Comparison

### Where the service exceeds the spec:
- **Identifiers**: 5 types with type codes (MR, SB, NI, TAX) + privacy masking — spec only mentions ABHA
- **Name**: use, text, given as proper array with joinArray — spec says basic mapping
- **Telecom**: filter-based matching (order-independent) — spec assumes positional
- **Address**: full nested mapping with joinArray, codeMap, geolocation — spec doesn't cover it
- **Date**: handles epoch + DD/MM/YYYY + ISO — spec says direct only

### Where the spec has items we haven't done yet (6 gaps):
1. `tenantId` → `meta.tag` (needs `toTag` transform)
2. `source` → `meta.source` (needs `prefixUri` transform)
3. `photo` → `Patient.photo[0]` (needs `toAttachment` transform)
4. `isDeleted` extension (config exists, code needs to process `extensionMappings`)
5. `auditDetails` extensions (same as #4)
6. `additionalFields` → generic extensions
