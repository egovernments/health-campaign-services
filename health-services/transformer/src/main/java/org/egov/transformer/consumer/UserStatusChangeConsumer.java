package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.Constants;
import org.egov.transformer.service.ElasticsearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class UserStatusChangeConsumer {

    private final ObjectMapper objectMapper;
    private final ElasticsearchService elasticsearchService;

    private static final String USER_UUID_KEY = "userUuid";
    private static final String TENANT_ID_KEY = "tenantId";
    private static final String ACTIVE_KEY = Constants.ES_FIELD_ACTIVE;
    private static final String EFFECTIVE_DATE_KEY = Constants.ES_FIELD_EFFECTIVE_DATE;

    @Autowired
    public UserStatusChangeConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper,
                                    ElasticsearchService elasticsearchService) {
        this.objectMapper = objectMapper;
        this.elasticsearchService = elasticsearchService;
    }

    @KafkaListener(topics = { "${transformer.consumer.user.status.change.topic}" })
    public void consumeUserStatusChange(ConsumerRecord<String, Object> payload,
                                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            log.info("Received user status change message from topic: {}", topic);
            String message = (String) payload.value();
            log.debug("User status change payload: {}", message);

            // Parse the message into a Map
            Map<String, String> userStatusChangeEvent = objectMapper.readValue(message, Map.class);

            // Extract required fields
            String userUuid = userStatusChangeEvent.get(USER_UUID_KEY);
            String tenantId = userStatusChangeEvent.get(TENANT_ID_KEY);
            String activeStr = userStatusChangeEvent.get(ACTIVE_KEY);
            String effectiveDateStr = userStatusChangeEvent.get(EFFECTIVE_DATE_KEY);

            // Validate required fields
            if (userUuid == null || tenantId == null || activeStr == null || effectiveDateStr == null) {
                log.error("Missing required fields in user status change event: {}", message);
                return;
            }

            Boolean active = Boolean.parseBoolean(activeStr);
            Long effectiveDate = Long.parseLong(effectiveDateStr);

            log.info("Processing user status change: userUuid={}, tenantId={}, active={}, effectiveDate={}",
                    userUuid, tenantId, active, effectiveDate);

            // Update Elasticsearch documents
            elasticsearchService.updateUserStatusInProjectStaff(userUuid, tenantId, active, effectiveDate);

            log.info("Successfully processed user status change for userUuid: {}", userUuid);

        } catch (Exception exception) {
            log.error("Error processing user status change event", exception);
            // Note: In production, you might want to add retry logic or send to a dead-letter queue
        }
    }
}