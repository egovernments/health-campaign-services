package org.egov.web.notification.push.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.egov.web.notification.push.config.PushProperties;
import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FirebasePushServiceTest {

    @InjectMocks
    private FirebasePushService firebasePushService;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private PushProperties pushProperties;

    @Test
    void sendPushNotification_nullTokens_skips() {
        PushNotificationRequest request = PushNotificationRequest.builder()
                .title("Title")
                .body("Body")
                .build();

        firebasePushService.sendPushNotification(request);

        verify(firebaseMessaging, never()).sendAsync(any());
    }

    @Test
    void sendPushNotification_emptyTokens_skips() {
        PushNotificationRequest request = PushNotificationRequest.builder()
                .title("Title")
                .body("Body")
                .deviceTokens(Collections.emptyList())
                .build();

        firebasePushService.sendPushNotification(request);

        verify(firebaseMessaging, never()).sendAsync(any());
    }

    @Test
    void sendPushNotification_singleToken_sendsSingleMessage() throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("notificationType", "STOCK");
        data.put("eventType", "STOCK_ISSUE_PUSH_NOTIFICATION");

        PushNotificationRequest request = PushNotificationRequest.builder()
                .title("Stock Issue")
                .body("50 ITN Nets issued")
                .deviceTokens(List.of("single-token"))
                .data(data)
                .build();

        when(firebaseMessaging.send(any(Message.class))).thenReturn("projects/test/messages/123");

        firebasePushService.sendPushNotification(request);

        verify(firebaseMessaging).send(any(Message.class));
    }

    @Test
    void sendPushNotification_singleToken_noData_sendsWithoutData() throws Exception {
        PushNotificationRequest request = PushNotificationRequest.builder()
                .title("Title")
                .body("Body")
                .deviceTokens(List.of("token1"))
                .build();

        when(firebaseMessaging.send(any(Message.class))).thenReturn("msg-id");

        firebasePushService.sendPushNotification(request);

        verify(firebaseMessaging).send(any(Message.class));
    }

    @Test
    void sendPushNotification_singleToken_firebaseException_doesNotThrow() throws Exception {
        PushNotificationRequest request = PushNotificationRequest.builder()
                .title("Title")
                .body("Body")
                .deviceTokens(List.of("bad-token"))
                .build();

        when(firebaseMessaging.send(any(Message.class)))
                .thenThrow(FirebaseMessagingException.class);

        assertDoesNotThrow(() -> firebasePushService.sendPushNotification(request));
    }

    @Test
    void sendPushNotification_multipleTokens_sendsMulticast() throws Exception {
        PushNotificationRequest request = PushNotificationRequest.builder()
                .title("Title")
                .body("Body")
                .deviceTokens(Arrays.asList("token1", "token2", "token3"))
                .data(Map.of("notificationType", "STOCK"))
                .build();

        when(pushProperties.getFcmBatchSize()).thenReturn(500);

        BatchResponse batchResponse = createBatchResponse(3, 0);
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenReturn(batchResponse);

        firebasePushService.sendPushNotification(request);

        verify(firebaseMessaging).sendEachForMulticast(any(MulticastMessage.class));
    }

    @Test
    void sendPushNotification_multipleTokens_batching() throws Exception {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tokens.add("token-" + i);
        }

        PushNotificationRequest request = PushNotificationRequest.builder()
                .title("Title")
                .body("Body")
                .deviceTokens(tokens)
                .build();

        when(pushProperties.getFcmBatchSize()).thenReturn(2);

        BatchResponse batchResponse = createBatchResponse(2, 0);
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenReturn(batchResponse);

        firebasePushService.sendPushNotification(request);

        // 5 tokens with batch size 2 = 3 batches (2, 2, 1)
        verify(firebaseMessaging, times(3)).sendEachForMulticast(any(MulticastMessage.class));
    }

    @Test
    void sendPushNotification_multicast_firebaseException_doesNotThrow() throws Exception {
        PushNotificationRequest request = PushNotificationRequest.builder()
                .title("Title")
                .body("Body")
                .deviceTokens(Arrays.asList("tok1", "tok2"))
                .build();

        when(pushProperties.getFcmBatchSize()).thenReturn(500);
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenThrow(FirebaseMessagingException.class);

        assertDoesNotThrow(() -> firebasePushService.sendPushNotification(request));
    }

    private BatchResponse createBatchResponse(int successCount, int failureCount) {
        BatchResponse response = org.mockito.Mockito.mock(BatchResponse.class);
        when(response.getSuccessCount()).thenReturn(successCount);
        when(response.getFailureCount()).thenReturn(failureCount);
        if (failureCount > 0) {
            List<SendResponse> responses = new ArrayList<>();
            for (int i = 0; i < successCount; i++) {
                SendResponse sr = org.mockito.Mockito.mock(SendResponse.class);
                when(sr.isSuccessful()).thenReturn(true);
                responses.add(sr);
            }
            for (int i = 0; i < failureCount; i++) {
                SendResponse sr = org.mockito.Mockito.mock(SendResponse.class);
                when(sr.isSuccessful()).thenReturn(false);
                responses.add(sr);
            }
            when(response.getResponses()).thenReturn(responses);
        }
        return response;
    }
}
