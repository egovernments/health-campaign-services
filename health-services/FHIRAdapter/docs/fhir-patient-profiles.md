# FHIR Patient Profiles — International vs India (ABDM/ABHA)

**Date:** 2026-03-18

---

## Profile References

| Profile | Specification Link | Example Link |
|---------|-------------------|--------------|
| **International (HL7 Base)** | [HL7 FHIR Patient Resource](https://build.fhir.org/patient.html) | [patient-example.json](https://hl7.org/fhir/patient-example.json.html) |
| **India ABDM/ABHA** | [NRCES Patient StructureDefinition](https://nrces.in/ndhm/fhir/r4/StructureDefinition-Patient.html) | [Patient/example-01.json](https://nrces.in/ndhm/fhir/r4/Patient-example-01.json.html) |

---

## Sample 1: International Profile (HL7 Official)

```json
{
  "resourceType": "Patient",
  "id": "example",
  "identifier": [
    {
      "use": "usual",
      "type": {
        "coding": [
          {
            "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
            "code": "MR"
          }
        ]
      },
      "system": "urn:oid:1.2.36.146.595.217.0.1",
      "value": "12345",
      "period": { "start": "2001-05-06" },
      "assigner": { "display": "Acme Healthcare" }
    }
  ],
  "active": true,
  "name": [
    {
      "use": "official",
      "family": "Chalmers",
      "given": ["Peter", "James"]
    },
    {
      "use": "usual",
      "given": ["Jim"]
    },
    {
      "use": "maiden",
      "family": "Windsor",
      "given": ["Peter", "James"],
      "period": { "end": "2002" }
    }
  ],
  "telecom": [
    { "use": "home" },
    { "system": "phone", "value": "(03) 5555 6473", "use": "work", "rank": 1 },
    { "system": "phone", "value": "(03) 3410 5613", "use": "mobile", "rank": 2 },
    { "system": "phone", "value": "(03) 5555 8834", "use": "old", "period": { "end": "2014" } }
  ],
  "gender": "male",
  "birthDate": "1974-12-25",
  "_birthDate": {
    "extension": [
      {
        "url": "http://hl7.org/fhir/StructureDefinition/patient-birthTime",
        "valueDateTime": "1974-12-25T14:35:45-05:00"
      }
    ]
  },
  "deceasedBoolean": false,
  "address": [
    {
      "use": "home",
      "type": "both",
      "text": "534 Erewhon St PeasantVille, Rainbow, Vic  3999",
      "line": ["534 Erewhon St"],
      "city": "PleasantVille",
      "district": "Rainbow",
      "state": "Vic",
      "postalCode": "3999",
      "period": { "start": "1974-12-25" }
    }
  ],
  "contact": [
    {
      "relationship": [
        {
          "coding": [
            { "system": "http://terminology.hl7.org/CodeSystem/v2-0131", "code": "N" }
          ]
        }
      ],
      "name": {
        "family": "du Marché",
        "given": ["Bénédicte"]
      },
      "telecom": [
        { "system": "phone", "value": "+33 (237) 998327" }
      ],
      "address": {
        "use": "home",
        "type": "both",
        "line": ["534 Erewhon St"],
        "city": "PleasantVille",
        "district": "Rainbow",
        "state": "Vic",
        "postalCode": "3999"
      },
      "gender": "female",
      "period": { "start": "2012" }
    }
  ],
  "managingOrganization": {
    "reference": "Organization/1"
  }
}
```

---

## Sample 2: India ABDM/ABHA Profile

```json
{
  "resourceType": "Patient",
  "id": "example-01",
  "meta": {
    "versionId": "1",
    "lastUpdated": "2020-07-09T14:58:58.181+05:30",
    "profile": [
      "https://nrces.in/ndhm/fhir/r4/StructureDefinition/Patient"
    ]
  },
  "identifier": [
    {
      "type": {
        "coding": [
          {
            "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
            "code": "MR",
            "display": "Medical record number"
          }
        ]
      },
      "system": "https://healthid.ndhm.gov.in",
      "value": "22-7225-4829-5255"
    }
  ],
  "name": [
    { "text": "ABC" }
  ],
  "telecom": [
    { "system": "phone", "value": "+919818512600", "use": "home" }
  ],
  "gender": "male",
  "birthDate": "1981-01-12"
}
```

---

## Field-by-Field Comparison

| Field | International (HL7 Base) | India (ABDM/ABHA) |
|-------|------------------------|--------------------|
| **meta.profile** | Not set (uses base FHIR) | `https://nrces.in/ndhm/fhir/r4/StructureDefinition/Patient` |
| **identifier.system** | Generic OID (`urn:oid:1.2.36...`) | ABHA Health ID (`https://healthid.ndhm.gov.in`) |
| **identifier.period** | Present (validity period) | Not present |
| **identifier.assigner** | Present (issuing org) | Not present |
| **active** | Present | Not present |
| **name** | Multiple entries with `use` (official, usual, maiden), structured `family` + `given` | Single entry with `text` only (unstructured) |
| **telecom** | Multiple entries with `rank` ordering and `period` for old numbers | Single phone entry |
| **birthDate extensions** | `patient-birthTime` extension for exact birth time | Not present |
| **deceasedBoolean** | Present | Not present |
| **address** | Full structured address with `type`, `text`, `district`, `period` | Not present in example |
| **contact (Next-of-Kin)** | Full NOK with relationship, name, telecom, address, gender, period | Not present |
| **managingOrganization** | Present (reference to Organization) | Not present |

---

## Trade-off Analysis: Choosing One Over the Other

### If You Pick International (HL7 Base) Only

**Advantages:**
- Maximum interoperability with global FHIR systems (EHRs, HIEs, research platforms worldwide)
- Richest data model — supports multiple names, contact history, next-of-kin, address periods
- No profile-specific validation constraints — accepts the broadest range of data
- Easier integration with international health corridors and cross-border health data exchange

**Disadvantages:**
- Does not enforce ABDM-mandated fields — may produce records that fail ABDM validation
- No ABHA number semantics — the adapter won't know how to handle `https://healthid.ndhm.gov.in` identifiers specially
- Missing India-specific extensions that ABDM systems expect (e.g., Aadhaar masking rules, ABHA address format)
- Cannot participate in ABDM health record linking without additional ABDM-specific handling
- International clients may send data in formats (name structure, phone format) that don't map cleanly to Indian backend systems

### If You Pick India ABDM/ABHA Only

**Advantages:**
- Direct compliance with ABDM/NDHM regulations
- ABHA number is first-class — identifier system is well-defined (`https://healthid.ndhm.gov.in`)
- Simpler model — fewer optional fields to handle, less mapping complexity
- Aligns with Indian health ecosystem (ABHA, Aadhaar, state health IDs)

**Disadvantages:**
- Not interoperable with international FHIR systems out of the box
- Very minimal profile — the ABDM example doesn't use structured names (`family`/`given`), only `text`
- No support for rich demographics (multiple names, contact history, next-of-kin)
- No address in the standard example — would need to be added as an extension or custom mapping
- Name as unstructured `text` means no ability to search by family name or given name separately
- Locked into Indian health ecosystem — expanding to other countries requires a second profile

### If You Support Both (Recommended)

**Advantages:**
- Accepts data from both international and Indian FHIR clients
- Can validate against ABDM profile when the `meta.profile` indicates it
- Maximizes reuse — international fields map to eGov where possible, ABDM-specific fields get special handling
- Future-proof for cross-border health initiatives

**Disadvantages:**
- More complex mapping configuration — need to handle both structured and unstructured names
- Need conditional logic for identifier systems (ABHA vs generic MR)
- Testing surface area increases — must verify both profiles produce correct eGov requests
- Privacy rules differ (Aadhaar masking applies to ABDM, not international)

---

## Recommendation for FHIR Adapter

The adapter should support **both profiles** through its config-driven architecture:

1. **Base field mappings** cover the HL7 standard fields (`name.family`, `name.given`, `telecom`, `address`, etc.)
2. **Identifier mappings** already handle multiple systems (ABHA, Aadhaar, PAN) — international identifiers just use different `system` URIs
3. **Profile detection** via `meta.profile` can drive conditional behavior if needed
4. **Name handling** should accept both structured (`family` + `given`) and unstructured (`text`) formats

This approach means international FHIR clients and ABDM-compliant Indian systems can both use the same adapter endpoint, with the mapping config handling the differences transparently.

---

## Patient ↔ Individual — Unmappable Fields

The following FHIR Patient fields were identified by comparing the [HL7 international Patient example](https://hl7.org/fhir/patient-example.json.html) against the eGov Individual model. These fields have **no equivalent in eGov** and cannot be mapped without backend changes. Data sent in these fields will be silently dropped on create/update and absent from search responses.

| # | FHIR Field | Description | Why It Can't Be Mapped |
|---|-----------|-------------|----------------------|
| 1 | Multiple `name[]` entries | FHIR supports official, usual, maiden name variants simultaneously | eGov has a single `name` object (`givenName`, `familyName`, `otherNames`). Only the first entry is mapped; additional names are lost. |
| 2 | `telecom[].rank` | Priority ordering of contact methods (1 = preferred) | eGov has flat `mobileNumber`, `altContactNumber`, `email` — no ranking concept. |
| 3 | `telecom[].period` | Validity period for a contact (e.g., old phone valid until 2014) | eGov doesn't track when a contact method was active or expired. |
| 4 | `address[].type` | Whether address is postal, physical, or both | eGov `address.type` is used for PERMANENT/CORRESPONDENCE, not the postal/physical distinction. |
| 5 | `address[].period` | When the patient lived at this address | eGov doesn't track address validity periods. |
| 6 | `identifier[].period` | Validity period of an identifier (e.g., MRN valid since 2001) | eGov identifiers have no temporal validity tracking. |
| 7 | `identifier[].assigner` | Organization that issued the identifier | eGov doesn't track which organization assigned an identifier. |
| 8 | `deceasedBoolean` | Whether the patient is deceased | eGov has `isDeleted` (soft-delete flag). Mapping deceased to deleted would be semantically incorrect. |
| 9 | `contact[]` (Next-of-Kin) | Structured contact with relationship, name, phone, address, gender | eGov has flat `fatherName`, `husbandName` strings — no structured relationship or contact demographics. |
| 10 | `managingOrganization` | Reference to responsible Organization resource | eGov uses `tenantId` for org context, but it's not a FHIR Organization reference. |

### Impact

- **Data loss on inbound**: FHIR clients submitting rich Patient resources will have these fields silently discarded. A subsequent read returns less data than was submitted.
- **Incomplete outbound**: Search responses will never include these fields since eGov doesn't store them.
- **Spec compliance**: The adapter produces valid but minimal FHIR Patient resources. Clients expecting full demographics may find responses insufficient.

### Potential Solutions

1. **eGov `additionalFields`**: The Individual model supports an `additionalFields` map. Unmappable FHIR fields could be serialized into it as JSON, preserving data round-trip. However, these fields would not be searchable in eGov.
2. **eGov Model Extension**: Add new fields to the Individual model (e.g., `deceasedDate`, `contactPersons[]`) to support the most needed FHIR fields natively.
3. **Separate FHIR Store**: For full FHIR compliance, store the complete resource in a FHIR-native database (e.g., HAPI FHIR server) alongside eGov for operational fields.

---

## Practitioner ↔ Individual — Mapping Analysis

The FHIR [Practitioner](http://hl7.org/fhir/practitioner.html) resource represents healthcare workers. In the eGov/DIGIT context, practitioners are also stored as **Individual** records (same model as Patient), since health campaign workers (registrars, distributors, supervisors) are registered as Individuals.

Reference: [HL7 Practitioner example](https://hl7.org/fhir/practitioner-example.json.html)

### Sample FHIR Practitioner (International)

```json
{
  "resourceType": "Practitioner",
  "id": "example",
  "identifier": [
    {
      "system": "http://www.acme.org/practitioners",
      "value": "23"
    }
  ],
  "active": true,
  "name": [
    {
      "family": "Careful",
      "given": ["Adam"],
      "prefix": ["Dr"]
    }
  ],
  "address": [
    {
      "use": "home",
      "line": ["534 Erewhon St"],
      "city": "PleasantVille",
      "state": "Vic",
      "postalCode": "3999"
    }
  ],
  "qualification": [
    {
      "identifier": [
        {
          "system": "http://example.org/UniversityIdentifier",
          "value": "12345"
        }
      ],
      "code": {
        "coding": [
          {
            "system": "http://terminology.hl7.org/CodeSystem/v2-0360/2.7",
            "code": "BS",
            "display": "Bachelor of Science"
          }
        ],
        "text": "Bachelor of Science"
      },
      "period": { "start": "1995" },
      "issuer": { "display": "Example University" }
    }
  ]
}
```

### Mappable Fields (Practitioner → Individual)

These fields have a direct or transform-based mapping to eGov Individual, reusing the same config pattern as Patient:

| FHIR Practitioner Field | eGov Individual Field | Transform | Notes |
|------------------------|----------------------|-----------|-------|
| `id` | `id` | Direct | Same as Patient |
| `active` | `isDeleted` | `negate` | Same as Patient |
| `name[0].given` | `name.givenName` | Direct (array) | Same as Patient |
| `name[0].family` | `name.familyName` | Direct | Same as Patient |
| `gender` | `gender` | `codeMap` | Same as Patient |
| `birthDate` | `dateOfBirth` | `epochToDate` | Same as Patient |
| `telecom` (phone) | `mobileNumber` | `telecomEntry` | Same as Patient |
| `telecom` (email) | `email` | `telecomEntry` | Same as Patient |
| `address` | `address` | Nested array | Same as Patient |
| `identifier` | `individualId` / `identifiers` | identifierMappings | Same mechanism, different systems |

### Unmappable Fields

| # | FHIR Field | Description | Why It Can't Be Mapped |
|---|-----------|-------------|----------------------|
| 1 | `qualification[]` | Degrees, certifications, licenses with codes, periods, and issuers | eGov Individual has no qualification/certification model. This is the **biggest gap** — practitioner credentials (medical license, nursing degree) have no storage in eGov. |
| 2 | `qualification[].identifier` | License/registration numbers (e.g., medical council registration) | No equivalent. eGov `identifiers` are person-level IDs (ABHA, Aadhaar), not professional credentials. |
| 3 | `qualification[].period` | When the qualification was obtained or is valid | No temporal tracking for qualifications in eGov. |
| 4 | `qualification[].issuer` | Organization that granted the qualification | No reference to issuing institution in eGov. |
| 5 | `name[].prefix` | Professional title (Dr., Prof., etc.) | eGov `name` has `givenName`, `familyName`, `otherNames` — no prefix/suffix. |
| 6 | `communication[]` | Languages the practitioner speaks for patient communication | Not present in eGov Individual model. |
| 7 | `photo` (Attachment) | Photo with full FHIR Attachment metadata (contentType, url, hash) | eGov has a simple `photo` field but not the full Attachment structure. |

### Key Difference from Patient Mapping

The Patient and Practitioner resources share most demographic fields (name, telecom, address, gender, birthDate). The critical difference is:

- **Patient** has: `contact[]` (next-of-kin), `deceasedBoolean`, `maritalStatus`, `managingOrganization`
- **Practitioner** has: `qualification[]`, `communication[]`

Since both map to the same eGov Individual model, the **adapter config can reuse ~90% of the Patient mapping**. The Practitioner-specific config would only need:

1. Different `fhirResource`: `"Practitioner"` instead of `"Patient"`
2. Different identifier systems (professional registrations instead of ABHA/Aadhaar)
3. `qualification[]` handling — either via `additionalFields` or a future eGov model extension

### Recommended Config Approach

Create a separate mapping file `practitioner-individual-mapping.json` that:
- Copies the Patient field mappings for shared demographics
- Uses different identifier systems (medical council registration, facility ID)
- Documents `qualification[]` as unsupported until eGov model is extended

---

## Pending Mapping — Patient ↔ Individual

### `contact[]` → `fatherName` / `husbandName` (Next-of-Kin)

**FHIR expects** a structured `contact[]` array:

```json
"contact": [
  {
    "relationship": [
      {
        "coding": [
          { "system": "http://terminology.hl7.org/CodeSystem/v2-0131", "code": "N", "display": "Next-of-Kin" }
        ]
      }
    ],
    "name": { "text": "Ramesh Kumar" },
    "telecom": [{ "system": "phone", "value": "9876543210" }],
    "gender": "male"
  }
]
```

**eGov has** flat string fields: `fatherName`, `husbandName` — name only, no phone/address/gender.

**Proposed config:**

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

**Transform behavior:**

- **toFhir (search)**: Takes `fatherName` = `"Ramesh Kumar"` → builds `{"relationship": [{"coding": [{"system": "...", "code": "N"}]}], "name": {"text": "Ramesh Kumar"}}`
- **toEgov (create)**: Takes `contact[]` array → finds entry matching relationship code `"N"` → extracts `name.text` → sets as `fatherName`

**Known limitations (lossy mapping):**

- **FHIR→eGov**: If a client sends a contact with phone, address, gender — only the name is stored. Everything else is lost.
- **eGov→FHIR**: Response will have contacts with just a name — no phone, address, or gender.

**Code required:** New `contactEntry` case in `applyTransform` + array accumulation logic (same pattern as `telecomEntry`).

**Status:** Deferred — to be implemented when `fatherName`/`husbandName` support is needed.

---

> **Note:** Gap analysis for other resource mappings (Organization, Location, etc.) will be added as those mappings are developed.
