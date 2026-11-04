package org.egov.web.notification.push.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.egov.web.notification.push.config.FcmClientRegistry;
import org.egov.web.notification.push.config.FcmClientRegistry.FcmClient;
import org.egov.web.notification.push.config.FcmRateLimiter;
import org.egov.web.notification.push.config.PushProperties;
import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.google.api.core.ApiFuture;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;

import lombok.extern.slf4j.Slf4j;

@Service
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
@Slf4j
public class FirebasePushService implements PushNotificationService {

    static final String PERMANENT = "permanent";
    static final String TEMPORARY = "temporary";
    static final String RATE_LIMITED = "RATE_LIMITED";
    static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    static final long DEFAULT_QUOTA_BACKOFF_MS = 60_000;
    static final long FCM_MAX_TTL_MS = 28L * 24 * 3600 * 1000;

    @Autowired
    private FcmClientRegistry fcmClientRegistry;

    @Autowired
    private PushProperties pushProperties;

    @Autowired
    private DeviceTokenService deviceTokenService;

    @Autowired
    private PushRetryService pushRetryService;

    @Autowired
    private FcmRateLimiter rateLimiter;

    @Autowired
    private PushMetrics metrics;

    @Override
    public SendResult sendPushNotification(PushNotificationRequest request, int attempt) {
        List<String> tokens = request.getDeviceTokens();
        if (tokens == null || tokens.isEmpty()) {
            log.warn("PUSH_SKIP reason=NO_TOKENS_IN_REQUEST eventId={} title={} facilityId={} data={}",
                    request.getEventId(), request.getTitle(), request.getFacilityId(), request.getData());
            return SendResult.EMPTY;
        }
        if (request.getEventId() == null || request.getEventId().isBlank()) {
            request.setEventId(UUID.randomUUID().toString());
        }
        long now = System.currentTimeMillis();
        if (request.getExpiresAt() == null || request.getExpiresAt() > now + FCM_MAX_TTL_MS) {
            request.setExpiresAt(now + Math.min(pushProperties.ttlSeconds(request.getType()) * 1000, FCM_MAX_TTL_MS));
        } else if (request.getExpiresAt() <= now) {
            log.warn("PUSH_SKIP reason=EXPIRED eventId={} tenantId={} attempt={} tokens={}",
                    request.getEventId(), request.getTenantId(), attempt, tokens.size());
            metrics.failed(request.getTenantId(), null, PERMANENT, "EXPIRED", tokens.size());
            return new SendResult(tokens.size(), 0, tokens.size(), 0);
        }

        FcmClient client = fcmClientRegistry.forTenant(request.getTenantId()).orElse(null);
        if (client == null) {
            log.warn("PUSH_SKIP reason=NO_FCM_PROJECT eventId={} tenantId={} title={} facilityId={} data={}",
                    request.getEventId(), request.getTenantId(), request.getTitle(), request.getFacilityId(), request.getData());
            metrics.failed(request.getTenantId(), null, PERMANENT, "NO_FCM_PROJECT", tokens.size());
            return new SendResult(tokens.size(), 0, tokens.size(), 0);
        }

        long started = System.nanoTime();
        Outcome outcome = send(client, request, tokens);
        metrics.sendLatency(client.projectId(), Duration.ofNanos(System.nanoTime() - started));
        metrics.sent(request.getTenantId(), client.projectId(), request.getType(), outcome.sent);
        outcome.permanentCodes.forEach((code, n) -> metrics.failed(request.getTenantId(), client.projectId(), PERMANENT, code, n));
        outcome.temporaryCodes.forEach((code, n) -> metrics.failed(request.getTenantId(), client.projectId(), TEMPORARY, code, n));
        if (outcome.temporaryCodes.containsKey(QUOTA_EXCEEDED)) {
            rateLimiter.backOff(client.projectId(), outcome.retryAfterMs > 0 ? outcome.retryAfterMs : DEFAULT_QUOTA_BACKOFF_MS);
        }

        if (!outcome.unregistered.isEmpty()) {
            deviceTokenService.deleteStaleTokens(outcome.unregistered, request.getTenantId());
        }
        if (outcome.senderMismatch > 0) {
            log.error("PUSH_FCM_SENDER_ID_MISMATCH eventId={} projectId={} tenantId={} count={}: tokens belong to a different FCM project, check FCM_TENANT_PROJECT_MAP_JSON",
                    request.getEventId(), client.projectId(), request.getTenantId(), outcome.senderMismatch);
        }
        if (!outcome.temporary.isEmpty()) {
            pushRetryService.schedule(request, outcome.temporary, attempt, outcome.lastTemporaryError, outcome.retryAfterMs);
        }

        SendResult result = new SendResult(tokens.size(), outcome.sent, outcome.permanent, outcome.temporary.size());
        if (result.sent() == result.attempted()) {
            log.info("PUSH_FCM_OK eventId={} projectId={} attempt={} sent={} title={} facilityId={} data={}",
                    request.getEventId(), client.projectId(), attempt, result.sent(), request.getTitle(), request.getFacilityId(), request.getData());
        } else {
            log.warn("PUSH_FCM_PARTIAL eventId={} projectId={} attempt={} sent={} failedPermanent={} queuedForRetry={} title={} facilityId={} data={}",
                    request.getEventId(), client.projectId(), attempt, result.sent(), result.failedPermanent(), result.queuedForRetry(),
                    request.getTitle(), request.getFacilityId(), request.getData());
        }
        return result;
    }

