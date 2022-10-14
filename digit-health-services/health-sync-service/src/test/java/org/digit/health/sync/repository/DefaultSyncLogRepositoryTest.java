package org.digit.health.sync.repository;

import org.digit.health.sync.kafka.Producer;
import org.digit.health.sync.repository.enums.RepositoryErrorCode;
import org.digit.health.sync.web.models.ReferenceId;
import org.digit.health.sync.web.models.SyncStatus;
import org.digit.health.sync.web.models.dao.SyncLogData;
import org.digit.health.sync.web.models.dao.SyncLogDataMapper;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultSyncLogRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    @Qualifier("defaultSyncLogQueryBuilder")
    private SyncLogQueryBuilder syncLogQueryBuilder;

    @InjectMocks
    private DefaultSyncLogRepository defaultSyncLogRepository;

    @Mock
    private Producer producer;

    @BeforeEach
    void setUp() {
        defaultSyncLogRepository = new DefaultSyncLogRepository(jdbcTemplate, syncLogQueryBuilder, producer);
    }


    @Test
    @DisplayName("should successfully get results from sync repository")
    void shouldSuccessfullyGetResultsFromSyncRepository() {
        String SELECT_QUERY = "SELECT * FROM sync_log  WHERE tenantId = :tenantId " +
                "AND referenceId=:referenceId AND referenceIdType=:referenceIdType ";

        List<SyncLogData> searchedData = new ArrayList<>();
        searchedData.add(SyncLogData.builder().build());

        SyncLogData syncLogData = SyncLogData.builder().
                tenantId("tenant-id")
                .referenceId(
                        ReferenceId
                                .builder()
                                .id("ref-id")
                                .type("campaign")
                                .build())
                .build();

        Map<String, Object> in = new HashMap<>();
        in.put("tenantId", syncLogData.getTenantId());
        in.put("id", syncLogData.getSyncId());
        in.put("referenceId", syncLogData.getReferenceId().getId());
        in.put("referenceIdType", syncLogData.getReferenceId().getType());
        in.put("status", syncLogData.getStatus());

        lenient().when(syncLogQueryBuilder.createSelectQuery(
                eq(syncLogData))
        ).thenReturn(SELECT_QUERY);

        lenient().when(jdbcTemplate.query(
                        eq(SELECT_QUERY),
                        eq(in),
                        any(SyncLogDataMapper.class)
                )
        ).thenReturn(searchedData);

        defaultSyncLogRepository.find(syncLogData);

        verify(syncLogQueryBuilder, times(1))
                .createSelectQuery(syncLogData);
        verify(jdbcTemplate, times(1))
                .query(
                        eq(SELECT_QUERY),
                        eq(in),
                        any(SyncLogDataMapper.class)
                );

    }


    @Test
    @DisplayName("should successfully update using sync repository")
    void shouldSuccessfullyUpdateUsingSyncRepository() {
        String GENERATED_UPDATE_QUERY = "UPDATE sync_log SET  status = :status WHERE tenantId = :tenantId AND id=:id";
        SyncLogData updatedData = SyncLogData.builder().status(SyncStatus.CREATED)
                .syncId("sync-id")
                .tenantId("tenant-id")
                .build();

        Map<String, Object> in = new HashMap<>();
        in.put("tenantId", "tenant-id");
        in.put("id", "sync-id");
        in.put("status", SyncStatus.CREATED);

        when(syncLogQueryBuilder.createUpdateQuery(
                any(SyncLogData.class))
        ).thenReturn(GENERATED_UPDATE_QUERY);

        when(jdbcTemplate.update(
                        eq(GENERATED_UPDATE_QUERY), any(HashMap.class)
                )
        ).thenReturn(1);

        defaultSyncLogRepository.update(updatedData);

        verify(jdbcTemplate, times(1)).update(eq(GENERATED_UPDATE_QUERY), eq(in));
        verify(syncLogQueryBuilder, times(1)).createUpdateQuery(updatedData);
    }


    @Test
    @DisplayName("should save sync log and return the same successfully")
    void shouldSaveSyncErrorDetailsLogDataAndReturnSameSuccessfully() {
        SyncLogData expectedData = SyncLogData.builder().build();

        SyncLogData actualData = defaultSyncLogRepository.save(expectedData);

        assertEquals(expectedData, actualData);
        verify(producer, times(1))
                .send(DefaultSyncLogRepository.SAVE_KAFKA_TOPIC, expectedData);
    }

    @Test
    @DisplayName("should return null in case the payload is null")
    void shouldReturnNullInCaseThePayloadIsNull() {
        SyncLogData actualData = defaultSyncLogRepository.save(null);

        assertNull(actualData);
        verify(producer, times(0))
                .send(DefaultSyncLogRepository.SAVE_KAFKA_TOPIC, null);
    }

    @Test
    @DisplayName("should throw custom exception with proper error code in case of any error")
    void shouldThrowCustomExceptionWithErrorCodeInCaseOfAnyError() {
        SyncLogData expectedData = SyncLogData.builder().build();
        doThrow(new RuntimeException("some_message")).when(producer)
                .send(DefaultSyncLogRepository.SAVE_KAFKA_TOPIC, expectedData);
        CustomException ex = null;

        try {
            defaultSyncLogRepository.save(expectedData);
        } catch (CustomException exception) {
            ex = exception;
        }

        assertNotNull(ex);
        assertEquals(RepositoryErrorCode.SAVE_ERROR.name(), ex.getCode());
        assertEquals(RepositoryErrorCode.SAVE_ERROR.message("some_message"),
                ex.getMessage());
    }

}