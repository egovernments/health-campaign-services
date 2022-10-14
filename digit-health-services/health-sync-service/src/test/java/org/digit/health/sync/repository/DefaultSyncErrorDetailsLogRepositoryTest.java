package org.digit.health.sync.repository;

import org.digit.health.sync.kafka.Producer;
import org.digit.health.sync.repository.enums.RepositoryErrorCode;
import org.digit.health.sync.web.models.dao.SyncErrorDetailsLogData;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultSyncErrorDetailsLogRepositoryTest {

    @Mock
    private Producer producer;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private SyncErrorDetailsLogRepository repository;

    @BeforeEach
    void setUp() {
        SyncErrorDetailsLogQueryBuilder queryBuilder = new DefaultSyncErrorDetailsLogQueryBuilder();
        repository =
                new DefaultSyncErrorDetailsLogRepository(producer, jdbcTemplate, queryBuilder);
    }

    @Test
    @DisplayName("should save sync error details log and return the same successfully")
    void shouldSaveSyncErrorDetailsLogDataAndReturnSameSuccessfully() {
        SyncErrorDetailsLogData expectedData = SyncErrorDetailsLogData.builder().build();

        SyncErrorDetailsLogData actualData = repository.save(expectedData);

        assertEquals(expectedData, actualData);
        verify(producer, times(1))
                .send(DefaultSyncErrorDetailsLogRepository.SAVE_KAFKA_TOPIC, expectedData);
    }

    @Test
    @DisplayName("should return null in case the payload is null")
    void shouldReturnNullInCaseThePayloadIsNull() {
        SyncErrorDetailsLogData actualData = repository.save(null);

        assertNull(actualData);
        verify(producer, times(0))
                .send(DefaultSyncErrorDetailsLogRepository.SAVE_KAFKA_TOPIC, null);
    }

    @Test
    @DisplayName("should throw custom exception with proper error code in case of any error")
    void shouldThrowCustomExceptionWithErrorCodeInCaseOfAnyError() {
        SyncErrorDetailsLogData expectedData = SyncErrorDetailsLogData.builder().build();
        doThrow(new RuntimeException("some_message")).when(producer)
                .send(DefaultSyncErrorDetailsLogRepository.SAVE_KAFKA_TOPIC, expectedData);
        CustomException ex = null;

        try {
            repository.save(expectedData);
        } catch (CustomException exception) {
            ex = exception;
        }

        assertNotNull(ex);
        assertEquals(RepositoryErrorCode.SAVE_ERROR.name(), ex.getCode());
        assertEquals(RepositoryErrorCode.SAVE_ERROR.message("some_message"),
                ex.getMessage());
    }

    @Test
    @DisplayName("should return a list of sync error details log based on syncId")
    void shouldReturnAListOfSyncErrorDetailsLogBasedOnSyncId() {
        SyncErrorDetailsLogData syncErrorDetailsLogData = SyncErrorDetailsLogData.builder()
                .tenantId("some-tenant-id")
                .syncId("some-sync-id")
                .build();
        List<SyncErrorDetailsLogData> expectedDataList = Collections
                .singletonList(syncErrorDetailsLogData);
        when(jdbcTemplate.query(
                        eq("SELECT * FROM sync_error_details_log WHERE tenantId=:tenantId AND syncId=:syncId"),
                        any(Map.class),
                        any(BeanPropertyRowMapper.class))).thenReturn(expectedDataList);

        List<SyncErrorDetailsLogData> actualDataList = repository
                .find(syncErrorDetailsLogData);

        assertEquals(expectedDataList, actualDataList);
    }

    @Test
    @DisplayName("should return a list of sync error details log based on recordIdType")
    void shouldReturnAListOfSyncErrorDetailsLogBasedOnRecordIdType() {
        SyncErrorDetailsLogData syncErrorDetailsLogData = SyncErrorDetailsLogData.builder()
                .tenantId("some-tenant-id")
                .recordIdType("REGISTRATION")
                .build();
        List<SyncErrorDetailsLogData> expectedDataList = Collections
                .singletonList(syncErrorDetailsLogData);
        when(jdbcTemplate.query(
                eq("SELECT * FROM sync_error_details_log WHERE tenantId=:tenantId " +
                        "AND recordIdType=:recordIdType"),
                any(Map.class),
                any(BeanPropertyRowMapper.class))).thenReturn(expectedDataList);

        List<SyncErrorDetailsLogData> actualDataList = repository
                .find(syncErrorDetailsLogData);

        assertEquals(expectedDataList, actualDataList);
    }

    @Test
    @DisplayName("should return a list of sync error details log based on recordId")
    void shouldReturnAListOfSyncErrorDetailsLogBasedOnRecordId() {
        SyncErrorDetailsLogData syncErrorDetailsLogData = SyncErrorDetailsLogData.builder()
                .tenantId("some-tenant-id")
                .recordId("some-id")
                .build();
        List<SyncErrorDetailsLogData> expectedDataList = Collections
                .singletonList(syncErrorDetailsLogData);
        when(jdbcTemplate.query(
                eq("SELECT * FROM sync_error_details_log WHERE tenantId=:tenantId " +
                        "AND recordId=:recordId"),
                any(Map.class),
                any(BeanPropertyRowMapper.class))).thenReturn(expectedDataList);

        List<SyncErrorDetailsLogData> actualDataList = repository
                .find(syncErrorDetailsLogData);

        assertEquals(expectedDataList, actualDataList);
    }

    @Test
    @DisplayName("should return a list of sync error details log based on syncId, recordId and recordIdType")
    void shouldReturnAListOfSyncErrorDetailsLogBasedOnSyncIdRecordIdAndRecordIdType() {
        SyncErrorDetailsLogData syncErrorDetailsLogData = SyncErrorDetailsLogData.builder()
                .tenantId("some-tenant-id")
                .syncId("some-sync-id")
                .recordId("some-id")
                .recordIdType("REGISTRATION")
                .build();
        List<SyncErrorDetailsLogData> expectedDataList = Collections
                .singletonList(syncErrorDetailsLogData);
        when(jdbcTemplate.query(
                eq("SELECT * FROM sync_error_details_log WHERE tenantId=:tenantId " +
                        "AND syncId=:syncId AND recordId=:recordId AND recordIdType=:recordIdType"),
                any(Map.class),
                any(BeanPropertyRowMapper.class))).thenReturn(expectedDataList);

        List<SyncErrorDetailsLogData> actualDataList = repository
                .find(syncErrorDetailsLogData);

        assertEquals(expectedDataList, actualDataList);
    }
}
