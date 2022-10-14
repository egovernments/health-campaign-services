package org.digit.health.sync.repository;

import org.digit.health.sync.web.models.SyncStatus;
import org.digit.health.sync.web.models.dao.SyncLogData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.*;

import static org.junit.Assert.assertTrue;
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

    @BeforeEach
    void setUp() {
        defaultSyncLogRepository = new DefaultSyncLogRepository(jdbcTemplate, syncLogQueryBuilder);
    }


    @Test
    @DisplayName("should successfully get results from sync repository")
    void shouldSuccessfullyGetResultsFromSyncRepository()  {
        String SELECT_QUERY = "SELECT * FROM sync_log  WHERE tenantId = :tenantId " +
                "AND referenceId=:referenceId AND referenceIdType=:referenceIdType ";

        List<SyncLogData> searchedData = new ArrayList<>();
        searchedData.add(SyncLogData.builder().build());

        SyncLogData syncLogData = SyncLogData.builder().
                tenantId("tenant-id")
                .referenceIdType("campaign")
                .referenceId("ref-id")
                .build();

        Map<String, Object> in = new HashMap<>();
        in.put("tenantId", syncLogData.getTenantId());
        in.put("id", syncLogData.getId());
        in.put("status", syncLogData.getStatus());
        in.put("referenceId", syncLogData.getReferenceId());
        in.put("referenceIdType", syncLogData.getReferenceIdType());
        in.put("fileStoreId", syncLogData.getFileStoreId());

        when(syncLogQueryBuilder.createSelectQuery(
                eq(syncLogData))
        ).thenReturn(SELECT_QUERY);

        when(jdbcTemplate.query(
                eq(SELECT_QUERY),
                eq(in),
                any(BeanPropertyRowMapper.class)
                )
        ).thenReturn(searchedData);

        defaultSyncLogRepository.find(syncLogData);

        verify(syncLogQueryBuilder,times(1))
                .createSelectQuery(syncLogData);
        verify(jdbcTemplate,times(1))
                .query(
                        eq(SELECT_QUERY),
                        eq(in),
                        any(BeanPropertyRowMapper.class)
                );

    }



    @Test
    @DisplayName("should successfully update using sync repository")
    void shouldSuccessfullyUpdateUsingSyncRepository()  {
        String GENERATED_UPDATE_QUERY = "UPDATE sync_log SET  status = :status WHERE tenantId = :tenantId AND id=:id";
        SyncLogData updatedData =  SyncLogData.builder().status(SyncStatus.CREATED.name())
                .id("sync-id")
                .tenantId("tenant-id")
                .build();

        Map<String, Object> in = new HashMap<>();
        in.put("tenantId", "tenant-id");
        in.put("id", "sync-id");
        in.put("status", SyncStatus.CREATED.name());

        when(syncLogQueryBuilder.createUpdateQuery(
                any(SyncLogData.class))
        ).thenReturn(GENERATED_UPDATE_QUERY);

        when(jdbcTemplate.update(
                        eq(GENERATED_UPDATE_QUERY), any(HashMap.class)
            )
        ).thenReturn(1);

        defaultSyncLogRepository.update(updatedData);

        verify(jdbcTemplate,times(1)).update(eq(GENERATED_UPDATE_QUERY), eq(in));
        verify(syncLogQueryBuilder,times(1)).createUpdateQuery(updatedData);
    }

}