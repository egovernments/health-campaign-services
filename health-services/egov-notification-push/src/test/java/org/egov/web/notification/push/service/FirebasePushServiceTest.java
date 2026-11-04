package org.egov.web.notification.push.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.egov.web.notification.push.config.FcmClientRegistry;
import org.egov.web.notification.push.config.FcmClientRegistry.FcmClient;
import org.egov.web.notification.push.config.FcmRateLimiter;
import org.egov.web.notification.push.config.PushProperties;
import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;

@ExtendWith(MockitoExtension.class)
class FirebasePushServiceTest {

    @InjectMocks
    private FirebasePushService service;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private FcmClientRegistry fcmClientRegistry;

    @Mock
    private PushProperties pushProperties;

    @Mock
    private DeviceTokenService deviceTokenService;

    @Mock
    private PushRetryService pushRetryService;

    @Mock
    private FcmRateLimiter rateLimiter;

    @Mock
    private PushMetrics metrics;

    @BeforeEach
    void setUp() {
        lenient().when(fcmClientRegistry.forTenant(any()))
                .thenReturn(Optional.of(new FcmClient("test-project", firebaseMessaging)));
        lenient().when(pushProperties.getFcmSendChunkSize()).thenReturn(500);
        lenient().when(pushProperties.getSendDeadlineMs()).thenReturn(2000L);
        lenient().when(rateLimiter.tryAcquire(any(), anyInt(), anyLong())).thenReturn(true);
    }

    private static PushNotificationRequest request(String... tokens) {
        return PushNotificationRequest.builder().title("T").body("B").tenantId("ng")
                .deviceTokens(new ArrayList<>(List.of(tokens))).build();
    }

    private static SendResponse ok() {
        SendResponse r = mock(SendResponse.class);
        when(r.isSuccessful()).thenReturn(true);
        return r;
    }

    private static SendResponse fail(MessagingErrorCode code) {
        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        lenient().when(ex.getMessagingErrorCode()).thenReturn(code);
        SendResponse r = mock(SendResponse.class);
        when(r.isSuccessful()).thenReturn(false);
        when(r.getException()).thenReturn(ex);
        return r;
    }

