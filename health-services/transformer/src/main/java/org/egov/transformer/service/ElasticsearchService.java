package org.egov.transformer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.Constants;
import org.egov.transformer.config.TransformerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

@Service
@Slf4j
public class ElasticsearchService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TransformerProperties properties;

    @Autowired
    public ElasticsearchService(RestTemplate restTemplate,
                                @Qualifier("objectMapper") ObjectMapper objectMapper,
                                TransformerProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Helper method to get ES Base URL
     *
     * @return ES base URL (e.g., http://localhost:9200)
     */
    private String getESBaseUrl() {
        return "https://"+ properties.getEsHostName() + ":" + properties.getEsPortNo();
    }

    /**
     * Helper method to get ES encoded credentials for Basic Auth
     *
     * @return Base64 encoded credentials for Authorization header
     */
    private String getESEncodedCredentials() {
        String credentials = properties.getEsUsername() + ":" + properties.getEsPassword();
        byte[] credentialsBytes = credentials.getBytes();
        byte[] base64CredentialsBytes = Base64.getEncoder().encode(credentialsBytes);
        return "Basic " + new String(base64CredentialsBytes);
    }

    /**
     * Updates user status in project-staff-index-v1 for all documents matching the userId
     * Using Update By Query API - combines search and update in a single atomic operation
     *
     * @param userUuid       User UUID to search for
     * @param tenantId       Tenant ID (can be null)
     * @param active         New active status
     * @param effectiveDate  Effective date of status change
     */
    public void updateUserStatusInProjectStaff(String userUuid, String tenantId, Boolean active, Long effectiveDate) {
        try {
            String indexName = properties.getProjectStaffIndexName();
            log.info("Updating user status in Elasticsearch for userUuid: {}, index: {}, active: {}",
                    userUuid, indexName, active);

            String updateByQueryUrl = String.format("%s/%s/_update_by_query",
                    getESBaseUrl(),
                    indexName);

            // Build the update by query request
            ObjectNode updateByQueryRequest = objectMapper.createObjectNode();

            // Query part - find all documents with matching userId
            ObjectNode query = objectMapper.createObjectNode();
            ObjectNode term = objectMapper.createObjectNode();
            term.put("Data.userId.keyword", userUuid);
            query.set("term", term);
            updateByQueryRequest.set("query", query);

            // Script part - update the matching documents
            ObjectNode script = objectMapper.createObjectNode();
            String painlessScript =
                "ctx._source.Data.userActive = params.active; " +
                "if (ctx._source.Data.statusHistory == null) { " +
                "  ctx._source.Data.statusHistory = []; " +
                "} " +
                "Map statusEntry = new HashMap(); " +
                "statusEntry.put(params.activeFieldName, params.active); " +
                "statusEntry.put(params.effectiveDateFieldName, params.effectiveDate); " +
                "ctx._source.Data.statusHistory.add(statusEntry);";

            script.put("source", painlessScript);
            script.put("lang", "painless");

            // Add parameters for the script
            ObjectNode params = objectMapper.createObjectNode();
            params.put("active", active);
            params.put("effectiveDate", effectiveDate);
            params.put("activeFieldName", Constants.ES_FIELD_ACTIVE);
            params.put("effectiveDateFieldName", Constants.ES_FIELD_EFFECTIVE_DATE);
            script.set("params", params);

            updateByQueryRequest.set("script", script);

            // Optional: Add conflicts handling
            updateByQueryRequest.put("conflicts", "proceed"); // Continue even if version conflicts occur

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Authorization", getESEncodedCredentials());
            HttpEntity<String> requestEntity = new HttpEntity<>(updateByQueryRequest.toString(), headers);

            log.debug("Elasticsearch update by query request: {}", updateByQueryRequest.toString());

            ResponseEntity<String> response = restTemplate.exchange(
                    updateByQueryUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            // Parse response to get statistics
            JsonNode responseJson = objectMapper.readTree(response.getBody());
            int updated = responseJson.path("updated").asInt(0);
            int total = responseJson.path("total").asInt(0);

            if (total == 0) {
                log.info("No project staff documents found for userUuid: {}", userUuid);
            } else {
                log.info("Successfully updated {} out of {} documents for userUuid: {}",
                        updated, total, userUuid);
            }

        } catch (Exception e) {
            log.error("Error updating user status in Elasticsearch for userUuid: {}", userUuid, e);
            throw new RuntimeException("Failed to update user status in Elasticsearch", e);
        }
    }

}