# ProjectTask ↔ FHIR Task — Missing & Future Fields

This document covers the mapping between eGov ProjectTask (medicine delivery) and FHIR Task resource.

---

> **IMPORTANT — TaskResource (medicine items) Mapping:**
> Each eGov Task contains a `resources[]` array of `TaskResource` objects (what medicine, how many doses, whether delivered). These map to FHIR `Task.input[]` (planned) and `Task.output[]` (actual). However, the current adapter's field mapping engine handles flat fields and simple arrays — it doesn't yet support mapping a nested object array into separate `input[]`/`output[]` entries with typed values. This requires a composite mapping similar to HouseholdMember → Group.member[].
>
> Until implemented, TaskResource data will not appear in the FHIR Task response.

---

## 1. TaskResource → Task.input[] / Task.output[] (Not Yet Implemented)

Each TaskResource would become entries in both input and output:

### Proposed input[] Mapping (what was planned)

```json
"input": [
  {
    "type": {
      "coding": [{ "system": "urn:egov:product-variant", "code": "<productVariantId>" }],
      "text": "Medicine delivery"
    },
    "valueQuantity": {
      "value": <quantity>,
      "unit": "dose"
    }
  }
]
```

### Proposed output[] Mapping (what was delivered)

```json
"output": [
  {
    "type": {
      "coding": [{ "system": "urn:egov:product-variant", "code": "<productVariantId>" }],
      "text": "Delivery result"
    },
    "valueBoolean": <isDelivered>
  },
  {
    "type": { "text": "Delivery comment" },
    "valueString": "<deliveryComment>"
  }
]
```

### Code Changes Required

1. New transform type (e.g., `taskResourceToInput`) or a composite array mapper
2. The transform needs to iterate `resources[]` and produce multiple `input[]`/`output[]` entries
3. Reverse direction: parse `input[]`/`output[]` back to `TaskResource` objects on create

---

## 2. Status Mapping

### eGov TaskStatus → FHIR Task.status

| eGov TaskStatus | FHIR status | FHIR businessStatus.text |
|----------------|-------------|-------------------------|
| `DELIVERED` | `completed` | `DELIVERED` |
| `ADMINISTRATION_SUCCESS` | `completed` | `ADMINISTRATION_SUCCESS` |
| `BENEFICIARY_REFUSED` | `rejected` | `BENEFICIARY_REFUSED` |
| `NOT_ADMINISTERED` | `ready` | `NOT_ADMINISTERED` |
| `ADMINISTRATION_FAILED` | `failed` | `ADMINISTRATION_FAILED` |
| `CLOSED_HOUSEHOLD` | `cancelled` | `CLOSED_HOUSEHOLD` |

Note: `DELIVERED` and `ADMINISTRATION_SUCCESS` both map to `completed`. The original eGov status is preserved in `businessStatus.text` for full fidelity. On reverse mapping, `completed` maps to `DELIVERED` by default.

---

## 3. Reference Fields

| FHIR Field | eGov Field | FHIR Format | Notes |
|-----------|-----------|-------------|-------|
| `Task.for` | `projectBeneficiaryId` | `"Patient/{id}"` | Currently stored as plain ID. Should be a FHIR reference with resource type prefix. Needs `prefixRef` transform. |
| `Task.basedOn[0]` | `projectId` | `"Project/{id}"` | Project is not a standard FHIR resource. Could use a custom reference or map to `PlanDefinition`. |
| `Task.requester` | `createdBy` | `"Practitioner/{id}"` | User UUID stored as extension for now. |

---

## 4. Unmappable eGov Task Fields

| eGov Field | Why | Solution |
|-----------|-----|---------|
| `resources[]` (TaskResource array) | Needs composite input/output mapping | Extension per resource, or implement composite mapper |
| `resources[].productVariantId` | No direct FHIR field — goes in input[].type | Needs composite mapper |
| `resources[].quantity` | No direct FHIR field — goes in input[].valueQuantity | Needs composite mapper |
| `resources[].isDelivered` | No direct FHIR field — goes in output[].valueBoolean | Needs composite mapper |
| `resources[].deliveryComment` | No direct FHIR field — goes in output[].valueString | Needs composite mapper |
| `address` (full object) | FHIR Task uses `location` as Reference, not inline address | Extension (mapped as individual fields) |
| `clientReferenceId` | Offline-specific | Extension |
| `clientAuditDetails` | Offline-specific | Extension |
| `applicationId` | eGov-specific | Extension |
| `hasErrors` | eGov-specific | Extension |
| `source` | eGov-specific | Extension or meta.source |

---

## 5. Unmappable FHIR Task Fields

| FHIR Field | Description | Why Not Used |
|-----------|-------------|-------------|
| `encounter` | Associated healthcare event | No encounter model in eGov |
| `insurance` | Coverage/pre-authorization | No insurance model in eGov |
| `note` | Lifecycle comments | No comment history in eGov Task |
| `relevantHistory` | Provenance references | No provenance tracking |
| `restriction` | Fulfillment constraints | No constraints model in eGov |
| `priority` | Urgency level | Not tracked in eGov Task |
| `doNotPerform` | Prohibit action | Not tracked in eGov Task |
| `focus` | Resource being manipulated | eGov uses resources[] instead |
| `requestedPerformer` | Intended performer | Not tracked in eGov Task |
| `owner` | Party managing execution | Not tracked separately from createdBy |

---

## 6. How to Add TaskResource Mapping (Future)

When the composite mapper is implemented, add to the config:

```json
{
  "fhirField": "input",
  "egovField": "resources",
  "transform": "taskResourceToInput",
  "transformConfig": {
    "productVariantField": "productVariantId",
    "quantityField": "quantity",
    "productVariantSystem": "urn:egov:product-variant"
  }
},
{
  "fhirField": "output",
  "egovField": "resources",
  "transform": "taskResourceToOutput",
  "transformConfig": {
    "deliveredField": "isDelivered",
    "commentField": "deliveryComment",
    "productVariantField": "productVariantId",
    "productVariantSystem": "urn:egov:product-variant"
  }
}
```

---

## 7. Common FHIR Task Identifier Codes

| Code | Display | Use Case |
|------|---------|----------|
| `PLAC` | Placer identifier | Task ID from requesting system |
| `FILL` | Filler identifier | Task ID from performing system |
| `SNO` | Serial number | Sequential task number |
