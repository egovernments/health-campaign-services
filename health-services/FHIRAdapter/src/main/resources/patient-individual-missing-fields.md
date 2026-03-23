# Patient ↔ Individual — Missing & Future Fields

This document covers FHIR Patient fields that are **not currently mapped** in `individual-patient-mapping.json`, along with instructions on how to enable them when the eGov backend supports them.

---

> **IMPORTANT — Patient vs Practitioner Differentiation:**
> Both Patient and Practitioner FHIR resources currently map to the same eGov Individual model. There is no field in the eGov Individual model today that distinguishes a patient from a practitioner. This means a search via `/fhir/Patient` could return practitioners and vice versa. To resolve this, a differentiator needs to be added — possible options include:
> - `Individual.userType` (e.g., `CITIZEN` for patient, `EMPLOYEE` for practitioner)
> - `Individual.isSystemUser` (`false` = patient, `true` = practitioner)
> - `Individual.roles[]` (presence of practitioner roles like REGISTRAR, DISTRIBUTOR)
> - `Individual.additionalFields.fhirResourceType` (store "Patient" or "Practitioner" explicitly)
>
> Once a differentiator is chosen, a search filter should be added to each mapping config so that the adapter automatically filters by type when querying the eGov API.

---

## 1. Fields That Can Be Enabled via Config (No Code Changes)

### Additional Identifier Mappings

The current config maps 5 identifiers (individualId, ABHA Address, ABHA Number, Aadhaar, PAN). More can be added.

#### Driver's License

**How to enable:** Add to `identifierMappings[]` in `individual-patient-mapping.json`:

```json
{
  "name": "drivingLicense",
  "system": "urn:india:dl",
  "egovField": "identifiers[?(@.identifierType=='DRIVING_LICENSE')].identifierId",
  "use": "secondary",
  "type": {
    "coding": [{
      "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
      "code": "DL",
      "display": "Driver's license number"
    }]
  }
}
```

#### Passport

```json
{
  "name": "passport",
  "system": "urn:india:passport",
  "egovField": "identifiers[?(@.identifierType=='PASSPORT')].identifierId",
  "use": "secondary",
  "type": {
    "coding": [{
      "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
      "code": "PPN",
      "display": "Passport number"
    }]
  }
}
```

#### State Health ID

```json
{
  "name": "stateHealthId",
  "system": "urn:egov:state-health-id",
  "egovField": "identifiers[?(@.identifierType=='STATE_HEALTH_ID')].identifierId",
  "use": "official",
  "type": {
    "coding": [{
      "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
      "code": "MR",
      "display": "Medical record number"
    }]
  }
}
```

**Prerequisites:**
- eGov Individual must have an entry in `identifiers[]` with the matching `identifierType`
- Adjust `identifierType` and `system` values to match your eGov setup
- Restart the service — no code changes needed

---

## 2. Fields That Need Small Code Changes (Transforms)

These fields have a clear mapping path but need a new transform to be implemented in `TransformService.java`.

### tenantId → meta.tag

**Spec says:** Store tenantId as `meta.tag` with system `urn:egov:tenant`

**FHIR output expected:**
```json
"meta": {
  "lastUpdated": "2024-03-09T16:00:00Z",
  "tag": [{ "system": "urn:egov:tenant", "code": "dev" }]
}
```

**What's needed:** New `toTag` transform in `TransformService.java`

**Config to add (once transform is implemented):**
```json
{
  "fhirField": "meta.tag",
  "egovField": "tenantId",
  "transform": "toTag",
  "transformConfig": { "system": "urn:egov:tenant" }
}
```

### source → meta.source

**Spec says:** Map as provenance hint with stable URI namespace

**FHIR output expected:**
```json
"meta": {
  "source": "urn:egov:source:digit"
}
```

**What's needed:** New `prefixUri` transform

**Config to add (once transform is implemented):**
```json
{
  "fhirField": "meta.source",
  "egovField": "source",
  "transform": "prefixUri",
  "transformConfig": { "prefix": "urn:egov:source:" }
}
```

### photo → Patient.photo[0]

**Spec says:** Map as FHIR Attachment with url or data + contentType

**FHIR output expected:**
```json
"photo": [{
  "url": "https://storage.digit.org/photos/abc123.jpg",
  "contentType": "image/jpeg"
}]
```

**What's needed:** New `toAttachment` transform

**Config to add (once transform is implemented):**
```json
{
  "fhirField": "photo[0]",
  "egovField": "photo",
  "transform": "toAttachment"
}
```

### contact[] → fatherName / husbandName (Next-of-Kin)

**Spec says:** Map to structured FHIR contact with relationship coding

**FHIR output expected:**
```json
"contact": [{
  "relationship": [{
    "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/v2-0131", "code": "N", "display": "Next-of-Kin" }]
  }],
  "name": { "text": "Ramesh Kumar" }
}]
```

**What's needed:** New `contactEntry` transform (similar to `telecomEntry`)

**Config to add (once transform is implemented):**
```json
{
  "fhirField": "contact",
  "egovField": "fatherName",
  "transform": "contactEntry",
  "transformConfig": {
    "relationship": "N",
    "system": "http://terminology.hl7.org/CodeSystem/v2-0131"
  }
},
{
  "fhirField": "contact",
  "egovField": "husbandName",
  "transform": "contactEntry",
  "transformConfig": {
    "relationship": "C",
    "system": "http://terminology.hl7.org/CodeSystem/v2-0131"
  }
}
```

