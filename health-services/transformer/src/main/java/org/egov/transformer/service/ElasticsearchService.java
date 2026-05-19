package org.egov.transformer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
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

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        return "http://" + properties.getEsHostName() + ":" + properties.getEsPortNo();
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

            // Step 1: Search for all documents with matching userId
            List<String> documentIds = searchDocumentsByUserId(userUuid, tenantId);

            if (documentIds.isEmpty()) {
                log.info("No project staff documents found for userUuid: {}", userUuid);
                return;
            }

            log.info("Found {} project staff documents for userUuid: {}", documentIds.size(), userUuid);

            // Step 2: Update each document
            for (String docId : documentIds) {
                updateDocument(indexName, docId, active, effectiveDate);
            }

            log.info("Successfully updated {} documents for userUuid: {}", documentIds.size(), userUuid);

        } catch (Exception e) {
            log.error("Error updating user status in Elasticsearch for userUuid: {}", userUuid, e);
            throw new RuntimeException("Failed to update user status in Elasticsearch", e);
        }
    }

    /**
     * Search for documents in project-staff-index-v1 by userId
     *
     * @param userId   User UUID to search for
     * @param tenantId Tenant ID (not used in query since it's null in documents)
     * @return List of document IDs
     */
    private List<String> searchDocumentsByUserId(String userId, String tenantId) {
        List<String> documentIds = new ArrayList<>();

        try {
            String searchUrl = String.format("%s/%s/_search",
                    getESBaseUrl(),
                    properties.getProjectStaffIndexName());

            // Build search query to find all docs with matching userId
            // Note: tenantId is null in the documents, so we only match on userId
            ObjectNode searchQuery = objectMapper.createObjectNode();
            ObjectNode query = objectMapper.createObjectNode();
            ObjectNode term = objectMapper.createObjectNode();

            // Match userId using term query (exact match on keyword field)
            term.put("Data.userId.keyword", userId);
            query.set("term", term);
            searchQuery.set("query", query);
            searchQuery.put("size", 1000); // Fetch up to 1000 matching documents

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Authorization", getESEncodedCredentials());
            HttpEntity<String> requestEntity = new HttpEntity<>(searchQuery.toString(), headers);

            log.debug("Elasticsearch search query: {}", searchQuery.toString());

            ResponseEntity<String> response = restTemplate.exchange(
                    searchUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            // Parse response and extract document IDs
            JsonNode responseJson = objectMapper.readTree(response.getBody());
            JsonNode hits = responseJson.path("hits").path("hits");

            if (hits.isArray()) {
                for (JsonNode hit : hits) {
                    String docId = hit.path("_id").asText();
                    documentIds.add(docId);
                }
            }

            log.debug("Found {} documents matching userId: {}", documentIds.size(), userId);

        } catch (Exception e) {
            log.error("Error searching documents by userId: {}", userId, e);
            throw new RuntimeException("Failed to search documents in Elasticsearch", e);
        }

        return documentIds;
    }

    /**
     * Update a single document with userActive and statusHistory
     *
     * @param indexName     Index name
     * @param documentId    Document ID to update
     * @param active        New active status
     * @param effectiveDate Effective date of status change
     */
    private void updateDocument(String indexName, String documentId, Boolean active, Long effectiveDate) {
        try {
            String updateUrl = String.format("%s/%s/_update/%s",
                    getESBaseUrl(),
                    indexName,
                    documentId);

            // Build update script to set userActive and append to statusHistory
            ObjectNode updateRequest = objectMapper.createObjectNode();
            ObjectNode script = objectMapper.createObjectNode();

            // Painless script to update userActive and append to statusHistory
            String painlessScript =
                "ctx._source.Data.userActive = params.active; " +
                "if (ctx._source.Data.statusHistory == null) { " +
                "  ctx._source.Data.statusHistory = []; " +
                "} " +
                "ctx._source.Data.statusHistory.add(params.statusEntry);";

            script.put("source", painlessScript);
            script.put("lang", "painless");

            // Add parameters
            ObjectNode params = objectMapper.createObjectNode();
            params.put("active", active);

            ObjectNode statusEntry = objectMapper.createObjectNode();
            statusEntry.put("active", active);
            statusEntry.put("effectiveDate", effectiveDate);

            params.set("statusEntry", statusEntry);
            script.set("params", params);

            updateRequest.set("script", script);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Authorization", getESEncodedCredentials());
            HttpEntity<String> requestEntity = new HttpEntity<>(updateRequest.toString(), headers);

            log.debug("Elasticsearch update request for doc {}: {}", documentId, updateRequest.toString());

            ResponseEntity<String> response = restTemplate.exchange(
                    updateUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            log.debug("Document {} updated successfully: {}", documentId, response.getBody());

        } catch (Exception e) {
            log.error("Error updating document: {}", documentId, e);
            throw new RuntimeException("Failed to update document in Elasticsearch", e);
        }
    }
}
