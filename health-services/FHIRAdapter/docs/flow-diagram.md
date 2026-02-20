# DIGIT-to-FHIR Adapter — Flow Diagram

```
                        FHIR ADAPTER SERVICE
 FHIR Standard          (Spring Boot)                    DIGIT Platform
 (HL7 R4)               Config-Driven                   (eGov APIs)
─────────────           ─────────────────                ──────────────

                    ┌─────────────────────┐
                    │                     │
                    │   JSON Config Files │
                    │   (mappings/*.json) │
                    │                     │
                    │  ┌───────────────┐  │
                    │  │ field mappings│  │
                    │  │ id mappings   │  │
                    │  │ transforms    │  │
                    │  │ api endpoints │  │
                    │  │ search params │  │
                    │  └───────┬───────┘  │
                    │          │          │
                    │     auto-load       │
                    │     on startup      │
                    └──────────┼──────────┘
                               │
                               ▼
┌─────────────┐    ┌───────────────────────┐    ┌──────────────────────┐
│             │    │                       │    │                      │
│  FHIR       │    │   MappingConfigLoader │    │   DIGIT Backend      │
│  Client     │    │                       │    │   (unified-qa)       │
│             │    │  Loads all *.json     │    │                      │
│             │    │  from mappings/       │    │                      │
│             │    │  at @PostConstruct    │    │                      │
│             │    │                       │    │                      │
└──────┬──────┘    └───────────┬───────────┘    └──────────────────────┘
       │                       │
       │                       ▼
       │           ┌───────────────────────┐
       │           │  Map<String,Config>   │
       │           │                       │
       │           │  "patient" → {...}    │
       │           │  "practitioner"→{...} │
       │           │  "organization"→{...} │
       │           │  (add more via JSON!) │
       │           └───────────┬───────────┘
       │                       │
       ▼                       ▼
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│                        FhirController                                │
│                                                                      │
│   GET  /{resourceType}/{id}     →  read()                           │
│   GET  /{resourceType}?params   →  search()                        │
│   POST /{resourceType}          →  create()                         │
│   PUT  /{resourceType}/{id}     →  update()                         │
│   DELETE /{resourceType}/{id}   →  delete()                         │
│                                                                      │
│   Resolves resourceType ("Patient") → MappingConfig                 │
│                                                                      │
└────────────────────────────┬─────────────────────────────────────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
┌─────────────────┐ ┌──────────────┐ ┌──────────────────┐
│ FHIR → eGov     │ │Search Params │ │ eGov → FHIR      │
│ (create/update)  │ │ → eGov       │ │ (response)       │
└────────┬────────┘ └──────┬───────┘ └────────▲─────────┘
         │                 │                   │
         ▼                 ▼                   │
┌──────────────────────────────────────────────┴───────────────────────┐
│                                                                      │
│                       TransformService                               │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │                    fhirToEgov()                                │  │
│  │                                                                │  │
│  │  FHIR Patient               Transforms          DIGIT Individual│
│  │  ─────────────              ──────────           ───────────────│  │
│  │  name[0].family  ─────────► codeMap    ────────► name.familyName│  │
│  │  name[0].given   ─────────► (direct)   ────────► name.givenName │  │
│  │  gender:"male"   ─────────► codeMap    ────────► gender:"MALE"  │  │
│  │  birthDate       ─────────► dateToEpoch────────► dateOfBirth    │  │
│  │  active:true     ─────────► negate     ────────► isDeleted:false│  │
│  │  address[]       ─────────► nested     ────────► address[]      │  │
│  │  telecom[phone]  ─────────► extract    ────────► mobileNumber   │  │
│  │  identifier[]    ─────────► idMapping  ────────► identifiers[]  │  │
│  │                                                                │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │                    egovToFhir()                                │  │
│  │                                                                │  │
│  │  DIGIT Individual            Transforms          FHIR Patient  │  │
│  │  ───────────────             ──────────           ──────────── │  │
│  │  name.familyName ─────────► (direct)   ────────► name[0].family│  │
│  │  name.givenName  ─────────► (direct)   ────────► name[0].given │  │
│  │  gender:"MALE"   ─────────► codeMap    ────────► gender:"male" │  │
│  │  dateOfBirth     ─────────► epochToDate────────► birthDate     │  │
│  │  isDeleted:false ─────────► negate     ────────► active:true   │  │
│  │  address[]       ─────────► nested     ────────► address[]     │  │
│  │  mobileNumber    ─────────► wrap       ────────► telecom[phone]│  │
│  │  identifiers[]   ─────────► idMapping  ────────► identifier[]  │  │
│  │                                                                │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │                 searchParamsToEgov()                           │  │
│  │                                                                │  │
│  │  FHIR Search Params          Transforms          DIGIT Search  │  │
│  │  ──────────────────          ──────────           ──────────── │  │
│  │  _id              ─────────► (direct)  ────────► id            │  │
│  │  name             ─────────► (direct)  ────────► name.givenName│  │
│  │  birthdate        ─────────► dateToEpoch───────► dateOfBirth   │  │
│  │  gender           ─────────► codeMap   ────────► gender        │  │
│  │  phone            ─────────► (direct)  ────────► mobileNumber  │  │
│  │  _tag             ─────────► extract   ────────► tenantId      │  │
│  │                                                                │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
└──────────────────────────────┬───────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│                        EgovApiService                                │
│                                                                      │
│   Constructs URL from config:                                        │
│     base: https://unified-qa.digit.org                              │
│     + endpoint from apiMapping                                       │
│     + ?tenantId=mz&limit=10&offset=0                                │
│                                                                      │
│   Sets headers from config                                           │
│   Makes HTTP call (POST/GET) via RestTemplate                        │
│                                                                      │
└──────────────────────────────┬───────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│                       DIGIT Platform APIs                            │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  /health-individual/v1/                                      │    │
│  │    _create   _search   _update   _delete                    │    │
│  │                                                              │    │
│  │  Model: Individual                                           │    │
│  │  { id, individualId, name{givenName,familyName},             │    │
│  │    gender, dateOfBirth, mobileNumber, address[],             │    │
│  │    identifiers[], tenantId, auditDetails }                   │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  /health-worker/v1/         (add via JSON config)            │    │
│  │    _create   _search   _update   _delete                    │    │
│  │                                                              │    │
│  │  Model: HealthWorker                                         │    │
│  │  { id, surname, givenName, qualifications[],                 │    │
│  │    nmcNumber, tenantId }                                     │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  /facility/v1/              (add via JSON config)            │    │
│  │    _create   _search   _update   _delete                    │    │
│  │                                                              │    │
│  │  Model: Facility                                             │    │
│  │  { id, name, type, address, tenantId }                      │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  ... more APIs added via JSON config ...                     │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## End-to-End Request Flow

```
   FHIR Client                FHIR Adapter                    DIGIT Backend
   ───────────                ────────────                    ─────────────
       │                          │                               │
       │  POST /fhir/Patient      │                               │
       │  { "resourceType":       │                               │
       │    "Patient",            │                               │
       │    "gender":"male" }     │                               │
       │─────────────────────────►│                               │
       │                          │                               │
       │                  ┌───────┴───────┐                       │
       │                  │ Load config   │                       │
       │                  │ for "Patient" │                       │
       │                  └───────┬───────┘                       │
       │                          │                               │
       │                  ┌───────┴────────┐                      │
       │                  │ fhirToEgov()   │                      │
       │                  │                │                      │
       │                  │ "male"→"MALE"  │                      │
       │                  │ date→epoch     │                      │
       │                  │ wrap in        │                      │
       │                  │ RequestInfo +  │                      │
       │                  │ Individuals[]  │                      │
       │                  └───────┬────────┘                      │
       │                          │                               │
       │                          │  POST /health-individual/     │
       │                          │       v1/_create              │
       │                          │  { "RequestInfo":{},          │
       │                          │    "Individuals":[{           │
       │                          │      "gender":"MALE",...}] }  │
       │                          │──────────────────────────────►│
       │                          │                               │
       │                          │  200 OK                       │
       │                          │  { "Individuals":[{           │
       │                          │      "id":"abc-123",          │
       │                          │      "gender":"MALE",...}] }  │
       │                          │◄──────────────────────────────│
       │                          │                               │
       │                  ┌───────┴────────┐                      │
       │                  │ egovToFhir()   │                      │
       │                  │                │                      │
       │                  │ "MALE"→"male"  │                      │
       │                  │ epoch→date     │                      │
       │                  │ add identifiers│                      │
       │                  │ set resourceType│                     │
       │                  └───────┬────────┘                      │
       │                          │                               │
       │  201 Created             │                               │
       │  { "resourceType":       │                               │
       │    "Patient",            │                               │
       │    "id":"abc-123",       │                               │
       │    "gender":"male" }     │                               │
       │◄─────────────────────────│                               │
       │                          │                               │