    private Outcome send(FcmClient client, PushNotificationRequest request, List<String> tokens) {
        int chunkSize = Math.min(pushProperties.getFcmSendChunkSize(), 500);
        long deadline = System.currentTimeMillis() + pushProperties.getSendDeadlineMs();
        Notification notification = Notification.builder()
                .setTitle(request.getTitle())
                .setBody(request.getBody())
                .build();

        String collapseId = collapseId(request.getEventId());
        long ttlMs = Math.max(0, request.getExpiresAt() - System.currentTimeMillis());
        AndroidConfig android = AndroidConfig.builder()
                .setTtl(ttlMs)
                .setNotification(AndroidNotification.builder().setTag(collapseId).build())
                .build();
        ApnsConfig apns = ApnsConfig.builder()
                .putHeader("apns-collapse-id", collapseId)
                .putHeader("apns-expiration", String.valueOf(request.getExpiresAt() / 1000))
                .setAps(Aps.builder().build())
                .build();

        Outcome outcome = new Outcome();
        List<List<String>> chunks = new ArrayList<>();
        List<ApiFuture<BatchResponse>> futures = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i += chunkSize) {
            List<String> chunk = tokens.subList(i, Math.min(i + chunkSize, tokens.size()));
            if (!rateLimiter.tryAcquire(client.projectId(), chunk.size(), deadline - System.currentTimeMillis())) {
                outcome.temporary(chunk, RATE_LIMITED, 0);
                continue;
            }
            MulticastMessage.Builder builder = MulticastMessage.builder()
                    .setNotification(notification)
                    .setAndroidConfig(android)
                    .setApnsConfig(apns)
                    .addAllTokens(chunk);
            if (request.getData() != null) {
                builder.putAllData(request.getData());
            }
            chunks.add(chunk);
            futures.add(client.messaging().sendEachForMulticastAsync(builder.build()));
        }

        for (int i = 0; i < futures.size(); i++) {
            List<String> chunk = chunks.get(i);
            try {
                long wait = Math.max(0, deadline - System.currentTimeMillis());
                collect(outcome, futures.get(i).get(wait, TimeUnit.MILLISECONDS).getResponses(), chunk, request.getEventId());
            } catch (TimeoutException e) {
                futures.get(i).cancel(false);
                outcome.temporary(chunk, FcmErrorClassifier.TIMEOUT, 0);
                log.error("PUSH_FCM_FAIL eventId={} projectId={} tokens={} reason=DEADLINE_EXCEEDED",
                        request.getEventId(), client.projectId(), chunk.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                outcome.temporary(chunk, "INTERRUPTED", 0);
            } catch (ExecutionException e) {
                String code = FcmErrorClassifier.code(e.getCause());
                outcome.temporary(chunk, code, FcmErrorClassifier.retryAfterMs(e.getCause()));
                log.error("PUSH_FCM_FAIL eventId={} projectId={} tokens={} errorCode={}: {}",
                        request.getEventId(), client.projectId(), chunk.size(), code, e.getCause() == null ? null : e.getCause().getMessage());
            }
        }
        return outcome;
    }

    private void collect(Outcome outcome, List<SendResponse> responses, List<String> tokens, String eventId) {
        for (int i = 0; i < responses.size(); i++) {
            SendResponse response = responses.get(i);
            if (response.isSuccessful()) {
                outcome.sent++;
                continue;
            }
            String token = tokens.get(i);
            String code = FcmErrorClassifier.code(response.getException());
            log.warn("PUSH_FCM_TOKEN_FAIL eventId={} tokenSuffix={} errorCode={}: {}", eventId, DeviceTokenService.tokenSuffix(token), code,
                    response.getException() != null ? response.getException().getMessage() : "unknown error");
            if (FcmErrorClassifier.isPermanent(code)) {
                outcome.permanent++;
                outcome.permanentCodes.merge(code, 1, Integer::sum);
                if (FcmErrorClassifier.UNREGISTERED.equals(code)) {
                    outcome.unregistered.add(token);
                } else if (FcmErrorClassifier.SENDER_ID_MISMATCH.equals(code)) {
                    outcome.senderMismatch++;
                }
            } else {
                outcome.temporary(List.of(token), code, FcmErrorClassifier.retryAfterMs(response.getException()));
            }
        }
    }

    static String collapseId(String eventId) {
        return eventId.length() <= 64 ? eventId
                : UUID.nameUUIDFromBytes(eventId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static final class Outcome {
        private int sent;
        private int permanent;
        private int senderMismatch;
        private final List<String> unregistered = new ArrayList<>();
        private final List<String> temporary = new ArrayList<>();
        private final Map<String, Integer> permanentCodes = new HashMap<>();
        private final Map<String, Integer> temporaryCodes = new HashMap<>();
        private String lastTemporaryError;
        private long retryAfterMs;

        private void temporary(List<String> tokens, String code, long retryAfter) {
            temporary.addAll(tokens);
            temporaryCodes.merge(code, tokens.size(), Integer::sum);
            lastTemporaryError = code;
            retryAfterMs = Math.max(retryAfterMs, retryAfter);
        }
    }

}
