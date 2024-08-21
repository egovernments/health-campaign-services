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

import static org.egov.transformer.aggregator.config.ServiceConstants.AGG_HOUSEHOLD_ID;
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

    public void processUserActionLocationCapture(List<UserAction> userActionPayloadList) {
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

        groupByCompositeKey.forEach((userActionCompositeKey, userActionList) -> {
            ElasticsearchHit<UserActionLocationCaptureIndexRecord> esHit = fetchOrInitializeLocationCaptureEntry(userActionCompositeKey, userActionList);

            log.info("PROCESS HOUSEHOLD ::: SEQ_NO ::: {}  PRIMARY_TERM ::: {}", esHit.getSeqNo(), esHit.getPrimaryTerm());



        });
    }

    private String getDateFromTimeStamp(Long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    }

    private ElasticsearchHit<UserActionLocationCaptureIndexRecord> fetchOrInitializeLocationCaptureEntry(UserActionCompositeKey userActionCompositeKey, List<UserAction> userActionList) {
        Optional<ElasticsearchHit<UserActionLocationCaptureIndexRecord>> hit = fetchLocationCaptureEntry(userActionCompositeKey);
        return hit.orElseGet(
                () -> new ElasticsearchHit<>(0L, 0L, initLocationCaptureEntry(userActionCompositeKey, userActionList)));
    }

    private UserActionLocationCaptureIndexRecord initLocationCaptureEntry(UserActionCompositeKey userActionCompositeKey, List<UserAction> userActionList) {
        UserAction userAction = null;
        if(!CollectionUtils.isEmpty(userActionList)) {
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
                + "\"coordinates\": ["
                + "[%s]"
                + "]"
                + "},"
                + "\"properties\": {"
                + "\"accuracy\": %s"
                + "}"
                + "}", coordinatesJson, accuracyJson);

        try {
            // Parse JSON string to JsonNode
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            // TODO send to CustomException
//            e.printStackTrace(); // Handle exception properly in production code
            return null; // Or throw a custom exception
        }
    }


    private Optional<ElasticsearchHit<UserActionLocationCaptureIndexRecord>> fetchLocationCaptureEntry(UserActionCompositeKey userActionCompositeKey) {
        return elasticSearchRepository.findBySearchValueAndWithSeqNo(
                userActionCompositeKey.getId(),
                config.getAggregatedHouseholdIndex(),
                USER_LOCATION_CAPTURE_ID,
                new TypeReference<>() {
                });
    }

}
