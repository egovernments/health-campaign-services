package org.egov.fhir.controller;

import lombok.RequiredArgsConstructor;
import org.egov.fhir.config.MappingConfig;
import org.egov.fhir.config.MappingConfigLoader;
import org.egov.fhir.service.EgovApiService;
import org.egov.fhir.service.TransformService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/fhir")
@RequiredArgsConstructor
public class FhirController {

    private final MappingConfigLoader configLoader;
    private final TransformService transformService;
    private final EgovApiService egovApiService;

    // GET /fhir/{resourceType}/{id}
    @GetMapping("/{resourceType}/{id}")
    public ResponseEntity<Map<String, Object>> read(
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestHeader(value = "auth_token", required = false) String authToken) {

        MappingConfig config = getConfig(resourceType);

        // Build search by ID
        Map<String, Object> request = transformService.searchParamsToEgov(Map.of("_id", id), config, authToken);

        // Call eGov API
        Map<String, Object> egovResponse = egovApiService.callApi(config.getApiMapping().get("search"), request);

        // Transform to FHIR
        Map<String, Object> fhirResponse = transformService.egovToFhir(egovResponse, config, "read");

        return ResponseEntity.ok(fhirResponse);
    }

    // GET /fhir/{resourceType}?params
    @GetMapping("/{resourceType}")
    public ResponseEntity<Map<String, Object>> search(
            @PathVariable String resourceType,
            @RequestParam Map<String, String> params,
            @RequestHeader(value = "auth_token", required = false) String authToken) {

        MappingConfig config = getConfig(resourceType);

        // Transform search params
        Map<String, Object> request = transformService.searchParamsToEgov(params, config, authToken);

        // Call eGov API
        Map<String, Object> egovResponse = egovApiService.callApi(config.getApiMapping().get("search"), request);

        // Transform to FHIR Bundle
        Map<String, Object> bundle = transformService.egovToFhir(egovResponse, config, "search");

        return ResponseEntity.ok(bundle);
    }

    // POST /fhir/{resourceType}
    @PostMapping("/{resourceType}")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String resourceType,
            @RequestBody Map<String, Object> fhirResource,
            @RequestHeader(value = "auth_token", required = false) String authToken) {

        MappingConfig config = getConfig(resourceType);

        // Transform FHIR to eGov
        Map<String, Object> request = transformService.fhirToEgov(fhirResource, config, "create", authToken);

        // Call eGov API
        Map<String, Object> egovResponse = egovApiService.callApi(config.getApiMapping().get("create"), request);

        // Transform response to FHIR
        Map<String, Object> fhirResponse = transformService.egovToFhir(egovResponse, config, "create");

        return ResponseEntity.status(201).body(fhirResponse);
    }

    // PUT /fhir/{resourceType}/{id}
    @PutMapping("/{resourceType}/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestBody Map<String, Object> fhirResource,
            @RequestHeader(value = "auth_token", required = false) String authToken) {

        MappingConfig config = getConfig(resourceType);

        // Ensure ID is set
        fhirResource.put("id", id);

        // Transform FHIR to eGov
        Map<String, Object> request = transformService.fhirToEgov(fhirResource, config, "update", authToken);

        // Call eGov API
        Map<String, Object> egovResponse = egovApiService.callApi(config.getApiMapping().get("update"), request);

        // Transform response to FHIR
        Map<String, Object> fhirResponse = transformService.egovToFhir(egovResponse, config, "update");

        return ResponseEntity.ok(fhirResponse);
    }

    // DELETE /fhir/{resourceType}/{id}
    @DeleteMapping("/{resourceType}/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestHeader(value = "auth_token", required = false) String authToken) {

        MappingConfig config = getConfig(resourceType);

        // Build request with ID
        Map<String, Object> request = transformService.fhirToEgov(Map.of("id", id), config, "delete", authToken);

        // Call eGov API
        egovApiService.callApi(config.getApiMapping().get("delete"), request);

        return ResponseEntity.noContent().build();
    }

    private MappingConfig getConfig(String resourceType) {
        MappingConfig config = configLoader.getConfig(resourceType);
        if (config == null) {
            throw new IllegalArgumentException("Unsupported resource type: " + resourceType);
        }
        return config;
    }
}
