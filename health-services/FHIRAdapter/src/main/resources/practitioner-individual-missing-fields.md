# Practitioner ↔ Individual — Missing & Future Fields

This document covers FHIR Practitioner fields that are **not currently mapped** in `practitioner-individual-mapping.json`, along with instructions on how to enable them when the eGov backend supports them.

---

> **IMPORTANT — Patient vs Practitioner Differentiation:**
> Both Patient and Practitioner FHIR resources currently map to the same eGov Individual model. There is no field in the eGov Individual model today that distinguishes a patient from a practitioner. This means a search via `/fhir/Practitioner` could return patients and vice versa. To resolve this, a differentiator needs to be added — possible options include:
> - `Individual.userType` (e.g., `EMPLOYEE` for practitioner, `CITIZEN` for patient)
> - `Individual.isSystemUser` (`true` = practitioner, `false` = patient)
> - `Individual.roles[]` (presence of practitioner roles like REGISTRAR, DISTRIBUTOR)
> - `Individual.additionalFields.fhirResourceType` (store "Patient" or "Practitioner" explicitly)
>
> Once a differentiator is chosen, a search filter should be added to each mapping config so that the adapter automatically filters by type when querying the eGov API.

---

## 1. Additional Identifier Mappings (Currently Disabled)

The current config only maps `individualId` as an identifier. The following identifiers can be added to `identifierMappings[]` in the config when the corresponding data is available in eGov.

### Medical Council Registration (PRN - Provider Number)

For practitioners with a medical council registration number (e.g., state medical council, nursing council):

**How to enable:** Add this entry to `identifierMappings[]` in `practitioner-individual-mapping.json`:

```json
{
  "name": "medicalCouncilRegistration",
  "system": "urn:egov:practitioner:registration",
  "egovField": "identifiers[?(@.identifierType=='MCR')].identifierId",
  "use": "official",
  "type": {
    "coding": [{
      "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
      "code": "PRN",
      "display": "Provider number"
    }]
  }
}
```

**Prerequisites:**
- eGov Individual must have an entry in `identifiers[]` with `identifierType: "MCR"`
- Adjust `identifierType` value to match what your eGov setup uses (e.g., `MCR`, `MED_REG`, `COUNCIL_REG`)
- The `system` URI should be updated to your organization's namespace (e.g., `urn:india:nmc:registration` for National Medical Commission)

**FHIR output after enabling:**
```json
{
  "system": "urn:egov:practitioner:registration",
  "value": "REG-12345",
  "use": "official",
  "type": {
    "coding": [{
      "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
      "code": "PRN",
      "display": "Provider number"
    }]
  }
}
```

### Facility/Organization Employee ID

For practitioners who have an employee ID within a facility:

**How to enable:** Add to `identifierMappings[]`:

```json
{
  "name": "employeeId",
  "system": "urn:egov:employee",
  "egovField": "identifiers[?(@.identifierType=='EMPLOYEE_ID')].identifierId",
  "use": "secondary",
  "type": {
    "coding": [{
      "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
      "code": "EN",
      "display": "Employer number"
    }]
  }
}
```

### National Health Worker Registry ID

For practitioners registered in a national health worker registry:

**How to enable:** Add to `identifierMappings[]`:

```json
{
  "name": "healthWorkerRegistryId",
  "system": "https://hwr.abdm.gov.in",
  "egovField": "identifiers[?(@.identifierType=='HWR_ID')].identifierId",
  "use": "official",
  "type": {
    "coding": [{
      "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
      "code": "PRN",
      "display": "Provider number"
    }]
  }
}
```

---

## 2. Unmappable FHIR Practitioner Fields

These fields exist in the FHIR Practitioner resource but have **no equivalent in the eGov Individual model**. They cannot be enabled via config alone — they require eGov model changes.

### qualification[] — Professional Qualifications

The biggest gap. FHIR Practitioner supports:

```json
"qualification": [{
  "identifier": [{ "system": "http://example.org/UniversityIdentifier", "value": "12345" }],
  "code": {
    "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/v2-0360/2.7", "code": "BS", "display": "Bachelor of Science" }],
    "text": "Bachelor of Science"
  },
  "period": { "start": "1995" },
  "issuer": { "display": "Example University" }
}]
```

**What it captures:**
- Degree/certification code and display name
- Qualification identifier (license number)
- Validity period (when obtained, when it expires)
- Issuing organization

**Why it can't be mapped:**
- eGov Individual has no `qualification`, `certification`, or `skills` array that stores structured credential data
- eGov `skills[]` exists but is a simple string list, not a structured qualification model

**Potential solutions:**
1. Store serialized qualification JSON in eGov `additionalFields` (data preserved but not searchable)
2. Extend the eGov Individual model with a `qualifications[]` array
3. Use a separate eGov service for credential management and link to it

### communication[] — Languages

```json
"communication": [{
  "coding": [{ "system": "urn:ietf:bcp:47", "code": "en", "display": "English" }]
}]
```

**Why it can't be mapped:** No language field in eGov Individual.

**Potential solution:** Store in `additionalFields` or add a `languages[]` field to the eGov model.

### name[].prefix — Professional Title

```json
"name": [{ "prefix": ["Dr"], "given": ["Adam"], "family": "Careful" }]
```

**Why it can't be mapped:** eGov `name` has `givenName`, `familyName`, `otherNames` — no prefix/suffix fields.

**Potential solution:** Prepend prefix to `givenName` (e.g., "Dr Adam") — but this is lossy and makes name searches unreliable.

### photo (full Attachment)

eGov has a `photo` field but FHIR expects a full `Attachment` type with `contentType`, `url`, `size`, `hash`, `title`, `creation`. Only the URL/data can be mapped; metadata fields are lost.

**To enable basic photo mapping:** Requires a `toAttachment` transform (not yet implemented).

---

## 3. Common Identifier Type Codes for Practitioners

Reference for when adding new identifier mappings:

| Code | Display | Use Case |
|------|---------|----------|
| `PRN` | Provider number | Medical council registration, nursing license |
| `EN` | Employer number | Facility/organization employee ID |
| `NPI` | National provider identifier | National-level practitioner ID |
| `MD` | Medical license number | State medical license |
| `RN` | Registered Nurse number | Nursing registration |
| `DN` | Doctor number | Doctor-specific ID |
| `TAX` | Tax ID number | PAN (if needed for practitioners) |
| `NI` | National unique individual identifier | Aadhaar (if needed) |

Source: [http://terminology.hl7.org/CodeSystem/v2-0203](http://terminology.hl7.org/CodeSystem/v2-0203)

---

## 4. How to Add a New Identifier

1. Ensure the identifier exists in eGov `identifiers[]` with a known `identifierType`
2. Add a new entry to `identifierMappings[]` in `practitioner-individual-mapping.json`
3. Set the appropriate `system` URI, `use`, and `type.coding` code
4. Restart the service — no code changes needed
5. Test with a search to verify the identifier appears in the FHIR response
