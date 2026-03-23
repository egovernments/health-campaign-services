# FHIR Adapter — Code Review

**Date:** 2026-03-18
**Scope:** Full codebase review of `/health-services/fhiradapter`
**Branch:** `fhiradapter`

---

## Overall Architecture

The config-driven design is solid — adding new FHIR resource mappings without code changes is the right approach. The service is compact (~7 files, ~750 lines of Java) and easy to follow. That said, there are significant issues across security, reliability, and code quality that need to be addressed before production use.

### Component Summary

| File | Lines | Purpose |
|------|-------|---------|
| `FhirAdapterApplication.java` | 11 | Spring Boot entry point |
| `MappingConfig.java` | 69 | Data models for JSON mapping configs |
| `MappingConfigLoader.java` | 42 | Loads mapping configs from classpath at startup |
| `FhirController.java` | 135 | REST API — FHIR CRUD + search endpoints |
| `GlobalExceptionHandler.java` | 42 | Exception → FHIR OperationOutcome |
| `EgovApiService.java` | 90 | HTTP client for eGov/DIGIT APIs |
| `TransformService.java` | 353 | Bidirectional FHIR ↔ eGov transformation |

---

## Critical Issues

### 1. RestTemplate instantiated directly, bypasses Spring DI

**File:** `EgovApiService.java:22-23`

```java
private final RestTemplate restTemplate = new RestTemplate();
private final ObjectMapper objectMapper = new ObjectMapper();
```

These are `new`'d inline, which means:

- You can't configure timeouts (connect/read) — a hung backend will hang this service forever.
- You can't add interceptors (retry, circuit breaker, metrics).
- `@RequiredArgsConstructor` can't inject them, and they conflict with Lombok's constructor generation since they're initialized inline with `final`.
- The `ObjectMapper` duplicates Spring's auto-configured one.

**Fix:** Define a `RestTemplate` `@Bean` with timeouts in a configuration class and inject both via constructor.

---

### 2. No HTTP timeouts anywhere

**File:** `EgovApiService.java:75-80`

The `restTemplate.exchange()` call has no connect or read timeout. A single slow eGov backend call will hold a thread indefinitely, eventually exhausting the Tomcat thread pool under load.

**Fix:** Configure `RestTemplate` with `SimpleClientHttpRequestFactory` or `HttpComponentsClientHttpRequestFactory` setting connect timeout (~5s) and read timeout (~30s).

---

### 3. Request body mutation

**File:** `EgovApiService.java:42,46`

```java
requestBody.remove("limit");
requestBody.remove("offset");
```

This mutates the caller's map. If `Map.of()` or any other immutable map is passed, this throws `UnsupportedOperationException`. This is a latent bug — it works today only because `HashMap` is used upstream.

**Fix:** Copy the map at the start of `callApi`, or extract limit/offset before adding them to the map upstream.

---

### 4. Sensitive data logged at INFO level

**File:** `EgovApiService.java:56,70,83`

Full request and response bodies — including `authToken`, patient names, Aadhaar numbers, ABHA numbers — are logged at `INFO`. In production this is a PII/PHI compliance violation.

**Fix:** Change to `DEBUG` level at minimum. Ideally, implement a log sanitizer that redacts sensitive fields (`authToken`, `identifierId`, `mobileNumber`, etc.) before logging.

---

### 5. Auth token passed as custom header, not Authorization

**File:** `FhirController.java:27`

```java
@RequestHeader(value = "auth_token", required = false) String authToken
```

Using a non-standard header name bypasses all standard HTTP security tooling (API gateways, OAuth proxies, browser security). FHIR clients expect to use the standard `Authorization: Bearer <token>` header.

**Fix:** Accept `Authorization` header, parse the Bearer token, and pass it downstream.

---

### 6. No input validation on FHIR resources

**File:** `FhirController.java:68`

```java
@RequestBody Map<String, Object> fhirResource
```

The endpoint accepts any JSON. There is no validation that:

- `resourceType` in the body matches the URL path variable.
- Required fields are present.
- Payload size is within acceptable limits.

A malformed or malicious payload passes straight through to the eGov API.

**Fix:** At minimum, validate that `resourceType` matches the path variable. Consider adding a FHIR validation layer or at least size limits.

---

## Design Issues

### 7. egovToFhir reverse mapping is incomplete

**File:** `TransformService.java:104-110`

The forward path (`fhirToEgov`, lines 30-71) handles:
- Nested array mappings (e.g., address)
- Array-to-scalar conversion
- Nested field mappings within arrays

The reverse path (`egovToFhir`, lines 104-110) does none of this — it only does flat field mapping. This means an `address` array from eGov won't map back to FHIR's nested `address[].line[]` structure correctly.

