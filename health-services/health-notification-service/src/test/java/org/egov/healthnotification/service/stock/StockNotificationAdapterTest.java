package org.egov.healthnotification.service.stock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;
import org.egov.common.models.stock.SenderReceiverType;
import org.egov.common.models.stock.Stock;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.service.FacilityUserService;
import org.egov.healthnotification.service.MdmsService;
import org.egov.healthnotification.web.models.MdmsV2Data;
import org.egov.healthnotification.web.models.NotificationEvent;
import org.egov.healthnotification.web.models.enums.NotificationChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockNotificationAdapterTest {

    @InjectMocks
    private StockNotificationAdapter adapter;

    @Mock
    private MdmsService mdmsService;

    @Mock
    private FacilityUserService facilityUserService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ═══════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════

    private Stock buildStock(String stockEntryType, String primaryRole, String status) {
        List<Field> fields = new java.util.ArrayList<>();
        fields.add(Field.builder().key(Constants.ADDITIONAL_FIELD_STOCK_ENTRY_TYPE).value(stockEntryType).build());
        fields.add(Field.builder().key(Constants.ADDITIONAL_FIELD_PRIMARY_ROLE).value(primaryRole).build());
        fields.add(Field.builder().key(Constants.ADDITIONAL_FIELD_SKU).value("ITN Nets").build());
        fields.add(Field.builder().key(Constants.ADDITIONAL_FIELD_MRN_NUMBER).value("MRN-001").build());
        if (status != null) {
            fields.add(Field.builder().key(Constants.ADDITIONAL_FIELD_STOCK_STATUS).value(status).build());
        }

        AdditionalFields additionalFields = AdditionalFields.builder()
                .fields(fields)
                .build();

        return Stock.builder()
                .id("stock-1")
                .tenantId("tenant1")
                .clientReferenceId("cref-1")
                .senderId("facility-sender")
                .senderType(SenderReceiverType.WAREHOUSE)
                .receiverId("facility-receiver")
                .receiverType(SenderReceiverType.WAREHOUSE)
                .quantity(50)
                .additionalFields(additionalFields)
                .build();
    }

    /** Legacy helper without status — for backward-compat tests */
    private Stock buildStock(String stockEntryType, String primaryRole) {
        return buildStock(stockEntryType, primaryRole, null);
    }

    private MdmsV2Data buildMdmsConfig(String eventType) {
        ObjectNode dataNode = objectMapper.createObjectNode();
        dataNode.put("campaignType", "PUSH-NOTIFICATION");

        ArrayNode localeArray = objectMapper.createArrayNode();
        localeArray.add("en_NG");
        dataNode.set("locale", localeArray);

        ArrayNode eventNotifications = objectMapper.createArrayNode();
        ObjectNode eventNode = objectMapper.createObjectNode();
        eventNode.put("eventType", eventType);
        eventNode.put("enabled", true);

        ArrayNode scheduledNotifications = objectMapper.createArrayNode();
        ObjectNode schedNode = objectMapper.createObjectNode();
        schedNode.put("templateCode", "STOCK_TEMPLATE_001");
        schedNode.put("enabled", true);
        scheduledNotifications.add(schedNode);
        eventNode.set("scheduledNotifications", scheduledNotifications);

        eventNotifications.add(eventNode);
        dataNode.set("eventNotifications", eventNotifications);

        return MdmsV2Data.builder().data(dataNode).build();
    }

    // ═══════════════════════════════════════════════════════
    //  ISSUED + status tests
    // ═══════════════════════════════════════════════════════

    @Test
    void issuedInTransit_notifiesReceiver() {
        Stock stock = buildStock("ISSUED", "SENDER", "IN_TRANSIT");
        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_STOCK_ISSUE);

        when(mdmsService.fetchNotificationConfigByProjectType(
                eq(Constants.CAMPAIGN_TYPE_PUSH_NOTIFICATION), eq("tenant1"))).thenReturn(config);
        when(facilityUserService.resolveFacilityName(any(), any(), any())).thenReturn("Warehouse A");

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "stock-create-topic");

        assertEquals(1, events.size());
        NotificationEvent event = events.get(0);
        assertEquals(Constants.EVENT_TYPE_STOCK_ISSUE, event.getEventType());
        assertEquals(Constants.ENTITY_TYPE_STOCK, event.getEntityType());
        assertEquals("stock-1", event.getEntityId());
        assertEquals(NotificationChannel.PUSH, event.getChannel());
        // ISSUED + IN_TRANSIT → notify receiver
        assertEquals("facility-receiver", event.getRecipientFacilityId());
    }

    @Test
    void issuedAccepted_notifiesSender() {
        Stock stock = buildStock("ISSUED", "RECEIVER", "ACCEPTED");
        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_STOCK_RECEIPT);

        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);
        when(facilityUserService.resolveFacilityName(any(), any(), any())).thenReturn("Fac");

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");

        assertEquals(1, events.size());
        // ISSUED + ACCEPTED → STOCK_RECEIVE, notify sender
        assertEquals(Constants.EVENT_TYPE_STOCK_RECEIPT, events.get(0).getEventType());
        assertEquals("facility-sender", events.get(0).getRecipientFacilityId());
        assertEquals(Constants.SCREEN_TRANSACTION_DETAILS, events.get(0).getData().get("screen"));
    }

    @Test
    void issuedRejected_notifiesSender() {
        Stock stock = buildStock("ISSUED", "RECEIVER", "REJECTED");
        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_STOCK_ISSUE_REJECT);

        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);
        when(facilityUserService.resolveFacilityName(any(), any(), any())).thenReturn("Fac");

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");

        assertEquals(1, events.size());
        // ISSUED + REJECTED → STOCK_ISSUE_REJECT, notify sender
        assertEquals(Constants.EVENT_TYPE_STOCK_ISSUE_REJECT, events.get(0).getEventType());
        assertEquals("facility-sender", events.get(0).getRecipientFacilityId());
        assertEquals(Constants.SCREEN_TRANSACTION_DETAILS, events.get(0).getData().get("screen"));
    }

    // ═══════════════════════════════════════════════════════
    //  RETURNED + status tests
    // ═══════════════════════════════════════════════════════

    @Test
    void returnedInTransit_notifiesReceiver() {
        Stock stock = buildStock("RETURNED", "SENDER", "IN_TRANSIT");
        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_STOCK_REVERSE_ISSUE);

        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);
        when(facilityUserService.resolveFacilityName(any(), any(), any())).thenReturn("Fac");

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");

        assertEquals(1, events.size());
        assertEquals(Constants.EVENT_TYPE_STOCK_REVERSE_ISSUE, events.get(0).getEventType());
        // RETURNED + IN_TRANSIT → notify receiver
        assertEquals("facility-receiver", events.get(0).getRecipientFacilityId());
        assertEquals(Constants.SCREEN_PENDING_RECEIPT, events.get(0).getData().get("screen"));
    }

    @Test
    void returnedAccepted_notifiesReceiver() {
        Stock stock = buildStock("RETURNED", "SENDER", "ACCEPTED");
        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_STOCK_REVERSE_ACCEPT);

        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);
        when(facilityUserService.resolveFacilityName(any(), any(), any())).thenReturn("Fac");

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");

        assertEquals(1, events.size());
        assertEquals(Constants.EVENT_TYPE_STOCK_REVERSE_ACCEPT, events.get(0).getEventType());
        // RETURNED + ACCEPTED → notify receiver
        assertEquals("facility-receiver", events.get(0).getRecipientFacilityId());
    }

    @Test
    void returnedRejected_notifiesReceiver() {
        Stock stock = buildStock("RETURNED", "SENDER", "REJECTED");
        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_STOCK_REVERSE_REJECT);

        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);
        when(facilityUserService.resolveFacilityName(any(), any(), any())).thenReturn("Fac");

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");

        assertEquals(1, events.size());
        assertEquals(Constants.EVENT_TYPE_STOCK_REVERSE_REJECT, events.get(0).getEventType());
        // RETURNED + REJECTED → notify receiver
        assertEquals("facility-receiver", events.get(0).getRecipientFacilityId());
    }

    // ═══════════════════════════════════════════════════════
    //  Navigation data tests
    // ═══════════════════════════════════════════════════════

    @Test
    void issuedInTransit_dataContainsCorrectNavigationInfo() {
        Stock stock = buildStock("ISSUED", "SENDER", "IN_TRANSIT");
        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_STOCK_ISSUE);

        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);
        when(facilityUserService.resolveFacilityName(any(), any(), any())).thenReturn("Fac");

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");

        Map<String, String> data = events.get(0).getData();
        assertEquals("STOCK", data.get("notificationType"));
        assertEquals(Constants.EVENT_TYPE_STOCK_ISSUE, data.get("eventType"));
        assertEquals("MRN-001", data.get("transactionRef"));
        assertEquals(Constants.SCREEN_PENDING_RECEIPT, data.get("screen"));
    }

    // ═══════════════════════════════════════════════════════
    //  Edge case / skip tests
    // ═══════════════════════════════════════════════════════

    @Test
    void noStockEntryType_returnsEmpty() {
        Stock stock = Stock.builder()
                .id("stock-2")
                .tenantId("tenant1")
                .additionalFields(AdditionalFields.builder().fields(List.of()).build())
                .build();

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");
        assertTrue(events.isEmpty());
    }

    @Test
    void nullAdditionalFields_returnsEmpty() {
        Stock stock = Stock.builder()
                .id("stock-3")
                .tenantId("tenant1")
                .build();

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");
        assertTrue(events.isEmpty());
    }

    @Test
    void unknownStockEntryType_returnsEmpty() {
        Stock stock = buildStock("UNKNOWN_TYPE", "SENDER", "IN_TRANSIT");

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");
        assertTrue(events.isEmpty());
    }

    @Test
    void issuedWithNoStatus_returnsEmpty() {
        // ISSUED without status → skipped because status is now required
        Stock stock = buildStock("ISSUED", "SENDER");

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");
        assertTrue(events.isEmpty());
    }

    @Test
    void returnedWithNoStatus_returnsEmpty() {
        // RETURNED without status → skipped because status is now required
        Stock stock = buildStock("RETURNED", "SENDER");

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");
        assertTrue(events.isEmpty());
    }

    @Test
    void noMdmsConfig_returnsEmpty() {
        Stock stock = buildStock("ISSUED", "SENDER", "IN_TRANSIT");

        when(mdmsService.fetchNotificationConfigByProjectType(any(), any()))
                .thenThrow(new RuntimeException("MDMS down"));

        assertThrows(RuntimeException.class, () -> adapter.buildNotificationEvents(stock, "topic"));
    }

    @Test
    void eventTypeDisabledInMdms_returnsEmpty() {
        Stock stock = buildStock("ISSUED", "SENDER", "IN_TRANSIT");

        // Build config with event disabled
        ObjectNode dataNode = objectMapper.createObjectNode();
        ArrayNode eventNotifications = objectMapper.createArrayNode();
        ObjectNode eventNode = objectMapper.createObjectNode();
        eventNode.put("eventType", Constants.EVENT_TYPE_STOCK_ISSUE);
        eventNode.put("enabled", false);
        eventNotifications.add(eventNode);
        dataNode.set("eventNotifications", eventNotifications);

        MdmsV2Data config = MdmsV2Data.builder().data(dataNode).build();
        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");
        assertTrue(events.isEmpty());
    }

    // ═══════════════════════════════════════════════════════
    //  Placeholder tests
    // ═══════════════════════════════════════════════════════

    @Test
    void placeholdersBuiltCorrectly() {
        Stock stock = buildStock("ISSUED", "SENDER", "IN_TRANSIT");
        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_STOCK_ISSUE);

        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);
        when(facilityUserService.resolveFacilityName(eq("facility-sender"), any(), any()))
                .thenReturn("Sender Warehouse");
        when(facilityUserService.resolveFacilityName(eq("facility-receiver"), any(), any()))
                .thenReturn("Receiver Warehouse");

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");

        Map<String, Object> placeholders = events.get(0).getPlaceholders();
        assertEquals("Sender Warehouse", placeholders.get(Constants.PLACEHOLDER_SENDING_FACILITY));
        assertEquals("Receiver Warehouse", placeholders.get(Constants.PLACEHOLDER_RECEIVING_FACILITY));
        assertEquals("MRN-001", placeholders.get(Constants.PLACEHOLDER_TRANSACTION_ID));
        assertEquals("50 ITN Nets", placeholders.get(Constants.PLACEHOLDER_QUANTITY_OF_SKU));
    }

    // ═══════════════════════════════════════════════════════
    //  mapToEventType unit tests
    // ═══════════════════════════════════════════════════════

    @Test
    void mapToEventType_issuedVariants() {
        assertEquals(Constants.EVENT_TYPE_STOCK_ISSUE, adapter.mapToEventType("ISSUED", "IN_TRANSIT"));
        assertEquals(Constants.EVENT_TYPE_STOCK_RECEIPT, adapter.mapToEventType("ISSUED", "ACCEPTED"));
        assertEquals(Constants.EVENT_TYPE_STOCK_ISSUE_REJECT, adapter.mapToEventType("ISSUED", "REJECTED"));
        assertNull(adapter.mapToEventType("ISSUED", null));
        assertNull(adapter.mapToEventType("ISSUED", "UNKNOWN"));
    }

    @Test
    void mapToEventType_returnedVariants() {
        assertEquals(Constants.EVENT_TYPE_STOCK_REVERSE_ISSUE, adapter.mapToEventType("RETURNED", "IN_TRANSIT"));
        assertEquals(Constants.EVENT_TYPE_STOCK_REVERSE_ACCEPT, adapter.mapToEventType("RETURNED", "ACCEPTED"));
        assertEquals(Constants.EVENT_TYPE_STOCK_REVERSE_REJECT, adapter.mapToEventType("RETURNED", "REJECTED"));
        assertNull(adapter.mapToEventType("RETURNED", null));
        assertNull(adapter.mapToEventType("RETURNED", "UNKNOWN"));
    }

    @Test
    void mapToEventType_legacyFallback() {
        // Legacy stockEntryTypes still map correctly (backward compat)
        assertEquals(Constants.EVENT_TYPE_STOCK_RECEIPT, adapter.mapToEventType("RECEIPT", null));
        assertEquals(Constants.EVENT_TYPE_STOCK_REVERSE_ACCEPT, adapter.mapToEventType("RETURN_ACCEPTED", null));
        assertEquals(Constants.EVENT_TYPE_STOCK_REVERSE_REJECT, adapter.mapToEventType("RETURN_REJECTED", null));
    }

    @Test
    void mapToEventType_damaged_returnsNull() {
        assertNull(adapter.mapToEventType("DAMAGED", null));
        assertNull(adapter.mapToEventType("DAMAGED", "IN_TRANSIT"));
    }

    @Test
    void mapToEventType_unknownType_returnsNull() {
        assertNull(adapter.mapToEventType("UNKNOWN", null));
        assertNull(adapter.mapToEventType("UNKNOWN", "IN_TRANSIT"));
    }

    // ═══════════════════════════════════════════════════════
    //  Title mapping test
    // ═══════════════════════════════════════════════════════

    @Test
    void mapEventTypeToTitle_allMappings() {
        assertEquals(Constants.TITLE_STOCK_ISSUE, adapter.mapEventTypeToTitle(Constants.EVENT_TYPE_STOCK_ISSUE));
        assertEquals(Constants.TITLE_STOCK_RECEIPT, adapter.mapEventTypeToTitle(Constants.EVENT_TYPE_STOCK_RECEIPT));
        assertEquals(Constants.TITLE_STOCK_ISSUE_REJECT, adapter.mapEventTypeToTitle(Constants.EVENT_TYPE_STOCK_ISSUE_REJECT));
        assertEquals(Constants.TITLE_STOCK_REVERSE_ISSUE, adapter.mapEventTypeToTitle(Constants.EVENT_TYPE_STOCK_REVERSE_ISSUE));
        assertEquals(Constants.TITLE_STOCK_REVERSE_ACCEPT, adapter.mapEventTypeToTitle(Constants.EVENT_TYPE_STOCK_REVERSE_ACCEPT));
        assertEquals(Constants.TITLE_STOCK_REVERSE_REJECT, adapter.mapEventTypeToTitle(Constants.EVENT_TYPE_STOCK_REVERSE_REJECT));
        assertEquals("CUSTOM_TYPE", adapter.mapEventTypeToTitle("CUSTOM_TYPE")); // default fallback
    }
}