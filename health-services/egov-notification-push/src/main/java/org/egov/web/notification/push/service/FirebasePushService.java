package org.egov.web.notification.push.service;

import java.util.ArrayList;
import java.util.List;

import org.egov.web.notification.push.config.FcmClientRegistry;
import org.egov.web.notification.push.config.FcmClientRegistry.FcmClient;
import org.egov.web.notification.push.config.PushProperties;
import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.egov.web.notification.push.utils.ErrorConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.firebase.messaging.BatchResponse;
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
    private FcmClientRegistry fcmClientRegistry;

    @Autowired
    private PushProperties pushProperties;

    @Autowired
    private DeviceTokenService deviceTokenService;

    @Override
    public void sendPushNotification(PushNotificationRequest request) {
        List<String> tokens = request.getDeviceTokens();
        if (tokens == null || tokens.isEmpty()) {
            log.warn("PUSH_SKIP reason=NO_TOKENS_IN_REQUEST title={} facilityId={} data={}",
                    request.getTitle(), request.getFacilityId(), request.getData());
            return;
        }

        FcmClient client = fcmClientRegistry.forTenant(request.getTenantId()).orElse(null);
        if (client == null) {
            log.warn("PUSH_SKIP reason=NO_FCM_PROJECT tenantId={} title={} facilityId={} data={}",
                    request.getTenantId(), request.getTitle(), request.getFacilityId(), request.getData());
            return;
        }

        Notification notification = Notification.builder()
                .setTitle(request.getTitle())
                .setBody(request.getBody())
                .build();

        if (tokens.size() == 1) {
            sendSingleMessage(client, notification, request, tokens.get(0));
        } else {
            sendMulticastMessage(client, notification, request, tokens);
        }
    }

    private void sendSingleMessage(FcmClient client, Notification notification, PushNotificationRequest request, String token) {
        try {
            Message.Builder builder = Message.builder()
                    .setNotification(notification)
                    .setToken(token);

            if (request.getData() != null) {
                builder.putAllData(request.getData());
            }

            String response = client.messaging().send(builder.build());
            log.info("PUSH_FCM_OK single projectId={} messageId={} tokenSuffix={} title={} data={}",
                    client.projectId(), response, DeviceTokenService.tokenSuffix(token), request.getTitle(), request.getData());
        } catch (FirebaseMessagingException e) {
            log.error("PUSH_FCM_FAIL single projectId={} tokenSuffix={} errorCode={} title={} data={}: {}",
                    client.projectId(), DeviceTokenService.tokenSuffix(token),
                    e.getMessagingErrorCode() != null ? e.getMessagingErrorCode().name() : "UNKNOWN",
                    request.getTitle(), request.getData(), e.getMessage());
            handleMessagingError(client, e, token, request.getTenantId());
        }
    }

    private void sendMulticastMessage(FcmClient client, Notification notification, PushNotificationRequest request, List<String> tokens) {
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
            futures.add(client.messaging().sendEachForMulticastAsync(builder.build()));
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
                    handleBatchFailures(client, response.getResponses(), chunks.get(i), request.getTenantId());
                }
            }
            if (failure > 0) {
                log.warn("PUSH_FCM_PARTIAL multicast projectId={} success={} failure={} title={} facilityId={} data={}",
                        client.projectId(), success, failure, request.getTitle(), request.getFacilityId(), request.getData());
            } else {
                log.info("PUSH_FCM_OK multicast projectId={} success={} title={} facilityId={} data={}",
                        client.projectId(), success, request.getTitle(), request.getFacilityId(), request.getData());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("PUSH_FCM_FAIL multicast interrupted projectId={} title={} data={}: {}",
                    client.projectId(), request.getTitle(), request.getData(), e.getMessage());
        } catch (Exception e) {
            log.error("PUSH_FCM_FAIL multicast projectId={} title={} facilityId={} data={}: {}",
                    client.projectId(), request.getTitle(), request.getFacilityId(), request.getData(), e.getMessage(), e);
        }
    }

    private void handleBatchFailures(FcmClient client, List<SendResponse> responses, List<String> tokens, String tenantId) {
        List<String> unregisteredTokens = new ArrayList<>();
        int senderMismatch = 0;
        for (int i = 0; i < responses.size(); i++) {
            if (!responses.get(i).isSuccessful()) {
                FirebaseMessagingException ex = responses.get(i).getException();
                if (ex != null && ex.getMessagingErrorCode() != null
                        && ErrorConstants.FCM_ERROR_UNREGISTERED.equals(ex.getMessagingErrorCode().name())) {
                    unregisteredTokens.add(tokens.get(i));
                } else if (isSenderIdMismatch(ex)) {
                    senderMismatch++;
                }
                log.warn("PUSH_FCM_TOKEN_FAIL tokenSuffix={} errorCode={}: {}",
                        DeviceTokenService.tokenSuffix(tokens.get(i)),
                        ex != null && ex.getMessagingErrorCode() != null ? ex.getMessagingErrorCode().name() : "UNKNOWN",
                        ex != null ? ex.getMessage() : "unknown error");
            }
        }
        if (senderMismatch > 0) {
            logSenderIdMismatch(client, tenantId, senderMismatch);
        }
        if (!unregisteredTokens.isEmpty()) {
            log.info("Cleaning up {} unregistered token(s)", unregisteredTokens.size());
            deviceTokenService.deleteStaleTokens(unregisteredTokens, tenantId);
        }
    }

    private void handleMessagingError(FcmClient client, FirebaseMessagingException e, String token, String tenantId) {
        if (e.getMessagingErrorCode() != null
                && ErrorConstants.FCM_ERROR_UNREGISTERED.equals(e.getMessagingErrorCode().name())) {
            log.info("Cleaning up unregistered token: {}", token);
            deviceTokenService.deleteStaleTokens(List.of(token), tenantId);
        } else if (isSenderIdMismatch(e)) {
            logSenderIdMismatch(client, tenantId, 1);
        }
    }

    private boolean isSenderIdMismatch(FirebaseMessagingException e) {
        return e != null && e.getMessagingErrorCode() != null
                && ErrorConstants.FCM_ERROR_SENDER_ID_MISMATCH.equals(e.getMessagingErrorCode().name());
    }

    private void logSenderIdMismatch(FcmClient client, String tenantId, int count) {
        log.error("PUSH_FCM_SENDER_ID_MISMATCH projectId={} tenantId={} count={}: tokens belong to a different FCM project, check FCM_TENANT_PROJECT_MAP_JSON",
                client.projectId(), tenantId, count);
    }

}
