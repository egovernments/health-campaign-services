package org.egov.transformer.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.transformer.aggregator.config.ServiceConfiguration;
import org.egov.transformer.aggregator.models.ElasticsearchHit;
import org.egov.transformer.aggregator.models.UserActionCompositeKey;
import org.egov.transformer.aggregator.models.UserActionLocationCaptureIndexRecord;
import org.egov.transformer.aggregator.repository.ElasticSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.transformer.aggregator.config.ServiceConstants.USER_LOCATION_CAPTURE_ID;

@Component
@Slf4j
public class UserActionLocationCaptureAggregationService {

    private final ServiceConfiguration config;
    private final ElasticSearchRepository elasticSearchRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public UserActionLocationCaptureAggregationService(ServiceConfiguration config, ElasticSearchRepository elasticSearchRepository, ObjectMapper objectMapper) {
        this.config = config;
        this.elasticSearchRepository = elasticSearchRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Process a list of UserAction location captures by grouping them by a composite key
     * and then updating or creating the respective entries in Elasticsearch.
     */
    public void processUserActionLocationCapture(List<UserAction> userActionPayloadList) {
        // Grouping UserAction list by a composite key (projectId, tenantId, clientCreatedBy, clientCreatedDate)
        Map<UserActionCompositeKey, List<UserAction>> groupByCompositeKey =
                userActionPayloadList.stream().collect(
                        Collectors.groupingBy(
                                userAction -> new UserActionCompositeKey(
                                        userAction.getProjectId(),
                                        userAction.getTenantId(),
                                        userAction.getClientAuditDetails() != null ? userAction.getClientAuditDetails().getCreatedBy() : null,
                                        userAction.getClientAuditDetails() != null ? getDateFromTimeStamp(userAction.getClientAuditDetails().getCreatedTime()) : null
                                )
                        )
                );

        // For each group, fetch or initialize a location capture entry and update it
        groupByCompositeKey.forEach((userActionCompositeKey, userActionList) -> {
            ElasticsearchHit<UserActionLocationCaptureIndexRecord> esHit = fetchOrInitializeLocationCaptureEntry(userActionCompositeKey, userActionList);
            log.info("PROCESS HOUSEHOLD ::: SEQ_NO ::: {}  PRIMARY_TERM ::: {}", esHit.getSeqNo(), esHit.getPrimaryTerm());
            updateAggregateLocationCaptureEntry(esHit, userActionCompositeKey, userActionList);
        });
    }

    /**
     * Updates an existing location capture entry or initializes a new one if it doesn't exist.
     */
    private void updateAggregateLocationCaptureEntry(
            ElasticsearchHit<UserActionLocationCaptureIndexRecord> esHit,
            UserActionCompositeKey userActionCompositeKey,
            List<UserAction> userActionList
    ) {
        UserActionLocationCaptureIndexRecord existingRecord = esHit.getSource();
        UserActionLocationCaptureIndexRecord updatedRecord;

        if (existingRecord != null) {
            // If the entry already exists, update the geoJson with new coordinates and accuracy
            updatedRecord = existingRecord;
            JsonNode existingGeoJson = existingRecord.getGeoJson();

            // Extract existing coordinates and accuracy arrays
            List<List<Double>> existingCoordinates = objectMapper.convertValue(
                    existingGeoJson.at("/geometry/coordinates").get(0),
                    new TypeReference<List<List<Double>>>() {}
            );
            List<Double> existingAccuracy = objectMapper.convertValue(
                    existingGeoJson.at("/properties/accuracy"),
                    new TypeReference<List<Double>>() {}
            );

            // Add new coordinates and accuracy from the incoming userActionList
            for (UserAction userAction : userActionList) {
                List<Double> newCoordinates = List.of(userAction.getLatitude(), userAction.getLongitude());
                Double newAccuracy = userAction.getLocationAccuracy();
                existingCoordinates.add(newCoordinates);
                existingAccuracy.add(newAccuracy);
            }

            // Rebuild the updated GeoJSON
            String updatedJsonString = String.format("{"
                    + "\"type\": \"Feature\","
                    + "\"geometry\": {"
                    + "\"type\": \"Polygon\","
                    + "\"coordinates\": [%s]"
                    + "},"
                    + "\"properties\": {"
                    + "\"accuracy\": %s"
                    + "}"
                    + "}", existingCoordinates.toString(), existingAccuracy.toString());

            try {
                updatedRecord.setGeoJson(objectMapper.readTree(updatedJsonString));
            } catch (Exception e) {
                log.error("Failed to update GeoJSON: ", e);
            }

        } else {
            // If no existing entry, initialize a new record
            updatedRecord = initLocationCaptureEntry(userActionCompositeKey, userActionList);
        }

        // Update or create the record in Elasticsearch
        elasticSearchRepository.createOrUpdateDocument(
                updatedRecord, config.getUserActionLocationCaptureIndex(), USER_LOCATION_CAPTURE_ID, esHit.getSeqNo(), esHit.getPrimaryTerm()
        );
    }



    /**
     * Convert a timestamp (in milliseconds) to a formatted date string (yyyyMMdd).
     */
    private String getDateFromTimeStamp(Long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    /**
     * Fetch an existing location capture entry from Elasticsearch or initialize a new one if not found.
     */
    private ElasticsearchHit<UserActionLocationCaptureIndexRecord> fetchOrInitializeLocationCaptureEntry(UserActionCompositeKey userActionCompositeKey, List<UserAction> userActionList) {
        Optional<ElasticsearchHit<UserActionLocationCaptureIndexRecord>> hit = fetchLocationCaptureEntry(userActionCompositeKey);
        return hit.orElseGet(() -> new ElasticsearchHit<>(0L, 0L, initLocationCaptureEntry(userActionCompositeKey, userActionList)));
    }

    /**
     * Initialize a new UserActionLocationCaptureIndexRecord with the given composite key and list of UserAction.
     */
    private UserActionLocationCaptureIndexRecord initLocationCaptureEntry(UserActionCompositeKey userActionCompositeKey, List<UserAction> userActionList) {
        UserAction userAction = null;
        if (!CollectionUtils.isEmpty(userActionList)) {
            userAction = userActionList.get(0);
        }

        return UserActionLocationCaptureIndexRecord.builder()
                .id(userActionCompositeKey.getId())
                .projectId(userActionCompositeKey.getProjectId())
                .tenantId(userActionCompositeKey.getTenantId())
                .boundaryCode(userAction != null ? userAction.getBoundaryCode() : null)
                .clientCreatedBy(userActionCompositeKey.getClientCreatedBy())
                .clientCreatedDate(userActionCompositeKey.getClientCreatedDate())
                .geoJson(buildGeoJson(userActionList))
                .build();
    }

    /**
     * Build a GeoJSON structure based on the provided list of UserAction objects.
     */
    public JsonNode buildGeoJson(List<UserAction> userActionList) {
        if (userActionList.isEmpty()) {
            // Handle empty list case if needed
            return null;
        }

        // Build coordinates array using streams
        String coordinatesJson = userActionList.stream()
                .map(userAction -> String.format("[%f, %f]", userAction.getLatitude(), userAction.getLongitude()))
                .collect(Collectors.joining(", ", "[", "]"));

        // Build accuracy array using streams
        String accuracyJson = userActionList.stream()
                .map(userAction -> String.format("%f", userAction.getLocationAccuracy()))
                .collect(Collectors.joining(", ", "[", "]"));

        // Construct the GeoJSON string
        String jsonString = String.format("{"
                + "\"type\": \"Feature\","
                + "\"geometry\": {"
                + "\"type\": \"Polygon\","
                + "\"coordinates\": [%s]"
                + "},"
                + "\"properties\": {"
                + "\"accuracy\": %s"
                + "}"
                + "}", coordinatesJson, accuracyJson);

        try {
            // Parse JSON string to JsonNode
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            log.error("Failed to build GeoJSON: ", e);
            return null; // Handle exception properly in production code
        }
    }

    /**
     * Fetch an existing location capture entry from Elasticsearch based on the composite key.
     */
    private Optional<ElasticsearchHit<UserActionLocationCaptureIndexRecord>> fetchLocationCaptureEntry(UserActionCompositeKey userActionCompositeKey) {
        return elasticSearchRepository.findBySearchValueAndWithSeqNo(
                userActionCompositeKey.getId(),
                config.getAggregatedHouseholdIndex(),
                USER_LOCATION_CAPTURE_ID,
                new TypeReference<>() {
                });
    }

}
