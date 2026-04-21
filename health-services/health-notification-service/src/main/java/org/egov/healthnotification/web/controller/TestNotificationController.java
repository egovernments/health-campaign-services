package org.egov.healthnotification.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.stock.Stock;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.service.NotificationProcessorService;
import org.egov.healthnotification.service.stock.StockNotificationAdapter;
import org.egov.healthnotification.web.models.NotificationEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-only controller for simulating stock push notification flow.
 * Active only with the "hns-local" profile.
 *
 * Usage:
 *   POST /health-notification-service/test/v1/stock/_notify
 *   Body: the raw stock Kafka JSON array (same payload as the Kafka topic)
 */
@RestController
@RequestMapping("/test/v1/stock")
@Slf4j
public class TestNotificationController {

    private final StockNotificationAdapter stockAdapter;
    private final NotificationProcessorService processor;
    private final ObjectMapper objectMapper;

    @Autowired
    public TestNotificationController(StockNotificationAdapter stockAdapter,
                                       NotificationProcessorService processor,
                                       @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.stockAdapter = stockAdapter;
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/_notify")
    public ResponseEntity<Map<String, Object>> simulateStockNotification(@RequestBody String payload) {
        log.info("=== TEST: Simulating stock push notification flow ===");

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            JsonNode[] records = objectMapper.readValue(payload, JsonNode[].class);
            log.info("Received {} stock record(s) for testing", records.length);

            for (JsonNode record : records) {
                Map<String, Object> recordResult = new HashMap<>();
                String recordId = record.path("id").asText("unknown");
                String schema = record.path("additionalFields").path(Constants.ADDITIONAL_FIELD_SCHEMA).asText("");
                String stockEntryType = "unknown";

                // Extract stockEntryType from additionalFields.fields array
                JsonNode fields = record.path("additionalFields").path("fields");
                if (fields.isArray()) {
                    for (JsonNode field : fields) {
                        if ("stockEntryType".equals(field.path("key").asText())) {
                            stockEntryType = field.path("value").asText();
                            break;
                        }
                    }
                }

                recordResult.put("id", recordId);
                recordResult.put("schema", schema);
                recordResult.put("stockEntryType", stockEntryType);

                if (!Constants.SCHEMA_STOCK.equals(schema)) {
                    recordResult.put("status", "SKIPPED");
                    recordResult.put("reason", "Not a Stock schema: " + schema);
                    results.add(recordResult);
                    continue;
                }

                try {
                    Stock stock = objectMapper.treeToValue(record, Stock.class);
                    List<NotificationEvent> events = stockAdapter.buildNotificationEvents(stock, "test-topic");

                    recordResult.put("eventsBuilt", events.size());

                    if (events.isEmpty()) {
                        recordResult.put("status", "NO_EVENTS");
                        recordResult.put("reason", "Adapter returned no events (check stockEntryType mapping / MDMS config)");
                    } else {
                        List<Map<String, Object>> eventDetails = new ArrayList<>();
                        for (NotificationEvent event : events) {
                            Map<String, Object> detail = new HashMap<>();
                            detail.put("eventType", event.getEventType());
                            detail.put("entityType", event.getEntityType());
                            detail.put("templateCode", event.getTemplateCode());
                            detail.put("recipientFacilityId", event.getRecipientFacilityId());
                            detail.put("locale", event.getLocale());
                            detail.put("channel", event.getChannel());
                            detail.put("data", event.getData());
                            detail.put("placeholders", event.getPlaceholders());
                            eventDetails.add(detail);
                        }
                        recordResult.put("events", eventDetails);

                        // Actually process and send
                        processor.processAndSendBatch(events);
                        recordResult.put("status", "SENT");
                    }
                } catch (Exception e) {
                    recordResult.put("status", "ERROR");
                    recordResult.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                    log.error("Error processing stock record id={}: {}", recordId, e.getMessage(), e);
                }

                results.add(recordResult);
            }

            response.put("totalRecords", records.length);
            response.put("results", results);
            response.put("status", "completed");

        } catch (Exception e) {
            log.error("Error parsing stock payload: {}", e.getMessage(), e);
            response.put("status", "error");
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }

        log.info("=== TEST: Stock notification simulation complete ===");
        return ResponseEntity.ok(response);
    }
}