```

---

## Adding a New API via JSON Config (No Code Changes)

```
 Step 1: Create JSON mapping file
 ─────────────────────────────────

 mappings/healthworker-practitioner-mapping.json
 ┌────────────────────────────────────────────────────────┐
 │ {                                                      │
 │   "fhirResource": "Practitioner",                     │
 │   "egovModel": "HealthWorker",                        │
 │                                                        │
 │   "apiMapping": {                                      │
 │     "create": {                                        │
 │       "endpoint": "/health-worker/v1/_create",         │
 │       "method": "POST"                                │
 │     },                                                 │
 │     "search": {                                        │
 │       "endpoint": "/health-worker/v1/_search",         │
 │       "method": "POST"                                │
 │     }                                                  │
 │   },                                                   │
 │                                                        │
 │   "fieldMappings": [                                   │
 │     { "fhirField": "name[0].family",                  │
 │       "egovField": "surname" },                        │
 │     { "fhirField": "qualification[0]...",             │
 │       "egovField": "qualifications[0].degree",         │
 │       "transform": "codeMap" }                         │
 │   ]                                                    │
 │ }                                                      │
 └────────────────────────────────────────────────────────┘

 Step 2: Restart → MappingConfigLoader auto-discovers
 ─────────────────────────────────────────────────────

 ┌──────────────┐     ┌───────────────────┐     ┌──────────────────┐
 │  individual- │     │  healthworker-    │     │  facility-       │
 │  patient-    │     │  practitioner-    │     │  location-       │
 │  mapping.json│     │  mapping.json     │     │  mapping.json    │
 └──────┬───────┘     └────────┬──────────┘     └────────┬─────────┘
        │                      │                          │
        └──────────┬───────────┴──────────────────────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │ MappingConfigLoader  │
        │                      │
        │ classpath:mappings/  │
        │       *.json         │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │ Config Registry      │
        │                      │
        │ "patient"      → {} │
        │ "practitioner" → {} │  ← NEW!
        │ "location"     → {} │  ← NEW!
        └──────────────────────┘

 Step 3: Endpoints automatically available
 ──────────────────────────────────────────

 Existing:                        New (no code changes):
 GET  /fhir/Patient/{id}         GET  /fhir/Practitioner/{id}
 GET  /fhir/Patient?name=...     GET  /fhir/Practitioner?name=...
 POST /fhir/Patient              POST /fhir/Practitioner
 PUT  /fhir/Patient/{id}         PUT  /fhir/Practitioner/{id}
 DEL  /fhir/Patient/{id}         DEL  /fhir/Practitioner/{id}
                                  GET  /fhir/Location/{id}
                                  ...
