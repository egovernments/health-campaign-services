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
import org.egov.tracer.model.CustomException;
import org.egov.transformer.aggregator.config.ServiceConfiguration;
import org.egov.transformer.aggregator.models.ElasticsearchHit;
import org.egov.transformer.aggregator.models.UserActionCompositeKey;
import org.egov.transformer.aggregator.models.UserActionLocationCaptureIndexRecord;
import org.egov.transformer.aggregator.repository.ElasticSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.transformer.aggregator.config.ServiceConstants.USER_LOCATION_CAPTURE_ID;

/**
 * Service class responsible for aggregating and processing user action location captures.
 * The service groups user actions by a composite key and then updates or creates the respective entries in Elasticsearch.
 */
@Component
@Slf4j
public class UserActionLocationCaptureAggregationService {

    // Service configuration instance
    private final ServiceConfiguration config;
    // Elasticsearch repository for interacting with Elasticsearch
    private final ElasticSearchRepository elasticSearchRepository;
    // ObjectMapper for JSON processing
    private final ObjectMapper objectMapper;

    /**
     * Constructor for dependency injection.
     *
     * @param config                  Service configuration.
     * @param elasticSearchRepository Elasticsearch repository.
     * @param objectMapper            ObjectMapper for JSON handling.
     */
    @Autowired
    public UserActionLocationCaptureAggregationService(ServiceConfiguration config, ElasticSearchRepository elasticSearchRepository, ObjectMapper objectMapper) {
        this.config = config;
        this.elasticSearchRepository = elasticSearchRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Process a list of UserAction location captures by grouping them by a composite key
     * and then updating or creating the respective entries in Elasticsearch.
     *
     * @param userActionPayloadList List of UserAction objects to process.
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
            // Fetch or initialize location capture entry for the composite key
            ElasticsearchHit<UserActionLocationCaptureIndexRecord> esHit = fetchOrInitializeLocationCaptureEntry(userActionCompositeKey, userActionList);
            log.info("PROCESSING USER ACTION LOCATION CAPTURE ::: SEQ_NO ::: {}  PRIMARY_TERM ::: {}", esHit.getSeqNo(), esHit.getPrimaryTerm());
            // Update the aggregated location capture entry with the current user actions
            updateAggregateLocationCaptureEntry(esHit, userActionCompositeKey, userActionList);
        });
    }

    /**
     * Updates an existing location capture entry or initializes a new one if it doesn't exist.
     *
     * @param esHit                The Elasticsearch hit containing the current record or a new initialized record.
     * @param userActionCompositeKey The composite key used for identifying the record.
     * @param userActionList       List of UserAction objects to be aggregated.
     */
    private void updateAggregateLocationCaptureEntry(
            ElasticsearchHit<UserActionLocationCaptureIndexRecord> esHit,
            UserActionCompositeKey userActionCompositeKey,
            List<UserAction> userActionList
    ) {
        // Existing record fetched from Elasticsearch
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
                // Update the GeoJSON in the record
                updatedRecord.setGeoJson(objectMapper.readTree(updatedJsonString));
                log.info("Updated GeoJSON for user action location capture entry with ID: {}", userActionCompositeKey.getId());
            } catch (Exception e) {
                log.error("Failed to update GeoJSON for user action location capture entry: ", e);
            }

        } else {
            // If no existing entry, initialize a new record
            updatedRecord = initLocationCaptureEntry(userActionCompositeKey, userActionList);
            log.info("Initialized new user action location capture entry for composite key: {}", userActionCompositeKey.getId());
        }

        // Update or create the record in Elasticsearch
        elasticSearchRepository.createOrUpdateDocument(
                updatedRecord, config.getUserActionLocationCaptureIndex(), USER_LOCATION_CAPTURE_ID, esHit.getSeqNo(), esHit.getPrimaryTerm()
        );
        log.info("Updated/Created user action location capture entry in Elasticsearch for composite key: {}", userActionCompositeKey.getId());
    }

    /**
     * Convert a timestamp (in milliseconds) to a formatted date string (yyyyMMdd).
     *
     * @param timestamp The timestamp in milliseconds.
     * @return The formatted date string.
     */
    private String getDateFromTimeStamp(Long timestamp) {
        // Convert the timestamp to a formatted date string
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    /**
     * Fetch an existing location capture entry from Elasticsearch or initialize a new one if not found.
     *
     * @param userActionCompositeKey The composite key used for fetching the record.
     * @param userActionList         The list of UserAction objects.
     * @return An ElasticsearchHit containing the record or a new initialized record.
     */
    private ElasticsearchHit<UserActionLocationCaptureIndexRecord> fetchOrInitializeLocationCaptureEntry(UserActionCompositeKey userActionCompositeKey, List<UserAction> userActionList) {
        // Fetch existing record from Elasticsearch
        Optional<ElasticsearchHit<UserActionLocationCaptureIndexRecord>> hit = fetchLocationCaptureEntry(userActionCompositeKey);
        // Return the existing record or initialize a new one if not found
        return hit.orElseGet(() -> {
            log.info("No existing record found for composite key: {}. Initializing a new entry.", userActionCompositeKey.getId());
            return new ElasticsearchHit<>(0L, 0L, initLocationCaptureEntry(userActionCompositeKey, userActionList));
        });
    }

    /**
     * Initialize a new UserActionLocationCaptureIndexRecord with the given composite key and list of UserAction.
     *
     * @param userActionCompositeKey The composite key used for identifying the record.
     * @param userActionList         The list of UserAction objects.
     * @return A new UserActionLocationCaptureIndexRecord instance.
     */
    private UserActionLocationCaptureIndexRecord initLocationCaptureEntry(UserActionCompositeKey userActionCompositeKey, List<UserAction> userActionList) {
        UserAction userAction = null;
        if (!CollectionUtils.isEmpty(userActionList)) {
            userAction = userActionList.get(0);
        }

        // Initialize a new record with the provided data
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
     * Build a GeoJSON object from a list of UserAction objects.
     *
     * @param userActionList List of UserAction objects.
     * @return A JsonNode representing the GeoJSON.
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
            // Parse the GeoJSON string into a JsonNode
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            // Log and throw an exception if GeoJSON building fails
            log.error("Failed to build GeoJSON for user action location capture: ", e);
            throw new CustomException("GEOJSON_BUILD_ERROR", "Failed to build GeoJSON for user action location capture: " + e.getMessage());
        }
    }

    /**
     * Fetch an existing location capture entry from Elasticsearch by composite key.
     *
     * @param userActionCompositeKey The composite key used for fetching the record.
     * @return An Optional containing the ElasticsearchHit with the record if found, empty otherwise.
     */
    private Optional<ElasticsearchHit<UserActionLocationCaptureIndexRecord>> fetchLocationCaptureEntry(UserActionCompositeKey userActionCompositeKey) {
        return elasticSearchRepository.findBySearchValueAndWithSeqNo(
                userActionCompositeKey.getId(),
                config.getUserActionLocationCaptureIndex(),
                USER_LOCATION_CAPTURE_ID,
                new TypeReference<>() {
                });
    }

}
