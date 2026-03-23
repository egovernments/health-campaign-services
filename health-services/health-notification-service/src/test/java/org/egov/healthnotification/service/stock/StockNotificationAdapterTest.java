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
import org.junit.jupiter.api.BeforeEach;
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

    private Stock buildStock(String stockEntryType, String primaryRole) {
        List<Field> fields = new java.util.ArrayList<>();
        fields.add(Field.builder().key(Constants.ADDITIONAL_FIELD_STOCK_ENTRY_TYPE).value(stockEntryType).build());
        fields.add(Field.builder().key(Constants.ADDITIONAL_FIELD_PRIMARY_ROLE).value(primaryRole).build());
        fields.add(Field.builder().key(Constants.ADDITIONAL_FIELD_SKU).value("ITN Nets").build());
        fields.add(Field.builder().key(Constants.ADDITIONAL_FIELD_MRN_NUMBER).value("MRN-001").build());

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

    @Test
    void buildNotificationEvents_stockIssue_buildsSingleEvent() {
        Stock stock = buildStock("ISSUED", "SENDER");
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
        assertEquals("STOCK_TEMPLATE_001", event.getTemplateCode());
        assertEquals("en_NG", event.getLocale());
        assertEquals(NotificationChannel.PUSH, event.getChannel());
        // SENDER → notify receiver
        assertEquals("facility-receiver", event.getRecipientFacilityId());
    }

    @Test
    void buildNotificationEvents_stockIssue_dataContainsNotificationType() {
        Stock stock = buildStock("ISSUED", "SENDER");
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

    @Test
    void buildNotificationEvents_stockReceipt_receiverRole_notifiesSender() {
        Stock stock = buildStock("RECEIPT", "RECEIVER");
        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_STOCK_RECEIPT);

        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);
        when(facilityUserService.resolveFacilityName(any(), any(), any())).thenReturn("Fac");

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");

        assertEquals(1, events.size());
        // RECEIVER → notify sender
        assertEquals("facility-sender", events.get(0).getRecipientFacilityId());
        assertEquals(Constants.SCREEN_TRANSACTION_DETAILS, events.get(0).getData().get("screen"));
    }

    @Test
    void buildNotificationEvents_noStockEntryType_returnsEmpty() {
        Stock stock = Stock.builder()
                .id("stock-2")
                .tenantId("tenant1")
                .additionalFields(AdditionalFields.builder().fields(List.of()).build())
                .build();

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");

        assertTrue(events.isEmpty());
    }

    @Test
    void buildNotificationEvents_nullAdditionalFields_returnsEmpty() {
        Stock stock = Stock.builder()
                .id("stock-3")
                .tenantId("tenant1")
                .build();

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");

        assertTrue(events.isEmpty());
    }

    @Test
    void buildNotificationEvents_unknownStockEntryType_returnsEmpty() {
        Stock stock = buildStock("UNKNOWN_TYPE", "SENDER");

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");

        assertTrue(events.isEmpty());
    }

    @Test
    void buildNotificationEvents_noMdmsConfig_returnsEmpty() {
        Stock stock = buildStock("ISSUED", "SENDER");

        when(mdmsService.fetchNotificationConfigByProjectType(any(), any()))
                .thenThrow(new RuntimeException("MDMS down"));

        List<NotificationEvent> events = adapter.buildNotificationEvents(stock, "topic");

        assertTrue(events.isEmpty());
    }

    @Test
    void buildNotificationEvents_eventTypeDisabledInMdms_returnsEmpty() {
        Stock stock = buildStock("ISSUED", "SENDER");

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

    @Test
    void buildNotificationEvents_placeholdersBuiltCorrectly() {
        Stock stock = buildStock("ISSUED", "SENDER");
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

    @Test
    void mapStockEntryTypeToEventType_allMappings() {
        assertEquals(Constants.EVENT_TYPE_STOCK_ISSUE, adapter.mapStockEntryTypeToEventType("ISSUED"));
        assertEquals(Constants.EVENT_TYPE_STOCK_RECEIPT, adapter.mapStockEntryTypeToEventType("RECEIPT"));
        assertEquals(Constants.EVENT_TYPE_STOCK_REVERSE_ISSUE, adapter.mapStockEntryTypeToEventType("RETURNED"));
        assertEquals(Constants.EVENT_TYPE_STOCK_REVERSE_ACCEPT, adapter.mapStockEntryTypeToEventType("RETURN_ACCEPTED"));
        assertEquals(Constants.EVENT_TYPE_STOCK_REVERSE_REJECT, adapter.mapStockEntryTypeToEventType("RETURN_REJECTED"));
        assertNull(adapter.mapStockEntryTypeToEventType("UNKNOWN"));
    }

    @Test
    void buildNotificationEvents_screenNavigationMapping() {
        // Test RETURNED → PENDING_RECEIPT_SCREEN
        Stock stockReturned = buildStock("RETURNED", "SENDER");
        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_STOCK_REVERSE_ISSUE);
        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);
        when(facilityUserService.resolveFacilityName(any(), any(), any())).thenReturn("Fac");

        List<NotificationEvent> events = adapter.buildNotificationEvents(stockReturned, "topic");
        assertEquals(Constants.SCREEN_PENDING_RECEIPT, events.get(0).getData().get("screen"));
    }
}