**Known limitation:** eGov only stores the name string. Phone, address, gender of the contact person will not be available in the FHIR response.

---

## 3. Extension Mappings (Config Exists, Code Partially Implemented)

### Currently Active Extensions

These are implemented and working:

| Extension URL | eGov Field | Value Type | Transform |
|---|---|---|---|
| `https://digit.org/extensions/isDeleted` | `isDeleted` | boolean | — |
| `https://digit.org/extensions/audit/createdTime` | `auditDetails.createdTime` | dateTime | epochToIso |

### Future Extensions (Add to `extensionMappings[]` When Needed)

#### createdBy

```json
{
  "url": "https://digit.org/extensions/audit/createdBy",
  "egovField": "auditDetails.createdBy",
  "valueType": "string"
}
```

#### lastModifiedBy

```json
{
  "url": "https://digit.org/extensions/audit/lastModifiedBy",
  "egovField": "auditDetails.lastModifiedBy",
  "valueType": "string"
}
```

#### additionalFields (per field)

For each field in eGov `additionalFields`, add a separate extension:

```json
{
  "url": "https://digit.org/extensions/additional/<fieldName>",
  "egovField": "additionalFields.<fieldName>",
  "valueType": "string"
}
```

**Note:** This requires knowing the field names in advance. A generic `additionalFields` iterator is not yet implemented.

---

## 4. Unmappable FHIR Patient Fields (eGov Model Limitations)

These fields have **no equivalent in the eGov Individual model**. They cannot be enabled via config or transforms — they require eGov model changes.

| FHIR Field | Description | Why It Can't Be Mapped |
|---|---|---|
| Multiple `name[]` entries | Official, usual, maiden name variants | eGov has a single `name` object. Only the first entry is mapped. |
| `name[].prefix` / `suffix` | Dr., Mr., Jr., III | No prefix/suffix fields in eGov `name`. |
| `telecom[].rank` | Contact priority ordering | eGov has flat phone/email fields — no ranking. |
| `telecom[].period` | Contact validity period | eGov doesn't track when a contact was active/expired. |
| `address[].type` | postal / physical / both | eGov uses `type` for PERMANENT/CORRESPONDENCE, not postal/physical. |
| `address[].text` | Pre-formatted display string | No single display-text field in eGov. |
| `address[].period` | Address validity period | eGov doesn't track address time ranges. |
| `identifier[].period` | Identifier validity period | eGov identifiers have no temporal tracking. |
| `identifier[].assigner` | Issuing organization | eGov doesn't track who assigned an identifier. |
| `deceasedBoolean` / `deceasedDateTime` | Whether/when patient died | eGov `isDeleted` is a soft-delete flag, not a deceased indicator. Semantically different. |
| `maritalStatus` | Married, single, divorced, etc. | Not in eGov Individual model. |
| `multipleBirthBoolean` | Part of a multiple birth | Not in eGov Individual model. |
| `communication[]` | Languages spoken | Not in eGov Individual model. |
| `managingOrganization` | Responsible organization | eGov uses `tenantId`, not a FHIR Organization reference. |
| `generalPractitioner[]` | Nominated care providers | Not in eGov Individual model. |
| `link[]` | Links to other Patient resources | Not in eGov Individual model. |

### Potential Solutions for Unmappable Fields

1. **eGov `additionalFields`**: Serialize unmappable FHIR fields into this map as JSON. Data is preserved for round-trip but not searchable in eGov.
2. **eGov Model Extension**: Add new fields to the Individual model for the most commonly needed ones (e.g., `deceasedDate`, `maritalStatus`).
3. **Separate FHIR Store**: Use a FHIR-native database (e.g., HAPI FHIR server) alongside eGov. The adapter writes to both — eGov for operational fields, FHIR store for full clinical data.

---

## 5. Common Identifier Type Codes for Patients

Reference for when adding new identifier mappings:

| Code | Display | Use Case |
|------|---------|----------|
| `MR` | Medical record number | Hospital/facility patient ID, eGov individualId |
| `SB` | Social Beneficiary Identifier | ABHA Address, ABHA Number |
| `NI` | National unique individual identifier | Aadhaar |
| `TAX` | Tax ID number | PAN card |
| `DL` | Driver's license number | Driver's license |
| `PPN` | Passport number | Passport |
| `SS` | Social Security number | Social security (if applicable) |
| `PI` | Patient internal identifier | Internal system ID |

Source: [http://terminology.hl7.org/CodeSystem/v2-0203](http://terminology.hl7.org/CodeSystem/v2-0203)

---

## 6. How to Add a New Identifier

1. Ensure the identifier exists in eGov `identifiers[]` with a known `identifierType`
2. Add a new entry to `identifierMappings[]` in `individual-patient-mapping.json`
3. Set the appropriate `system` URI, `use`, and `type.coding` code
4. If privacy masking is needed, add `"privacy": { "mask": true, "pattern": "XXXX-{last4}" }`
5. If the identifier should be excluded from responses, add `"privacy": { "excludeFromResponse": true }`
6. Restart the service — no code changes needed
7. Test with a search to verify the identifier appears in the FHIR response

---

## 7. How to Add a New Extension

1. Add a new entry to `extensionMappings[]` in `individual-patient-mapping.json`
2. Set the `url` (namespace URI), `egovField` (dot notation path), and `valueType` (boolean, string, dateTime, integer, decimal)
3. If a transform is needed (e.g., `epochToIso`), add `transform` and `transformConfig`
4. Restart the service — no code changes needed
5. Test with a search to verify the extension appears in the FHIR response
