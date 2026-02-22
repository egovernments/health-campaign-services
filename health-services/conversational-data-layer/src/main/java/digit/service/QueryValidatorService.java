package digit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import digit.config.CdlConfiguration;
import digit.web.models.QueryValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;

import static digit.config.ServiceConstants.*;

@Service
@Slf4j
public class QueryValidatorService {

    private final ObjectMapper objectMapper;
    private final CdlConfiguration config;

    public QueryValidatorService(ObjectMapper objectMapper, CdlConfiguration config) {
        this.objectMapper = objectMapper;
        this.config = config;
    }

    /**
     * Validates the generated ES DSL query:
     * 1. Parses JSON
     * 2. Checks top-level keys against allowlist
     * 3. Recursively checks for blocked query types
     * 4. Enforces size limits
     * 5. Injects timeout
     * 6. Caps aggregation depth
     */
    public QueryValidationResult validate(String queryJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(queryJson);
        } catch (JsonProcessingException e) {
            return QueryValidationResult.builder()
                    .valid(false)
                    .reason("Invalid JSON: " + e.getMessage())
                    .build();
        }

        if (!root.isObject()) {
            return QueryValidationResult.builder()
                    .valid(false)
                    .reason("Query must be a JSON object")
                    .build();
        }

        // Check top-level keys
        Iterator<String> topLevelFields = root.fieldNames();
        while (topLevelFields.hasNext()) {
            String field = topLevelFields.next();
            if (!ALLOWED_TOP_LEVEL_KEYS.contains(field)) {
                return QueryValidationResult.builder()
                        .valid(false)
                        .reason("Disallowed top-level key: " + field)
                        .build();
            }
        }

        // Check for blocked query types recursively
        String blockedType = findBlockedQueryType(root);
        if (blockedType != null) {
            return QueryValidationResult.builder()
                    .valid(false)
                    .reason("Blocked query type found: " + blockedType)
                    .build();
        }

        // Check aggregation depth
        JsonNode aggsNode = root.has("aggs") ? root.get("aggs") : root.get("aggregations");
        if (aggsNode != null) {
            int depth = measureAggDepth(aggsNode);
            if (depth > config.getQueryMaxAggDepth()) {
                return QueryValidationResult.builder()
                        .valid(false)
                        .reason("Aggregation depth " + depth + " exceeds maximum " + config.getQueryMaxAggDepth())
                        .build();
            }
        }

        // Enforce size and timeout — mutate the query
        ObjectNode rootObj = (ObjectNode) root;
        enforceSize(rootObj);
        injectTimeout(rootObj);

        try {
            String sanitizedQuery = objectMapper.writeValueAsString(rootObj);
            return QueryValidationResult.builder()
                    .valid(true)
                    .sanitizedQuery(sanitizedQuery)
                    .build();
        } catch (JsonProcessingException e) {
            return QueryValidationResult.builder()
                    .valid(false)
                    .reason("Failed to serialize sanitized query")
                    .build();
        }
    }

    /**
     * Recursively searches for blocked query type keys in the JSON tree.
     */
    private String findBlockedQueryType(JsonNode node) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (BLOCKED_QUERY_TYPES.contains(entry.getKey())) {
                    return entry.getKey();
                }
                String found = findBlockedQueryType(entry.getValue());
                if (found != null) return found;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findBlockedQueryType(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Measures the depth of nested aggregations.
     */
    private int measureAggDepth(JsonNode aggsNode) {
        if (aggsNode == null || !aggsNode.isObject()) return 0;

        int maxChildDepth = 0;
        Iterator<Map.Entry<String, JsonNode>> fields = aggsNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode aggDef = entry.getValue();
            if (aggDef.isObject()) {
                JsonNode nestedAggs = aggDef.has("aggs") ? aggDef.get("aggs") : aggDef.get("aggregations");
                if (nestedAggs != null) {
                    int childDepth = measureAggDepth(nestedAggs);
                    maxChildDepth = Math.max(maxChildDepth, childDepth);
                }
            }
        }
        return 1 + maxChildDepth;
    }

    /**
     * Enforces size limits: cap at max, default if missing.
     */
    private void enforceSize(ObjectNode root) {
        if (root.has("size")) {
            int size = root.get("size").asInt();
            if (size > config.getQueryMaxSize()) {
                root.put("size", config.getQueryMaxSize());
            }
        } else {
            root.put("size", config.getQueryDefaultSize());
        }
    }

    /**
     * Injects timeout into the query.
     */
    private void injectTimeout(ObjectNode root) {
        root.put("timeout", config.getQueryTimeout());
    }
}
