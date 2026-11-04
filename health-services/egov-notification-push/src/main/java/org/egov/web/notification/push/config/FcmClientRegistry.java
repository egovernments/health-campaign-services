package org.egov.web.notification.push.config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FcmClientRegistry {

    static final String KEY_ENV = "FCM_SERVICE_ACCOUNT_KEY_JSON";
    static final String MAP_ENV = "FCM_TENANT_PROJECT_MAP_JSON";

    public record FcmClient(String projectId, FirebaseMessaging messaging) {
    }

    private final Map<String, FcmClient> clientsByProject = new LinkedHashMap<>();
    private final Map<String, String> projectByTenant = new LinkedHashMap<>();
    private final FcmClient defaultClient;

    public FcmClientRegistry(String serviceAccountKeyJson, String tenantProjectMapJson, ObjectMapper mapper) {
        Map<String, JsonNode> accounts = parseServiceAccounts(serviceAccountKeyJson, mapper);
        projectByTenant.putAll(parseTenantMap(tenantProjectMapJson, mapper));

        if (projectByTenant.isEmpty() && accounts.size() > 1) {
            throw new IllegalStateException(MAP_ENV + " is required when " + KEY_ENV
                    + " has more than one service account. Set it as {\"<tenantId>\":\"<project_id>\"}. Loaded project ids: "
                    + accounts.keySet());
        }
        projectByTenant.forEach((tenantId, projectId) -> {
            if (!accounts.containsKey(projectId)) {
                throw new IllegalStateException(MAP_ENV + " maps tenant '" + tenantId + "' to project '" + projectId
                        + "' but " + KEY_ENV + " has no service account for it. Loaded project ids: " + accounts.keySet());
            }
        });

        accounts.forEach((projectId, node) ->
                clientsByProject.put(projectId, new FcmClient(projectId, initMessaging(projectId, node))));

        if (projectByTenant.isEmpty()) {
            defaultClient = clientsByProject.values().iterator().next();
            log.warn("{} not set; FCM project '{}' serves all tenants", MAP_ENV, defaultClient.projectId());
        } else {
            defaultClient = null;
            Set<String> unused = new HashSet<>(accounts.keySet());
            unused.removeAll(projectByTenant.values());
            if (!unused.isEmpty()) {
                log.warn("FCM projects not mapped to any tenant: {}", unused);
            }
        }
        log.info("FCM projects loaded: {} tenantProjectMap: {}", clientsByProject.keySet(), projectByTenant);
    }

    public Optional<FcmClient> forTenant(String tenantId) {
        String current = tenantId;
        while (current != null && !current.isBlank()) {
            String projectId = projectByTenant.get(current);
            if (projectId != null) {
                return Optional.of(clientsByProject.get(projectId));
            }
            int dot = current.lastIndexOf('.');
            current = dot > 0 ? current.substring(0, dot) : null;
        }
        return Optional.ofNullable(defaultClient);
    }

    private static Map<String, JsonNode> parseServiceAccounts(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException(KEY_ENV + " is not set. Set it to a service-account JSON object or a JSON array of them");
        }
        JsonNode root = readJson(json, mapper, KEY_ENV);
        List<JsonNode> entries = new ArrayList<>();
        if (root.isObject()) {
            entries.add(root);
        } else if (root.isArray()) {
            root.forEach(entries::add);
        } else {
            throw new IllegalStateException(KEY_ENV + " must be a service-account JSON object or a JSON array of them");
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException(KEY_ENV + " is an empty array. Add at least one service-account JSON object");
        }

        Map<String, JsonNode> byProject = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            JsonNode node = entries.get(i);
            if (!node.isObject() || !"service_account".equals(node.path("type").asText())) {
                throw new IllegalStateException(KEY_ENV + "[" + i + "] is not a service-account JSON (\"type\" must be \"service_account\")");
            }
            String projectId = node.path("project_id").asText("");
            if (projectId.isBlank()) {
                throw new IllegalStateException(KEY_ENV + "[" + i + "] has no \"project_id\"");
            }
            if (byProject.putIfAbsent(projectId, node) != null) {
                throw new IllegalStateException(KEY_ENV + " has more than one service account for project '" + projectId + "'");
            }
        }
        return byProject;
    }

    private static Map<String, String> parseTenantMap(String json, ObjectMapper mapper) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        JsonNode root = readJson(json, mapper, MAP_ENV);
        if (!root.isObject()) {
            throw new IllegalStateException(MAP_ENV + " must be a JSON object like {\"<tenantId>\":\"<project_id>\"}");
        }
        root.fields().forEachRemaining(e -> {
            if (!e.getValue().isTextual() || e.getValue().asText().isBlank() || e.getKey().isBlank()) {
                throw new IllegalStateException(MAP_ENV + " entry '" + e.getKey() + "' must map a tenantId to a non-empty project_id string");
            }
            result.put(e.getKey(), e.getValue().asText());
        });
        return result;
    }

    private static JsonNode readJson(String json, ObjectMapper mapper, String name) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(name + " is not valid JSON");
        }
    }

    private static FirebaseMessaging initMessaging(String projectId, JsonNode node) {
        FirebaseApp app = FirebaseApp.getApps().stream()
                .filter(a -> a.getName().equals(projectId))
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(options(projectId, node), projectId));
        return FirebaseMessaging.getInstance(app);
    }

    private static FirebaseOptions options(String projectId, JsonNode node) {
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(node.toString().getBytes(StandardCharsets.UTF_8)));
            return FirebaseOptions.builder().setCredentials(credentials).setProjectId(projectId).build();
        } catch (Exception e) {
            throw new IllegalStateException(KEY_ENV + " service account for project '" + projectId
                    + "' could not be loaded: " + e.getClass().getSimpleName());
        }
    }
}
