package org.egov.fhir.service;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.egov.fhir.config.MappingConfig;
import org.egov.fhir.config.MappingConfig.*;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Arrays;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TransformService {

    // TODO: Later extract tenantId from FHIR meta.tag or request context
    @org.springframework.beans.factory.annotation.Value("${egov.default.tenantId:mz}")
    private String defaultTenantId;

    // FHIR Patient -> eGov Individual (for create/update)
    public Map<String, Object> fhirToEgov(Map<String, Object> fhirResource, MappingConfig config, String operation, String authToken) {
        Map<String, Object> egovObject = new HashMap<>();

        egovObject.put("tenantId", defaultTenantId);

        // Apply field mappings
        for (FieldMapping mapping : config.getFieldMappings()) {
            Object value = getValueByPath(fhirResource, mapping.getFhirField());
            if (value != null) {
                // Handle nested array mappings (like address)
                if (Boolean.TRUE.equals(mapping.getIsArray()) && mapping.getFieldMappings() != null && value instanceof List) {
                    List<Map<String, Object>> transformedList = new ArrayList<>();
                    for (Object item : (List<?>) value) {
                        if (item instanceof Map) {
                            Map<String, Object> transformedItem = new HashMap<>();
                            transformedItem.put("tenantId", defaultTenantId);
                            for (FieldMapping nestedMapping : mapping.getFieldMappings()) {
                                Object nestedValue = getValueByPath((Map<String, Object>) item, nestedMapping.getFhirField());
                                if (nestedValue != null) {
                                    // Apply transform first — it may handle the array (e.g., joinArray)
                                    nestedValue = applyTransform(nestedValue, nestedMapping, "toEgov");
                                    // If transform didn't collapse the array, take first element
                                    if (nestedValue instanceof List && !Boolean.TRUE.equals(nestedMapping.getIsArray())) {
                                        List<?> list = (List<?>) nestedValue;
                                        nestedValue = !list.isEmpty() ? list.get(0) : null;
                                    }
                                    if (nestedValue != null) {
                                        setValueByPath(transformedItem, nestedMapping.getEgovField(), nestedValue);
                                    }
                                }
                            }
                            transformedList.add(transformedItem);
                        }
                    }
                    value = transformedList;
                }
                // If FHIR field is array but eGov expects scalar (no nested mappings)
                else if (Boolean.TRUE.equals(mapping.getIsArray()) && value instanceof List && mapping.getFieldMappings() == null) {
                    // Apply transform first — it may handle the array itself (e.g., joinArray)
                    value = applyTransform(value, mapping, "toEgov");
                    // If transform didn't collapse the array, take first element
                    if (value instanceof List) {
                        List<?> list = (List<?>) value;
                        value = !list.isEmpty() ? list.get(0) : null;
                    }
                } else {
                    value = applyTransform(value, mapping, "toEgov");
                }
                if (value == null) continue;
                setValueByPath(egovObject, mapping.getEgovField(), value);
            }
        }

        // Build request wrapper
        ApiMapping apiMapping = config.getApiMapping().get(operation);
        ModelDef modelDef = config.getRequestModels().get(apiMapping.getRequestModel());

        Map<String, Object> request = new HashMap<>();
        request.put("RequestInfo", createRequestInfo(authToken));

        String wrapperKey = extractWrapperKey(modelDef.getBasePath());
        if (Boolean.TRUE.equals(modelDef.getIsArray())) {
            request.put(wrapperKey, List.of(egovObject));
        } else {
            request.put(wrapperKey, egovObject);
        }

        return request;
    }

    // eGov Individual -> FHIR Patient (for response)
    public Map<String, Object> egovToFhir(Map<String, Object> egovResponse, MappingConfig config, String operation) {
        ApiMapping apiMapping = config.getApiMapping().get(operation);
        ModelDef modelDef = config.getResponseModels().get(apiMapping.getResponseModel());

        // Extract data from response using basePath
        List<Map<String, Object>> egovObjects = extractFromPath(egovResponse, modelDef.getBasePath());

        List<Map<String, Object>> fhirResources = new ArrayList<>();
        for (Map<String, Object> egovObject : egovObjects) {
            Map<String, Object> fhirResource = new HashMap<>();
            fhirResource.put("resourceType", config.getFhirResource());

            // Apply field mappings
            for (FieldMapping mapping : config.getFieldMappings()) {
                Object value = getValueByPath(egovObject, mapping.getEgovField());
                if (value != null) {
                    value = applyTransform(value, mapping, "toFhir");
                    if (value != null) {
                        // For telecomEntry: accumulate into an array on the fhirField
                        if ("telecomEntry".equals(mapping.getTransform())) {
                            Object existing = fhirResource.get(mapping.getFhirField());
                            if (existing instanceof List) {
                                ((List<Object>) existing).add(value);
                            } else {
                                List<Object> list = new ArrayList<>();
                                list.add(value);
                                fhirResource.put(mapping.getFhirField(), list);
                            }
                        } else {
                            // Wrap in array if mapping expects array (e.g., name[0].given)
                            if (Boolean.TRUE.equals(mapping.getIsArray()) && !(value instanceof List)) {
                                value = new ArrayList<>(List.of(value));
                            }
                            setValueByPath(fhirResource, mapping.getFhirField(), value);
                        }
                    }
                }
            }

            // Apply identifier mappings
            applyIdentifierMappings(egovObject, fhirResource, config.getIdentifierMappings());

            // Apply extension mappings
            applyExtensionMappings(egovObject, fhirResource, config.getExtensionMappings());

            fhirResources.add(fhirResource);
        }

        // For single resource operations (read/create/update)
        if (!operation.equals("search") && !fhirResources.isEmpty()) {
            return fhirResources.get(0);
        }

        // For search - return Bundle
        return createBundle(fhirResources, egovResponse, modelDef);
    }

    // Search params -> eGov search request
    public Map<String, Object> searchParamsToEgov(Map<String, String> params, MappingConfig config, String authToken) {
        Map<String, Object> searchCriteria = new HashMap<>();

        // TODO: tenantId should come from request context
        searchCriteria.put("tenantId", defaultTenantId);

        for (FieldMapping mapping : config.getSearchParamMappings()) {
            String value = params.get(mapping.getFhirKey());
            if (value != null) {
                Object transformed = applyTransform(value, mapping, "toEgov");
                // Wrap in array if mapping declares isArray (e.g., id, individualId fields that backend expects as lists)
                if (Boolean.TRUE.equals(mapping.getIsArray())) {
                    if (transformed instanceof String) {
                        String strValue = (String) transformed;
                        if (strValue.contains(",")) {
                            transformed = Arrays.asList(strValue.split("\\s*,\\s*"));
                        } else {
                            transformed = List.of(transformed);
                        }
                    } else if (!(transformed instanceof List)) {
                        transformed = List.of(transformed);
                    }
                }
                setValueByPath(searchCriteria, mapping.getEgovField(), transformed);
            }
        }

        ApiMapping apiMapping = config.getApiMapping().get("search");
        ModelDef modelDef = config.getRequestModels().get(apiMapping.getRequestModel());

        Map<String, Object> request = new HashMap<>();
        request.put("RequestInfo", createRequestInfo(authToken));
        request.put(extractWrapperKey(modelDef.getBasePath()), searchCriteria);

        // Add pagination
        if (params.containsKey("_count")) {
            request.put("limit", Integer.parseInt(params.get("_count")));
        }
        if (params.containsKey("_offset")) {
            request.put("offset", Integer.parseInt(params.get("_offset")));
        }

        return request;
    }

    // --- Transform helpers ---

    private Object applyTransform(Object value, FieldMapping mapping, String direction) {
        if (mapping.getTransform() == null) return value;

        Map<String, Object> config = mapping.getTransformConfig();

        return switch (mapping.getTransform()) {
            case "codeMap" -> {
                Map<String, String> codeMap = (Map<String, String>) config.get(direction);
                yield codeMap.getOrDefault(value.toString(), value.toString());
            }
            case "epochToDate" -> {
                if (direction.equals("toFhir") && value instanceof Number) {
                    long epoch = ((Number) value).longValue();
                    yield LocalDate.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC).toString();
                }
                // toFhir: convert DD/MM/YYYY to ISO format
                if (direction.equals("toFhir") && value instanceof String) {
                    try {
                        LocalDate date = LocalDate.parse((String) value, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                        yield date.toString(); // ISO format YYYY-MM-DD
                    } catch (Exception e) {
                        yield value;
                    }
                }
                // toEgov: convert ISO YYYY-MM-DD to DD/MM/YYYY
                if (direction.equals("toEgov") && value instanceof String) {
                    try {
                        LocalDate date = LocalDate.parse((String) value);
                        yield date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    } catch (Exception e) {
                        yield value;
                    }
                }
                yield value;
            }
            case "dateToEpoch" -> {
                if (direction.equals("toEgov") && value instanceof String) {
                    yield LocalDate.parse((String) value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                }
                yield value;
            }
            case "epochToIso" -> {
                if (direction.equals("toFhir") && value instanceof Number) {
                    long epoch = ((Number) value).longValue();
                    yield Instant.ofEpochMilli(epoch).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
                }
                if (direction.equals("toEgov") && value instanceof String) {
                    try {
                        yield Instant.parse((String) value).toEpochMilli();
                    } catch (Exception e) {
                        // Try parsing as local date if not full ISO instant
                        try {
                            yield LocalDate.parse((String) value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                        } catch (Exception e2) {
                            yield value;
                        }
                    }
                }
                yield value;
            }
            case "negate" -> {
                if (value instanceof Boolean) {
                    yield !(Boolean) value;
                }
                yield value;
            }
            case "staticValue" -> {
                if (direction.equals("toFhir") && config != null && config.get("toFhir") != null) {
                    yield config.get("toFhir");
                }
                yield null;
            }
            case "concatFields" -> {
                if (direction.equals("toFhir") && value instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) value;
                    List<String> fields = config != null ? (List<String>) config.get("fields") : List.of();
                    String sep = config != null && config.get("separator") != null
                            ? (String) config.get("separator") : " ";
                    String result = fields.stream()
                            .map(f -> map.get(f))
                            .filter(Objects::nonNull)
                            .map(Object::toString)
                            .filter(s -> !s.isEmpty())
                            .reduce((a, b) -> a + sep + b)
                            .orElse(null);
                    yield result;
                }
                // toEgov: skip — individual fields are mapped separately
                yield null;
            }
            case "joinArray" -> {
                String separator = config != null && config.get("separator") != null
                        ? (String) config.get("separator") : ", ";
                if (direction.equals("toEgov")) {
                    // Array -> joined string
                    if (value instanceof List) {
                        List<?> list = (List<?>) value;
                        yield list.stream()
                                .filter(Objects::nonNull)
                                .map(Object::toString)
                                .reduce((a, b) -> a + separator + b)
                                .orElse("");
                    }
                    yield value;
                }
                if (direction.equals("toFhir")) {
                    // Joined string -> array
                    if (value instanceof String) {
                        yield Arrays.asList(((String) value).split(Pattern.quote(separator)));
                    }
                    yield value;
                }
                yield value;
            }
            case "telecomEntry" -> {
                if (direction.equals("toFhir")) {
                    // Build full FHIR telecom object from plain value using config for system/use
                    Map<String, Object> telecom = new HashMap<>();
                    if (config != null && config.get("system") != null) {
                        telecom.put("system", config.get("system"));
                    }
                    if (config != null && config.get("use") != null) {
                        telecom.put("use", config.get("use"));
                    }
                    telecom.put("value", value);
                    yield telecom;
                }
                if (direction.equals("toEgov")) {
                    // value is the full telecom array — find entry matching system/use from config
                    if (value instanceof List) {
                        for (Object item : (List<?>) value) {
                            if (item instanceof Map) {
                                Map<?, ?> entry = (Map<?, ?>) item;
                                String system = config != null ? (String) config.get("system") : null;
                                String use = config != null ? (String) config.get("use") : null;
                                boolean systemMatch = system == null || system.equals(entry.get("system"));
                                boolean useMatch = use == null || use.equals(entry.get("use"));
                                if (systemMatch && useMatch) {
                                    yield entry.get("value");
                                }
                            }
                        }
                    }
                    yield null;
                }
                yield value;
            }
            default -> value;
        };
    }

    private void applyIdentifierMappings(Map<String, Object> egov, Map<String, Object> fhir, List<IdentifierMapping> mappings) {
        if (mappings == null) return;

        List<Map<String, Object>> identifiers = new ArrayList<>();
        for (IdentifierMapping mapping : mappings) {
            Object value = getValueByPath(egov, mapping.getEgovField());
            if (value != null) {
                Map<String, Object> identifier = new HashMap<>();
                identifier.put("system", mapping.getSystem());
                identifier.put("value", value);
                if (mapping.getUse() != null) {
                    identifier.put("use", mapping.getUse());
                }
                if (mapping.getType() != null) {
                    identifier.put("type", mapping.getType());
                }
                identifiers.add(identifier);
            }
        }
        if (!identifiers.isEmpty()) {
            fhir.put("identifier", identifiers);
        }
    }

    private void applyExtensionMappings(Map<String, Object> egov, Map<String, Object> fhir, List<MappingConfig.ExtensionMapping> mappings) {
        if (mappings == null) return;

        List<Map<String, Object>> extensions = new ArrayList<>();
        for (MappingConfig.ExtensionMapping mapping : mappings) {
            Object value = getValueByPath(egov, mapping.getEgovField());
            if (value != null) {
                // Apply transform if configured (reuse via a temporary FieldMapping)
                if (mapping.getTransform() != null) {
                    FieldMapping fm = new FieldMapping();
                    fm.setTransform(mapping.getTransform());
                    fm.setTransformConfig(mapping.getTransformConfig());
                    value = applyTransform(value, fm, "toFhir");
                }

                Map<String, Object> ext = new HashMap<>();
                ext.put("url", mapping.getUrl());

                // Set typed value based on valueType from config
                String valueKey = switch (mapping.getValueType()) {
                    case "boolean" -> "valueBoolean";
                    case "dateTime" -> "valueDateTime";
                    case "integer" -> "valueInteger";
                    case "decimal" -> "valueDecimal";
                    default -> "valueString";
                };
                ext.put(valueKey, value);
                extensions.add(ext);
            }
        }
        if (!extensions.isEmpty()) {
            fhir.put("extension", extensions);
        }
    }

    // --- Path helpers ---

    private Object getValueByPath(Map<String, Object> obj, String path) {
        try {
            // Handle simple dot notation paths manually for reliability
            String[] parts = path.split("\\.");
            Object current = obj;

            for (String part : parts) {
                if (current == null) return null;

                // Check for array index like "name[0]"
                if (part.contains("[")) {
                    String fieldName = part.substring(0, part.indexOf("["));
                    int index = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.indexOf("]")));

                    if (current instanceof Map) {
                        current = ((Map<?, ?>) current).get(fieldName);
                    }
                    if (current instanceof List) {
                        List<?> list = (List<?>) current;
                        current = index < list.size() ? list.get(index) : null;
                    }
                } else {
                    if (current instanceof Map) {
                        current = ((Map<?, ?>) current).get(part);
                    }
                }
            }
            return current;
        } catch (Exception e) {
            return null;
        }
    }

    private void setValueByPath(Map<String, Object> obj, String path, Object value) {
        String[] parts = path.split("\\.");
        Object current = obj;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (part.contains("[")) {
                String fieldName = part.substring(0, part.indexOf("["));
                int index = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.indexOf("]")));
                Map<String, Object> currentMap = (Map<String, Object>) current;
                currentMap.computeIfAbsent(fieldName, k -> new ArrayList<>());
                List<Object> list = (List<Object>) currentMap.get(fieldName);
                while (list.size() <= index) {
                    list.add(new HashMap<String, Object>());
                }
                current = list.get(index);
            } else {
                Map<String, Object> currentMap = (Map<String, Object>) current;
                currentMap.computeIfAbsent(part, k -> new HashMap<String, Object>());
                current = currentMap.get(part);
            }
        }

        // Handle last part
        String lastPart = parts[parts.length - 1];
        if (lastPart.contains("[")) {
            String fieldName = lastPart.substring(0, lastPart.indexOf("["));
            int index = Integer.parseInt(lastPart.substring(lastPart.indexOf("[") + 1, lastPart.indexOf("]")));
            Map<String, Object> currentMap = (Map<String, Object>) current;
            currentMap.computeIfAbsent(fieldName, k -> new ArrayList<>());
            List<Object> list = (List<Object>) currentMap.get(fieldName);
            while (list.size() <= index) {
                list.add(null);
            }
            list.set(index, value);
        } else {
            ((Map<String, Object>) current).put(lastPart, value);
        }
    }

    private String extractWrapperKey(String basePath) {
        // $.Individuals[*] -> Individuals
        return basePath.replaceAll("^\\$\\.", "").replaceAll("\\[\\*\\]$", "");
    }

    private List<Map<String, Object>> extractFromPath(Map<String, Object> response, String basePath) {
        try {
            Object result = JsonPath.read(response, basePath);
            if (result instanceof List) {
                return (List<Map<String, Object>>) result;
            }
            return List.of((Map<String, Object>) result);
        } catch (PathNotFoundException e) {
            return Collections.emptyList();
        }
    }

    private Map<String, Object> createRequestInfo(String authToken) {
        Map<String, Object> requestInfo = new HashMap<>();
        requestInfo.put("apiId", "fhir-adapter");
        requestInfo.put("ver", "1.0");
        requestInfo.put("ts", System.currentTimeMillis());
        requestInfo.put("msgId", UUID.randomUUID().toString());
        if (authToken != null && !authToken.isEmpty()) {
            requestInfo.put("authToken", authToken);
        }
        return requestInfo;
    }

    private Map<String, Object> createBundle(List<Map<String, Object>> resources, Map<String, Object> egovResponse, ModelDef modelDef) {
        Map<String, Object> bundle = new HashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "searchset");

        // Get total count
        if (modelDef.getTotalCountPath() != null) {
            try {
                Object total = JsonPath.read(egovResponse, modelDef.getTotalCountPath());
                bundle.put("total", total);
            } catch (PathNotFoundException ignored) {}
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<String, Object> resource : resources) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("resource", resource);
            entries.add(entry);
        }
        bundle.put("entry", entries);

        return bundle;
    }
}
