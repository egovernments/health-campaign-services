package org.egov.web.notification.push.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.egov.web.notification.push.config.PushProperties;
import org.egov.web.notification.push.producer.DeviceTokenProducer;
import org.egov.web.notification.push.repository.DeviceTokenRepository;
import org.egov.web.notification.push.web.contract.DeviceToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @InjectMocks
    private DeviceTokenService deviceTokenService;

    @Mock
    private DeviceTokenRepository repository;

    @Mock
    private DeviceTokenProducer producer;

    @Mock
    private PushProperties properties;

    private RequestInfo createRequestInfo(String uuid) {
        User user = User.builder().uuid(uuid).build();
        return RequestInfo.builder().userInfo(user).build();
    }

    @Test
    void registerDeviceTokens_validTokens_setsIdAndAuditAndPublishes() {
        when(properties.getSaveDeviceTokenTopic()).thenReturn("save-topic");

        DeviceToken token = DeviceToken.builder()
                .deviceToken("fcm-token-123")
                .deviceType("ANDROID")
                .tenantId("tenant1")
                .build();

        RequestInfo requestInfo = createRequestInfo("user-uuid-1");
        List<DeviceToken> result = deviceTokenService.registerDeviceTokens(requestInfo, List.of(token));

        assertEquals(1, result.size());
        assertNotNull(result.get(0).getId());
        assertEquals("user-uuid-1", result.get(0).getUserId());
        assertNotNull(result.get(0).getAuditDetails());
        assertEquals("user-uuid-1", result.get(0).getAuditDetails().getCreatedBy());
        verify(producer).push(eq("tenant1"), eq("save-topic"), any());
    }

    @Test
    void registerDeviceTokens_emptyDeviceToken_throwsCustomException() {
        DeviceToken token = DeviceToken.builder()
                .deviceToken("")
                .deviceType("ANDROID")
                .build();

        RequestInfo requestInfo = createRequestInfo("user-1");

        assertThrows(CustomException.class,
                () -> deviceTokenService.registerDeviceTokens(requestInfo, List.of(token)));
    }

    @Test
    void registerDeviceTokens_invalidDeviceType_throwsCustomException() {
        DeviceToken token = DeviceToken.builder()
                .deviceToken("valid-token")
                .deviceType("DESKTOP")
                .build();

        RequestInfo requestInfo = createRequestInfo("user-1");

        assertThrows(CustomException.class,
                () -> deviceTokenService.registerDeviceTokens(requestInfo, List.of(token)));
    }

    @Test
    void registerDeviceTokens_validDeviceTypes_accepted() {
        when(properties.getSaveDeviceTokenTopic()).thenReturn("save-topic");
        RequestInfo requestInfo = createRequestInfo("user-1");

        for (String type : Arrays.asList("ANDROID", "IOS", "WEB", "android", "ios", "web")) {
            DeviceToken token = DeviceToken.builder()
                    .deviceToken("token-" + type)
                    .deviceType(type)
                    .tenantId("t1")
                    .build();

            assertDoesNotThrow(
                    () -> deviceTokenService.registerDeviceTokens(requestInfo, List.of(token)),
                    "Device type " + type + " should be accepted");
        }
    }

    @Test
    void registerDeviceTokens_preservesExistingUserId() {
        when(properties.getSaveDeviceTokenTopic()).thenReturn("save-topic");

        DeviceToken token = DeviceToken.builder()
                .deviceToken("tok")
                .deviceType("ANDROID")
                .userId("existing-user")
                .tenantId("t1")
                .build();

        RequestInfo requestInfo = createRequestInfo("requester-uuid");
        List<DeviceToken> result = deviceTokenService.registerDeviceTokens(requestInfo, List.of(token));

        assertEquals("existing-user", result.get(0).getUserId());
    }

    @Test
    void deleteDeviceTokens_validRequest_publishes() {
        when(properties.getDeleteDeviceTokenTopic()).thenReturn("delete-topic");

        DeviceToken token = DeviceToken.builder()
                .deviceToken("tok-to-delete")
                .userId("user-1")
                .build();

        RequestInfo requestInfo = createRequestInfo("user-1");
        deviceTokenService.deleteDeviceTokens(requestInfo, List.of(token));

        verify(producer).push(any(), eq("delete-topic"), any());
    }

    @Test
    void deleteDeviceTokens_emptyDeviceToken_throwsCustomException() {
        DeviceToken token = DeviceToken.builder()
                .deviceToken("")
                .build();

        RequestInfo requestInfo = createRequestInfo("user-1");

        assertThrows(CustomException.class,
                () -> deviceTokenService.deleteDeviceTokens(requestInfo, List.of(token)));
    }

    @Test
    void deleteDeviceTokens_noUserId_setsFromRequestInfo() {
        when(properties.getDeleteDeviceTokenTopic()).thenReturn("delete-topic");

        DeviceToken token = DeviceToken.builder()
                .deviceToken("tok")
                .build();

        RequestInfo requestInfo = createRequestInfo("requester-uuid");
        deviceTokenService.deleteDeviceTokens(requestInfo, List.of(token));

        assertEquals("requester-uuid", token.getUserId());
    }

    @Test
    void getActiveTokensForUsers_delegatesToRepository() {
        List<String> userIds = List.of("u1", "u2");
        List<DeviceToken> expected = List.of(
                DeviceToken.builder().deviceToken("t1").build()
        );
        when(repository.fetchTokensByUserIds(userIds, "t1")).thenReturn(expected);

        List<DeviceToken> result = deviceTokenService.getActiveTokensForUsers(userIds, "t1");

        assertEquals(expected, result);
        verify(repository).fetchTokensByUserIds(userIds, "t1");
    }

    @Test
    void getTokensByFacilityId_delegatesToRepository() {
        List<DeviceToken> expected = List.of(
                DeviceToken.builder().deviceToken("ft1").facilityId("fac-1").build()
        );
        when(repository.fetchTokensByFacilityId("fac-1", "t1")).thenReturn(expected);

        List<DeviceToken> result = deviceTokenService.getTokensByFacilityId("fac-1", "t1");

        assertEquals(expected, result);
        verify(repository).fetchTokensByFacilityId("fac-1", "t1");
    }
}
