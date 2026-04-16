package org.egov.web.notification.push.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.egov.tracer.model.CustomException;
import org.egov.web.notification.push.config.PushProperties;
import org.egov.web.notification.push.repository.rowmappers.DeviceTokenRowMapper;
import org.egov.web.notification.push.web.contract.DeviceToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DeviceTokenRepositoryTest {

    @InjectMocks
    private DeviceTokenRepository repository;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private DeviceTokenRowMapper rowMapper;

    @Mock
    private PushProperties properties;

    @Test
    void fetchTokensByUserIds_validUserIds_returnsTokens() {
        List<String> userIds = List.of("user-1", "user-2");
        List<DeviceToken> expected = List.of(
                DeviceToken.builder().deviceToken("tok1").userId("user-1").build()
        );

        when(namedParameterJdbcTemplate.query(
                anyString(),
                any(Map.class),
                eq(rowMapper)))
                .thenReturn(expected);

        List<DeviceToken> result = repository.fetchTokensByUserIds(userIds, "ba");

        assertEquals(1, result.size());
        assertEquals("tok1", result.get(0).getDeviceToken());
    }

    @Test
    void fetchTokensByUserIds_nullUserIds_returnsEmpty() {
        List<DeviceToken> result = repository.fetchTokensByUserIds(null, "ba");

        assertTrue(result.isEmpty());
    }

    @Test
    void fetchTokensByUserIds_emptyUserIds_returnsEmpty() {
        List<DeviceToken> result = repository.fetchTokensByUserIds(Collections.emptyList(), "ba");

        assertTrue(result.isEmpty());
    }

    @Test
    void fetchTokensByUserIds_dbException_throwsRuntimeException() {
        List<String> userIds = List.of("user-1");

        when(namedParameterJdbcTemplate.query(
                anyString(),
                any(Map.class),
                eq(rowMapper)))
                .thenThrow(new RuntimeException("DB down"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> repository.fetchTokensByUserIds(userIds, "ba"));
        assertEquals("Error while fetching device tokens", exception.getMessage());
    }

    @Test
    void fetchTokensByFacilityId_validFacilityId_returnsTokens() {
        List<DeviceToken> expected = List.of(
                DeviceToken.builder().deviceToken("ftok1").facilityId("fac-1").build(),
                DeviceToken.builder().deviceToken("ftok2").facilityId("fac-1").build()
        );

        when(namedParameterJdbcTemplate.query(
                anyString(),
                any(Map.class),
                eq(rowMapper)))
                .thenReturn(expected);

        List<DeviceToken> result = repository.fetchTokensByFacilityId("fac-1", "ba");

        assertEquals(2, result.size());
    }

    @Test
    void fetchTokensByFacilityId_nullFacilityId_returnsEmpty() {
        List<DeviceToken> result = repository.fetchTokensByFacilityId(null, "ba");

        assertTrue(result.isEmpty());
    }

    @Test
    void fetchTokensByFacilityId_emptyFacilityId_returnsEmpty() {
        List<DeviceToken> result = repository.fetchTokensByFacilityId("", "ba");

        assertTrue(result.isEmpty());
    }

    @Test
    void fetchTokensByFacilityId_dbException_throwsRuntimeException() {
        when(namedParameterJdbcTemplate.query(
                anyString(),
                any(Map.class),
                eq(rowMapper)))
                .thenThrow(new RuntimeException("Connection refused"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> repository.fetchTokensByFacilityId("fac-err", "ba"));
        assertEquals("Error while fetching device tokens by facilityId", exception.getMessage());
    }

    @Test
    void fetchTokensByUserIds_unsafeDerivedSchema_throwsCustomException() {
        when(properties.getIsCentralInstance()).thenReturn(true);
        when(properties.getSchemaIndexPosition()).thenReturn(1);

        CustomException exception = assertThrows(CustomException.class,
                () -> repository.fetchTokensByUserIds(List.of("user-1"), "in.bad-schema.tenant"));

        assertEquals("INVALID_TENANT_ID", exception.getCode());
    }

    @Test
    void fetchTokensByFacilityIdAndRole_bindsWholeRolePattern() {
        when(namedParameterJdbcTemplate.query(
                anyString(),
                any(Map.class),
                eq(rowMapper)))
                .thenReturn(Collections.emptyList());

        repository.fetchTokensByFacilityIdAndRole("facility-1", "ANM", "ba");

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(namedParameterJdbcTemplate).query(anyString(), paramsCaptor.capture(), eq(rowMapper));

        assertEquals("%,ANM,%", paramsCaptor.getValue().get("rolePattern"));
    }
}