```

---

## Component Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      FHIR Adapter Service                       │
│                                                                 │
│  ┌─────────────────┐   ┌──────────────────┐   ┌─────────────┐ │
│  │  FhirController │──►│ TransformService  │──►│EgovApiService│ │
│  │                 │   │                  │   │             │ │
│  │ Routes requests │   │ Bidirectional    │   │ HTTP client │ │
│  │ by resourceType │   │ FHIR ↔ eGov     │   │ to DIGIT    │ │
│  │                 │   │ transformation   │   │ backend     │ │
│  └────────┬────────┘   └────────┬─────────┘   └──────┬──────┘ │
│           │                     │                     │        │
│           │            ┌────────┴─────────┐           │        │
│           │            │MappingConfigLoader│           │        │
│           │            │                  │           │        │
│           │            │ Reads *.json at  │           │        │
│           │            │ startup, builds  │           │        │
│           │            │ config registry  │           │        │
│           │            └────────┬─────────┘           │        │
│           │                     │                     │        │
│           │            ┌────────┴─────────┐           │        │
│           │            │  MappingConfig    │           │        │
│           │            │                  │           │        │
│           │            │ • apiMapping     │           │        │
│           │            │ • fieldMappings  │           │        │
│           │            │ • identifierMap  │           │        │
│           │            │ • searchParams   │           │        │
│           │            │ • request/resp   │           │        │
│           │            │   models         │           │        │
│           │            └──────────────────┘           │        │
│           │                                           │        │
│  ┌────────┴──────────────────────────────────┐        │        │
│  │       GlobalExceptionHandler              │        │        │
│  │  Converts errors → FHIR OperationOutcome  │        │        │
│  └───────────────────────────────────────────┘        │        │
│                                                       │        │
└───────────────────────────────────────────────────────┼────────┘
                                                        │
                                                        ▼
                                              DIGIT Platform APIs
```

---

## Supported Transforms

```
┌─────────────────────────────────────────────────────────┐
│                  Transform Pipeline                      │
│                                                          │
│  ┌──────────┐   ┌──────────┐   ┌───────────────────┐   │
│  │ codeMap  │   │epochToDate│   │ dateToEpoch       │   │
│  │          │   │          │   │                   │   │
│  │ MALE ↔   │   │ 63521280 │   │ "1990-02-17"     │   │
│  │  male    │   │ 0000 →   │   │  → 635212800000  │   │
│  │ FEMALE ↔ │   │ "1990-   │   │                   │   │
│  │  female  │   │  02-17"  │   │                   │   │
│  └──────────┘   └──────────┘   └───────────────────┘   │
│                                                          │
│  ┌──────────┐   ┌──────────┐                            │
│  │ negate   │   │epochToIso│                            │
│  │          │   │          │                            │
│  │ true ↔   │   │ epoch →  │                            │
│  │  false   │   │ ISO-8601 │                            │
│  │          │   │ instant  │                            │
│  └──────────┘   └──────────┘                            │
│                                                          │
└─────────────────────────────────────────────────────────┘
```
