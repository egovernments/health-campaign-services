package org.egov.web.notification.push.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.web.notification.push.repository.rowmappers.DeviceTokenRowMapper;
import org.egov.web.notification.push.web.contract.DeviceToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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

    @Spy
    private MultiStateInstanceUtil multiStateInstanceUtil = new MultiStateInstanceUtil(1, true, 0);

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
    @Test
    void centralInstance_validTenantIds_resolveSchemaAsBefore() {
        when(namedParameterJdbcTemplate.query(anyString(), any(Map.class), eq(rowMapper)))
                .thenReturn(Collections.emptyList());

        repository.fetchTokensByUserIds(List.of("user-1"), "ba");
        repository.fetchTokensByUserIds(List.of("user-1"), "ba.city_a");

        org.mockito.Mockito.verify(namedParameterJdbcTemplate, org.mockito.Mockito.times(2)).query(
                org.mockito.ArgumentMatchers.contains("FROM ba.eg_push_device_tokens"), any(Map.class), eq(rowMapper));
    }

    @Test
    void centralInstance_tenantIdWithSqlCharacters_isRejectedBeforeQuerying() {
        org.egov.tracer.model.CustomException ex = assertThrows(org.egov.tracer.model.CustomException.class,
                () -> repository.fetchTokensByUserIds(List.of("user-1"), "eg_push_device_tokens UNION SELECT 1--"));

        assertEquals("INVALID_TENANT_ID", ex.getCode());
        org.mockito.Mockito.verifyNoInteractions(namedParameterJdbcTemplate);
    }

    @Test
    void centralInstance_dottedTenantIdWithBadSchemaPart_isRejected() {
        org.egov.tracer.model.CustomException ex = assertThrows(org.egov.tracer.model.CustomException.class,
                () -> repository.fetchTokensByFacilityId("fac-1", "ba;drop table x.city"));

        assertEquals("INVALID_TENANT_ID", ex.getCode());
        org.mockito.Mockito.verifyNoInteractions(namedParameterJdbcTemplate);
    }

    @Test
    void missingTenantId_keepsExistingErrorCode() {
        org.egov.tracer.model.CustomException ex = assertThrows(org.egov.tracer.model.CustomException.class,
                () -> repository.fetchTokensByUserIds(List.of("user-1"), ""));

        assertEquals("MISSING_TENANT_ID", ex.getCode());
        org.mockito.Mockito.verifyNoInteractions(namedParameterJdbcTemplate);
    }

    @Test
    void nonCentralInstance_usesDefaultSchemaAsBefore() {
        org.springframework.test.util.ReflectionTestUtils.setField(repository, "multiStateInstanceUtil",
                new MultiStateInstanceUtil(1, false, 0));
        when(namedParameterJdbcTemplate.update(anyString(), any(Map.class))).thenReturn(1);

        repository.deleteByDeviceTokens(List.of("tok1"), "ba");

        org.mockito.Mockito.verify(namedParameterJdbcTemplate).update(
                eq("DELETE FROM eg_push_device_tokens WHERE devicetoken IN (:deviceTokens)"), any(Map.class));
    }
}