**Fix:** Mirror the nested/array handling logic from `fhirToEgov` into `egovToFhir`.

---

### 8. setValueByPath doesn't handle arrays

**File:** `TransformService.java:289-299`

```java
private void setValueByPath(Map<String, Object> obj, String path, Object value) {
    String[] parts = path.split("\\.");
    Map<String, Object> current = obj;
    for (int i = 0; i < parts.length - 1; i++) {
        String part = parts[i];
        current.computeIfAbsent(part, k -> new HashMap<String, Object>());
        current = (Map<String, Object>) current.get(part);
    }
    current.put(parts[parts.length - 1], value);
}
```

It only creates nested `HashMap`s. Paths like `name[0].given` in set direction will create a map key literally named `name[0]` rather than creating a list and indexing into it. This breaks the FHIR response structure for array fields.

**Fix:** Add array-index detection (matching `getValueByPath` logic) — create `ArrayList` when encountering `[N]` segments and ensure the list is large enough.

---

### 9. individualId special-casing leaks domain logic into generic code

**File:** `TransformService.java:139-150`

```java
if ("individualId".equals(mapping.getEgovField())) {
    if (transformed instanceof String) {
        String strValue = (String) transformed;
        if (strValue.contains(",")) {
            transformed = Arrays.asList(strValue.split("\\s*,\\s*"));
        } else {
            transformed = List.of(transformed);
        }
    } else {
        transformed = List.of(transformed);
    }
}
```

This hardcoded field name in a "generic" transform service defeats the config-driven architecture. Every new resource type that needs comma-separated-to-list behavior will require a code change.

**Fix:** Add a new transform type (e.g., `commaSeparatedToList`) or a `wrapAsList` flag in the mapping config.

---

### 10. Delete doesn't do a read-before-delete

**File:** `FhirController.java:110-126`

The delete endpoint builds a minimal object with just `id` and sends it through `fhirToEgov`. But the eGov bulk delete API likely needs a full object (with `tenantId`, `rowVersion`, `isDeleted`, etc.).

Additionally, the `softDelete` flag defined in the mapping config (`ApiMapping.softDelete`) is never read by any code.

**Fix:** For soft-delete, fetch the existing record first, set `isDeleted=true`, then call the API. Implement logic that checks the `softDelete` flag.

---

### 11. No resource-not-found handling

**File:** `FhirController.java:23-41`

The `read` endpoint calls search and returns whatever comes back. If the ID doesn't exist, `egovToFhir` will return an empty/malformed resource rather than returning HTTP 404 with a FHIR OperationOutcome.

**Fix:** After `egovToFhir`, check if the result is empty/null and return 404 with an OperationOutcome containing code `not-found`.

---

### 12. read uses operation name "read" but there's no "read" API mapping

**File:** `FhirController.java:38`

```java
Map<String, Object> fhirResponse = transformService.egovToFhir(egovResponse, config, "read");
```

The mapping config defines `create`, `update`, `search`, `delete` — but not `read`. The `egovToFhir` call with `operation="read"` will NPE at `TransformService.java:92-93` when looking up the response model via `config.getApiMapping().get("read")`.

**Fix:** Either add a `"read"` entry to the mapping config (can share the same response model as `search`), or map the `read` operation to use the `search` response model in code.

---

## Code Quality Issues

### 13. Default URL has a typo

**File:** `EgovApiService.java:19`

```java
@Value("${egov.base.url:http:8080}")
```

Should be `http://localhost:8080`. Missing `//localhost`.

---

### 14. Raw type usage

**File:** `EgovApiService.java:75`

```java
ResponseEntity<Map> response = restTemplate.exchange(..., Map.class);
```

Should be `ResponseEntity<Map<String, Object>>` to avoid unchecked casts downstream. Use `ParameterizedTypeReference<Map<String, Object>>` with `exchange()`.

---

### 15. Unchecked casts throughout TransformService

**File:** `TransformService.java` — lines 41, 182, 296, 308-312

Multiple `(Map<String, Object>)` casts without `instanceof` checks. A malformed JSON structure will throw `ClassCastException` with no meaningful error message.

**Fix:** Add `instanceof` guards or use a helper method that validates and casts safely, throwing a descriptive exception on type mismatch.

---

### 16. Swallowed exceptions in getValueByPath

**File:** `TransformService.java:284-286`

```java
} catch (Exception e) {
    return null;
}
```

`getValueByPath` catches all exceptions silently. A typo in a mapping config path will produce silently missing fields with no indication of what went wrong.

**Fix:** Log at `WARN` or `DEBUG` level with the path that failed, so misconfigurations are discoverable.

---

### 17. Two divergent copies of the mapping file

- `src/main/resources/mappings/individual-patient-mapping.json` (production, simpler)
- `config/individual-patient-mapping.json` (development, more features)

