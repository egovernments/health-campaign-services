package org.egov.healthnotification.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.healthnotification.service.NotificationProcessorService;
import org.egov.healthnotification.service.hfreferral.HFReferralNotificationAdapter;
import org.egov.healthnotification.service.stock.StockNotificationAdapter;
import org.egov.healthnotification.web.models.NotificationEvent;
import org.egov.healthnotification.web.models.enums.NotificationChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockNotificationConsumerTest {

    @InjectMocks
    private StockNotificationConsumer consumer;

    @Mock
    private StockNotificationAdapter stockAdapter;

    @Mock
    private HFReferralNotificationAdapter hfReferralAdapter;

    @Mock
    private NotificationProcessorService processor;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void consumeNotificationEvent_stockSchema_routesToStockAdapter() {
        String payload = "[{\"id\":\"stock-1\",\"tenantId\":\"tenant1\",\"additionalFields\":{\"schema\":\"Stock\",\"fields\":[{\"key\":\"stockEntryType\",\"value\":\"ISSUED\"}]}}]";

        ConsumerRecord<String, Object> record = new ConsumerRecord<>("stock-create-topic", 0, 0, null, payload);

        NotificationEvent event = NotificationEvent.builder()
                .eventType("STOCK_ISSUE_PUSH_NOTIFICATION")
                .entityType("STOCK")
                .channel(NotificationChannel.PUSH)
                .build();

        when(stockAdapter.buildNotificationEvents(any(), eq("stock-create-topic")))
                .thenReturn(List.of(event));

        consumer.consumeNotificationEvent(record, "stock-create-topic");

        verify(stockAdapter).buildNotificationEvents(any(), eq("stock-create-topic"));
        verify(processor).processAndSendBatch(List.of(event));
    }

    @Test
    void consumeNotificationEvent_hfReferralSchema_routesToHfReferralAdapter() {
        String payload = "[{\"id\":\"ref-1\",\"clientReferenceId\":\"cref-1\",\"additionalFields\":{\"schema\":\"HFReferral\"}}]";

        ConsumerRecord<String, Object> record = new ConsumerRecord<>("hfreferral-create-topic", 0, 0, null, payload);

        when(hfReferralAdapter.buildNotificationEvents(any(), eq("hfreferral-create-topic")))
                .thenReturn(List.of());

        consumer.consumeNotificationEvent(record, "hfreferral-create-topic");

        verify(hfReferralAdapter).buildNotificationEvents(any(), eq("hfreferral-create-topic"));
    }

    @Test
    void consumeNotificationEvent_unknownSchema_skips() {
        String payload = "[{\"id\":\"unknown-1\",\"additionalFields\":{\"schema\":\"UnknownType\"}}]";

        ConsumerRecord<String, Object> record = new ConsumerRecord<>("some-topic", 0, 0, null, payload);

        consumer.consumeNotificationEvent(record, "some-topic");

        verify(stockAdapter, never()).buildNotificationEvents(any(), any());
        verify(hfReferralAdapter, never()).buildNotificationEvents(any(), any());
        verify(processor, never()).processAndSendBatch(any());
    }

    @Test
    void consumeNotificationEvent_emptyArray_doesNothing() {
        String payload = "[]";

        ConsumerRecord<String, Object> record = new ConsumerRecord<>("topic", 0, 0, null, payload);

        consumer.consumeNotificationEvent(record, "topic");

        verify(processor, never()).processAndSendBatch(any());
    }

    @Test
    void consumeNotificationEvent_invalidJson_doesNotThrow() {
        String payload = "not valid json";

        ConsumerRecord<String, Object> record = new ConsumerRecord<>("topic", 0, 0, null, payload);

        // Should not throw — error is logged
        consumer.consumeNotificationEvent(record, "topic");

        verify(processor, never()).processAndSendBatch(any());
    }

    @Test
    void consumeNotificationEvent_multipleRecords_processesEach() {
        String payload = "[" +
                "{\"id\":\"s1\",\"tenantId\":\"t1\",\"additionalFields\":{\"schema\":\"Stock\",\"fields\":[{\"key\":\"stockEntryType\",\"value\":\"ISSUED\"}]}}," +
                "{\"id\":\"s2\",\"tenantId\":\"t1\",\"additionalFields\":{\"schema\":\"Stock\",\"fields\":[{\"key\":\"stockEntryType\",\"value\":\"RECEIPT\"}]}}" +
                "]";

        ConsumerRecord<String, Object> record = new ConsumerRecord<>("stock-topic", 0, 0, null, payload);

        NotificationEvent event = NotificationEvent.builder()
                .eventType("TEST")
                .channel(NotificationChannel.PUSH)
                .build();

        when(stockAdapter.buildNotificationEvents(any(), any())).thenReturn(List.of(event));

        consumer.consumeNotificationEvent(record, "stock-topic");

        verify(processor, org.mockito.Mockito.times(2)).processAndSendBatch(any());
    }
}