    private void respond(SendResponse... responses) {
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getResponses()).thenReturn(List.of(responses));
        when(firebaseMessaging.sendEachForMulticastAsync(any(MulticastMessage.class)))
                .thenReturn(ApiFutures.immediateFuture(batch));
    }

    @Test
    void noTokens_skips() {
        assertEquals(SendResult.EMPTY, service.sendPushNotification(request()));
        verify(firebaseMessaging, never()).sendEachForMulticastAsync(any());
    }

    @Test
    void noFcmProjectForTenant_countsAsPermanentAndDoesNotSend() {
        when(fcmClientRegistry.forTenant("ng")).thenReturn(Optional.empty());

        assertEquals(new SendResult(2, 0, 2, 0), service.sendPushNotification(request("a", "b")));
        verify(firebaseMessaging, never()).sendEachForMulticastAsync(any());
    }

    @Test
    void allSuccessful_noRetry() {
        respond(ok(), ok());

        assertEquals(new SendResult(2, 2, 0, 0), service.sendPushNotification(request("a", "b")));
        verify(pushRetryService, never()).schedule(any(), any(), anyInt(), any(), anyLong());
    }

    @Test
    void temporaryFailures_retryOnlyFailedTokens() {
        respond(ok(), fail(MessagingErrorCode.INTERNAL), fail(MessagingErrorCode.QUOTA_EXCEEDED));

        SendResult result = service.sendPushNotification(request("ok", "internal", "quota"), 1);

        assertEquals(new SendResult(3, 1, 0, 2), result);
        verify(pushRetryService).schedule(any(), eq(List.of("internal", "quota")), eq(1), eq("QUOTA_EXCEEDED"), eq(0L));
    }

    @Test
    void unregistered_deletesTokenWithoutRetry() {
        respond(fail(MessagingErrorCode.UNREGISTERED), ok());

        assertEquals(new SendResult(2, 1, 1, 0), service.sendPushNotification(request("dead", "ok")));
        verify(deviceTokenService).deleteStaleTokens(List.of("dead"), "ng");
        verify(pushRetryService, never()).schedule(any(), any(), anyInt(), any(), anyLong());
    }

    @Test
    void senderIdMismatch_isPermanentAndKeepsToken() {
        respond(fail(MessagingErrorCode.SENDER_ID_MISMATCH));

        assertEquals(new SendResult(1, 0, 1, 0), service.sendPushNotification(request("other")));
        verify(deviceTokenService, never()).deleteStaleTokens(any(), any());
        verify(pushRetryService, never()).schedule(any(), any(), anyInt(), any(), anyLong());
    }

    @Test
    void wholeChunkFailure_retriesAllTokensInChunk() {
        when(firebaseMessaging.sendEachForMulticastAsync(any(MulticastMessage.class)))
                .thenReturn(ApiFutures.immediateFailedFuture(new RuntimeException("auth failed")));

        assertEquals(new SendResult(2, 0, 0, 2), service.sendPushNotification(request("a", "b")));
        verify(pushRetryService).schedule(any(), eq(List.of("a", "b")), eq(0), eq("RuntimeException"), eq(0L));
    }

    @Test
    void deadlineExceeded_retriesPendingChunk() {
        when(pushProperties.getSendDeadlineMs()).thenReturn(50L);
        when(firebaseMessaging.sendEachForMulticastAsync(any(MulticastMessage.class)))
                .thenReturn(SettableApiFuture.create());

        assertEquals(new SendResult(1, 0, 0, 1), service.sendPushNotification(request("slow")));
        verify(pushRetryService).schedule(any(), eq(List.of("slow")), eq(0), eq(FcmErrorClassifier.TIMEOUT), eq(0L));
    }

    @Test
    void chunksAreSentSeparately() {
        when(pushProperties.getFcmSendChunkSize()).thenReturn(2);
        List<SendResponse> twoOk = List.of(ok(), ok());
        List<SendResponse> oneOk = List.of(ok());
        BatchResponse two = mock(BatchResponse.class);
        when(two.getResponses()).thenReturn(twoOk);
        BatchResponse one = mock(BatchResponse.class);
        when(one.getResponses()).thenReturn(oneOk);
        when(firebaseMessaging.sendEachForMulticastAsync(any(MulticastMessage.class)))
                .thenReturn(ApiFutures.immediateFuture(two), ApiFutures.immediateFuture(two), ApiFutures.immediateFuture(one));

        assertEquals(new SendResult(5, 5, 0, 0), service.sendPushNotification(request("1", "2", "3", "4", "5")));
        verify(firebaseMessaging, times(3)).sendEachForMulticastAsync(any(MulticastMessage.class));
    }

    @Test
    void missingEventId_isGeneratedPerSend() {
        respond(ok());
        PushNotificationRequest first = request("a");
        PushNotificationRequest second = request("a");
        service.sendPushNotification(first);
        service.sendPushNotification(second);

        assertNotNull(first.getEventId());
        assertNotEquals(first.getEventId(), second.getEventId());
        verify(deviceTokenService, never()).deleteStaleTokens(any(), anyString());
    }

    @Test
    void expiredRetry_isDroppedWithoutSending() {
        PushNotificationRequest expired = request("a", "b");
        expired.setEventId("e1");
        expired.setExpiresAt(System.currentTimeMillis() - 1);

        assertEquals(new SendResult(2, 0, 2, 0), service.sendPushNotification(expired, 2));
        verify(firebaseMessaging, never()).sendEachForMulticastAsync(any());
    }

    @Test
    void firstSend_setsExpiryFromTypeTtl() {
        respond(ok());
        when(pushProperties.ttlSeconds("STOCK_ISSUE")).thenReturn(3600L);
        PushNotificationRequest req = request("a");
        req.setType("STOCK_ISSUE");
        long before = System.currentTimeMillis();

        service.sendPushNotification(req);

        assertTrue(req.getExpiresAt() >= before + 3_600_000 && req.getExpiresAt() <= before + 3_605_000);
    }

    @Test
    void collapseId_isShortenedForApnsLimit() {
        assertEquals("e1", FirebasePushService.collapseId("e1"));
        assertEquals(36, FirebasePushService.collapseId("x".repeat(100)).length());
    }

    @Test
    void rateLimitedChunk_isQueuedForRetryWithoutSending() {
        when(rateLimiter.tryAcquire(eq("test-project"), eq(2), anyLong())).thenReturn(false);

        assertEquals(new SendResult(2, 0, 0, 2), service.sendPushNotification(request("a", "b")));
        verify(firebaseMessaging, never()).sendEachForMulticastAsync(any());
        verify(pushRetryService).schedule(any(), eq(List.of("a", "b")), eq(0), eq("RATE_LIMITED"), eq(0L));
        verify(metrics).failed("ng", "test-project", "temporary", "RATE_LIMITED", 2);
    }

    @Test
    void quotaExceeded_backsOffProject() {
        respond(fail(MessagingErrorCode.QUOTA_EXCEEDED));

        service.sendPushNotification(request("q"));

        verify(rateLimiter).backOff("test-project", FirebasePushService.DEFAULT_QUOTA_BACKOFF_MS);
    }

    @Test
    void outcomes_areRecordedAsMetrics() {
        respond(ok(), fail(MessagingErrorCode.UNREGISTERED), fail(MessagingErrorCode.INTERNAL));
        PushNotificationRequest req = request("ok", "dead", "flaky");
        req.setType("STOCK_ISSUE");

        service.sendPushNotification(req);

        verify(metrics).sent("ng", "test-project", "STOCK_ISSUE", 1);
        verify(metrics).failed("ng", "test-project", "permanent", "UNREGISTERED", 1);
        verify(metrics).failed("ng", "test-project", "temporary", "INTERNAL", 1);
        verify(metrics).sendLatency(eq("test-project"), any());
    }

    @Test
    void expiryBeyondFcmLimit_isClampedTo28Days() {
        respond(ok());
        when(pushProperties.ttlSeconds(any())).thenReturn(10L * 365 * 24 * 3600);
        PushNotificationRequest req = request("a");
        req.setExpiresAt(Long.MAX_VALUE);
        long before = System.currentTimeMillis();

        service.sendPushNotification(req);

        assertTrue(req.getExpiresAt() <= before + FirebasePushService.FCM_MAX_TTL_MS + 5000);
    }
}