The `config/` version references transforms (`prefixUri`, `toTag`, `concatName`, `toTelecom`, `toAttachment`) that don't exist in `TransformService`. These will silently fall through to `default -> value` (line 229) and produce no transformation.

**Fix:** Remove the stale copy, or clearly mark one as "planned" vs "active". Implement referenced transforms or remove them from config.

---

### 18. MappingConfigLoader.loadConfigs() throws raw Exception

**File:** `MappingConfigLoader.java:28`

```java
public void loadConfigs() throws Exception {
```

If any mapping file is malformed, the entire application fails to start with a generic error and no indication of which file caused the problem.

**Fix:** Catch exceptions per-file inside the loop, log which file failed with the parse error, and continue loading the rest. Optionally fail startup only if zero configs load successfully.

---

## Missing Capabilities

| Gap | Impact |
|-----|--------|
| **No tests** | Zero unit or integration tests. No `src/test/` directory exists. The `TransformService` is the core logic and has the most edge cases — it should be the first thing tested. |
| **No health check** | No `/actuator/health` endpoint. The service cannot be monitored or probed by Kubernetes/DIGIT infrastructure. Add `spring-boot-starter-actuator` dependency. |
| **No FHIR conformance/capability statement** | FHIR clients expect `GET /fhir/metadata` to return a CapabilityStatement describing supported resources and operations. |
| **No pagination links in Bundles** | FHIR Bundles should include `link` entries (`self`, `next`, `previous`) for pagination navigation. Currently only `total` is set. |
| **No fullUrl in Bundle entries** | Each Bundle entry requires a `fullUrl` per the FHIR specification. Currently entries only contain `resource`. |
| **No ETag / versioning** | FHIR expects `meta.versionId` and `ETag` headers for concurrency control on updates. |
| **No FHIR content negotiation** | Should support `Accept: application/fhir+json` and return `Content-Type: application/fhir+json` per the FHIR spec. |
| **No identifier reverse mapping** | `fhirToEgov` ignores FHIR identifiers entirely — only `egovToFhir` maps them. Creating/updating a patient with identifiers will lose that data. |
| **No retry / circuit breaker** | A single eGov backend failure cascades to all FHIR clients. Consider Spring Retry or Resilience4j. |
| **No Dockerfile** | No containerization setup despite being in a DIGIT microservice repository. |
| **No request tracing** | No correlation ID propagation. Debugging cross-service issues in production will be difficult. |

---

## Positive Aspects

- **Clean separation of concerns** — controller / transform / API client layers are well-defined and distinct.
- **Config-driven mapping** — excellent extensibility model. Adding a new FHIR resource type is a JSON file, not a code change.
- **Bidirectional transforms** — a single mapping config drives both FHIR→eGov and eGov→FHIR directions.
- **Proper FHIR error responses** — `GlobalExceptionHandler` returns well-structured `OperationOutcome` resources.
- **Lightweight footprint** — minimal dependencies, fast startup, easy to understand.
- **Good documentation** — `docs/flow-diagram.md` with architecture diagrams and sequence flows.
- **Pluggable transform system** — `codeMap`, `epochToDate`, `dateToEpoch`, `epochToIso`, `negate` cover core needs and the pattern is easy to extend.

---

## Priority Recommendations

### Immediate (before any deployment)

1. Add HTTP timeouts to `RestTemplate` (connect: 5s, read: 30s).
2. Fix PII logging — change to `DEBUG`, add redaction for sensitive fields.
3. Fix the `http:8080` default URL typo.
4. Fix the `"read"` operation NPE — add a read entry to mapping config or alias it to search.

### High Priority

5. Add resource-not-found (404) handling for the read endpoint.
6. Remove `individualId` hardcoding — replace with a config-driven transform.
7. Write unit tests for `TransformService` — it's the core logic with the most edge cases.
8. Add `spring-boot-starter-actuator` for health checks.
9. Fix request body mutation in `EgovApiService.callApi()`.

### Medium Priority

10. Fix `setValueByPath` to handle array paths for correct FHIR response structure.
11. Complete the reverse mapping (`egovToFhir`) for nested/array fields.
12. Add input validation — at minimum, `resourceType` match between URL and body.
13. Add FHIR metadata endpoint (`GET /fhir/metadata`).
14. Add proper FHIR content type (`application/fhir+json`).
15. Implement the `softDelete` flag logic.

### Low Priority

16. Add retry/circuit breaker for eGov API calls.
17. Add `fullUrl` and pagination `link` entries to Bundles.
18. Add FHIR identifier reverse mapping (FHIR→eGov direction).
19. Add request tracing / correlation IDs.
20. Clean up or remove the stale `config/individual-patient-mapping.json`.
