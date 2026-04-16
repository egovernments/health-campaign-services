package org.egov.web.notification.push.service;

import java.util.ArrayList;
import java.util.List;

import org.egov.web.notification.push.config.PushProperties;
import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.egov.web.notification.push.utils.ErrorConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

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

    private static final int DEFAULT_FCM_BATCH_SIZE = 500;

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
            log.error("Failed to send single push notification: {}", e.getMessage());
            handleMessagingError(e, token, request.getTenantId());
        }
    }

    private void sendMulticastMessage(Notification notification, PushNotificationRequest request, List<String> tokens) {
        Integer configuredBatchSize = pushProperties.getFcmBatchSize();
        int batchSize = configuredBatchSize == null ? DEFAULT_FCM_BATCH_SIZE : configuredBatchSize;
        if (batchSize <= 0) {
            log.error("Invalid fcm.batch.size configuration: {}. Using default of {}.", batchSize, DEFAULT_FCM_BATCH_SIZE);
            batchSize = DEFAULT_FCM_BATCH_SIZE;
        }

        for (int i = 0; i < tokens.size(); i += batchSize) {
            List<String> batch = tokens.subList(i, Math.min(i + batchSize, tokens.size()));

            MulticastMessage.Builder builder = MulticastMessage.builder()
                    .setNotification(notification)
                    .addAllTokens(batch);

            if (request.getData() != null) {
                builder.putAllData(request.getData());
            }

            try {
                BatchResponse response = firebaseMessaging.sendEachForMulticast(builder.build());
                log.info("Multicast batch sent: {} success, {} failure",
                        response.getSuccessCount(), response.getFailureCount());

                if (response.getFailureCount() > 0) {
                    handleBatchFailures(response.getResponses(), batch, request.getTenantId());
                }
            } catch (FirebaseMessagingException e) {
                log.error("Failed to send multicast push notification batch: {}", e.getMessage());
            }
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
                log.warn("Failed to send multicast response at batch index {}: {}", i,
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
            log.info("Cleaning up 1 unregistered token");
            deviceTokenService.deleteStaleTokens(List.of(token), tenantId);
        }
    }

}
