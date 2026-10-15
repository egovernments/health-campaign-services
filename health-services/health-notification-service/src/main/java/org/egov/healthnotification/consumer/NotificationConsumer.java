package org.egov.healthnotification.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.stock.Stock;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.service.NotificationProcessorService;
import org.egov.healthnotification.service.hfreferral.HFReferralNotificationAdapter;
import org.egov.healthnotification.service.stock.StockNotificationAdapter;
import org.egov.healthnotification.web.models.NotificationEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Generic Kafka consumer for push notification events.
 *
 * Listens to stock and HFReferral create/update topics via a single listener.
 * Routes each record to the appropriate adapter based on additionalFields.schema value:
 *   - "Stock"      → StockNotificationAdapter
 *   - "HFReferral" → HFReferralNotificationAdapter (stub for now)
 *
 * Each adapter translates the domain object into generic NotificationEvent(s),
 * which are then dispatched by NotificationProcessorService.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "notification.push.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationConsumer {

    private final StockNotificationAdapter stockAdapter;
    private final HFReferralNotificationAdapter hfReferralAdapter;
    private final NotificationProcessorService processor;
    private final ObjectMapper objectMapper;

    @Autowired
    public NotificationConsumer(StockNotificationAdapter stockAdapter,
                                HFReferralNotificationAdapter hfReferralAdapter,
                                NotificationProcessorService processor,
                                @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.stockAdapter = stockAdapter;
        this.hfReferralAdapter = hfReferralAdapter;
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topicPattern = "(${kafka.tenant.id.pattern}){0,1}(${stock.consumer.create.topic}|${stock.consumer.update.topic}|${hfreferral.consumer.create.topic}|${hfreferral.consumer.update.topic})")
    public void consumeNotificationEvent(ConsumerRecord<String, Object> payload,
                                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        processEvents(payload, topic);
    }

    // ═══════════════════════════════════════════════════════
    //  Common Processing — schema-based routing
    // ═══════════════════════════════════════════════════════

    /**
     * Deserializes the Kafka payload as a JSON array, checks each record's
     * additionalFields.schema, and routes to the appropriate adapter.
     */
    private void processEvents(ConsumerRecord<String, Object> payload, String topic) {
        try {
            log.info("Received event from topic: {}", topic);

            JsonNode[] records = objectMapper.readValue((String) payload.value(), JsonNode[].class);

            if (records == null || records.length == 0) {
                log.info("No records found in the message from topic: {}. Skipping.", topic);
                return;
            }

            log.info("Received {} record(s) from topic: {}", records.length, topic);

            int successCount = 0;
            int failureCount = 0;

            for (JsonNode record : records) {
                try {
                    String schema = record.path("additionalFields").path(Constants.ADDITIONAL_FIELD_SCHEMA).asText("");

                    List<NotificationEvent> events = routeBySchema(record, schema, topic);
                    if (events != null && !events.isEmpty()) {
                        processor.processAndSendBatch(events);
                        successCount += events.size();
                    }
                } catch (Exception e) {
                    failureCount++;
                    String recordId = record.path("id").asText("unknown");
                    log.error("Failed to process record id={} from topic={}: {}",
                            recordId, topic, e.getMessage(), e);
                }
            }

            log.info("Event processing completed for topic: {}. {} event(s) dispatched, {} failure(s)",
                    topic, successCount, failureCount);

        } catch (Exception e) {
            log.error("Error deserializing/processing events from topic: {}", topic, e);
        }
    }

    /**
     * Routes a record to the appropriate adapter based on schema value.
     *
     * @param record The raw JSON record
     * @param schema The additionalFields.schema value (e.g., "Stock", "HFReferral")
     * @param topic  The Kafka topic
     * @return List of NotificationEvents built by the adapter
     */
    private List<NotificationEvent> routeBySchema(JsonNode record, String schema, String topic) throws Exception {
        switch (schema) {
            case Constants.SCHEMA_STOCK:
                Stock stock = objectMapper.treeToValue(record, Stock.class);
                return stockAdapter.buildNotificationEvents(stock, topic);

            case Constants.SCHEMA_HF_REFERRAL:
                return hfReferralAdapter.buildNotificationEvents(record, topic);

            default:
                log.warn("Unknown schema: {} in record from topic: {}. Skipping.", schema, topic);
                return List.of();
        }
    }
}
