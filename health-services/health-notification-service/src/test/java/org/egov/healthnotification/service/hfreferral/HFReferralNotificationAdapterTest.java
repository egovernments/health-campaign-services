package org.egov.healthnotification.service.hfreferral;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.egov.healthnotification.web.models.NotificationEvent;
import org.junit.jupiter.api.Test;

class HFReferralNotificationAdapterTest {

    private final HFReferralNotificationAdapter adapter = new HFReferralNotificationAdapter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildNotificationEvents_stub_returnsEmptyList() {
        ObjectNode record = objectMapper.createObjectNode();
        record.put("id", "ref-1");
        record.put("clientReferenceId", "cref-1");

        List<NotificationEvent> events = adapter.buildNotificationEvents(record, "hfreferral-create-topic");

        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    void buildNotificationEvents_missingFields_returnsEmptyGracefully() {
        ObjectNode record = objectMapper.createObjectNode();

        List<NotificationEvent> events = adapter.buildNotificationEvents(record, "topic");

        assertNotNull(events);
        assertTrue(events.isEmpty());
    }
}