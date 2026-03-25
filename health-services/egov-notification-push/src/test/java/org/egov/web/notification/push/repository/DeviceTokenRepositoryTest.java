package org.egov.web.notification.push.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.egov.web.notification.push.config.PushProperties;
import org.egov.web.notification.push.repository.rowmappers.DeviceTokenRowMapper;
import org.egov.web.notification.push.web.contract.DeviceToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;

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
    void fetchTokensByUserIds_dbException_returnsEmpty() {
        List<String> userIds = List.of("user-1");

        when(namedParameterJdbcTemplate.query(
                anyString(),
                any(Map.class),
                eq(rowMapper)))
                .thenThrow(new RuntimeException("DB down"));

        List<DeviceToken> result = repository.fetchTokensByUserIds(userIds, "ba");

        assertTrue(result.isEmpty());
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
    void fetchTokensByFacilityId_dbException_returnsEmpty() {
        when(namedParameterJdbcTemplate.query(
                anyString(),
                any(Map.class),
                eq(rowMapper)))
                .thenThrow(new RuntimeException("Connection refused"));

        List<DeviceToken> result = repository.fetchTokensByFacilityId("fac-err", "ba");

        assertTrue(result.isEmpty());
    }
}
