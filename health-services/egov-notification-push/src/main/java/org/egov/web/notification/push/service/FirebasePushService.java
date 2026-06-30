package org.egov.web.notification.push.service;

import java.util.ArrayList;
import java.util.List;

import org.egov.web.notification.push.config.PushProperties;
import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.egov.web.notification.push.utils.ErrorConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;

import lombok.extern.slf4j.Slf4j;

@Service
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
@Slf4j
public class FirebasePushService implements PushNotificationService {

    @Autowired
    private FirebaseMessaging firebaseMessaging;

    @Autowired
    private PushProperties pushProperties;

    @Autowired
    private DeviceTokenService deviceTokenService;

    @Override
    public void sendPushNotification(PushNotificationRequest request) {
        List<String> tokens = request.getDeviceTokens();
        if (tokens == null || tokens.isEmpty()) {
            log.warn("No device tokens provided, skipping push notification");
            return;
        }

        Notification notification = Notification.builder()
                .setTitle(request.getTitle())
                .setBody(request.getBody())
                .build();

        if (tokens.size() == 1) {
            sendSingleMessage(notification, request, tokens.get(0));
        } else {
            sendMulticastMessage(notification, request, tokens);
        }
    }

    private void sendSingleMessage(Notification notification, PushNotificationRequest request, String token) {
        try {
            Message.Builder builder = Message.builder()
                    .setNotification(notification)
                    .setToken(token);

            if (request.getData() != null) {
                builder.putAllData(request.getData());
            }

            String response = firebaseMessaging.send(builder.build());
            log.info("Successfully sent single push notification: {}", response);
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send push notification to token {}: {}", token, e.getMessage());
            handleMessagingError(e, token, request.getTenantId());
        }
    }

    private void sendMulticastMessage(Notification notification, PushNotificationRequest request, List<String> tokens) {
        int chunkSize = Math.min(pushProperties.getFcmSendChunkSize(), 500);

        List<List<String>> chunks = new ArrayList<>();
        List<ApiFuture<BatchResponse>> futures = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i += chunkSize) {
            List<String> chunk = tokens.subList(i, Math.min(i + chunkSize, tokens.size()));

            MulticastMessage.Builder builder = MulticastMessage.builder()
                    .setNotification(notification)
                    .addAllTokens(chunk);

            if (request.getData() != null) {
                builder.putAllData(request.getData());
            }

            chunks.add(chunk);
            futures.add(firebaseMessaging.sendEachForMulticastAsync(builder.build()));
        }

        try {
            List<BatchResponse> responses = ApiFutures.allAsList(futures).get();

            int success = 0;
            int failure = 0;
            for (int i = 0; i < responses.size(); i++) {
                BatchResponse response = responses.get(i);
                success += response.getSuccessCount();
                failure += response.getFailureCount();
                if (response.getFailureCount() > 0) {
                    handleBatchFailures(response.getResponses(), chunks.get(i), request.getTenantId());
                }
            }
            log.info("Multicast batch sent: {} success, {} failure", success, failure);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while sending multicast push notification: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to send multicast push notification: {}", e.getMessage());
        }
    }

    private void handleBatchFailures(List<SendResponse> responses, List<String> tokens, String tenantId) {
        List<String> unregisteredTokens = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            if (!responses.get(i).isSuccessful()) {
                FirebaseMessagingException ex = responses.get(i).getException();
                if (ex != null && ex.getMessagingErrorCode() != null
                        && ErrorConstants.FCM_ERROR_UNREGISTERED.equals(ex.getMessagingErrorCode().name())) {
                    unregisteredTokens.add(tokens.get(i));
                }
                log.warn("Failed to send to token {}: {}", tokens.get(i),
                        ex != null ? ex.getMessage() : "unknown error");
            }
        }
        if (!unregisteredTokens.isEmpty()) {
            log.info("Cleaning up {} unregistered token(s)", unregisteredTokens.size());
            deviceTokenService.deleteStaleTokens(unregisteredTokens, tenantId);
        }
    }

    private void handleMessagingError(FirebaseMessagingException e, String token, String tenantId) {
        if (e.getMessagingErrorCode() != null
                && ErrorConstants.FCM_ERROR_UNREGISTERED.equals(e.getMessagingErrorCode().name())) {
            log.info("Cleaning up unregistered token: {}", token);
            deviceTokenService.deleteStaleTokens(List.of(token), tenantId);
        }
    }

}
