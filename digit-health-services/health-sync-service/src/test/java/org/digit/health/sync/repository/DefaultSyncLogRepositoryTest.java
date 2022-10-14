package org.digit.health.sync.repository;

import org.digit.health.sync.web.models.ReferenceId;
import org.digit.health.sync.web.models.SyncStatus;
import org.digit.health.sync.web.models.dao.SyncLogData;
import org.digit.health.sync.web.models.request.SyncLogSearchDto;
import org.digit.health.sync.web.models.request.SyncLogSearchMapper;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        SyncLogSearchDto syncLogSearchDto = SyncLogSearchDto.builder().reference(ReferenceId.builder()
                .type("campaign")
                .id("id")
                .build()).build();
        List<SyncLogData> searchedData = new ArrayList<>();
        searchedData.add(SyncLogData.builder().build());
        SyncLogData syncLogData = SyncLogSearchMapper.INSTANCE.toData(syncLogSearchDto);

        when(syncLogQueryBuilder.getSQlBasedOn(
                any(SyncLogData.class))
        ).thenReturn("");

        when(jdbcTemplate.query(
                any(String.class),
                any(HashMap.class),
                any(BeanPropertyRowMapper.class))
        ).thenReturn(searchedData);

        List<SyncLogData> results = defaultSyncLogRepository.findByCriteria(syncLogData);

        assertTrue(
                searchedData.size() == results.size() &&
                        searchedData.containsAll(results) &&
                        results.containsAll(searchedData)
        );

        verify(syncLogQueryBuilder,times(1)).getSQlBasedOn(syncLogData);
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

        when(syncLogQueryBuilder.getUpdateSQlBasedOn(any(SyncLogData.class))).thenReturn(GENERATED_UPDATE_QUERY);

        when(jdbcTemplate.update(
                        eq(GENERATED_UPDATE_QUERY), any(HashMap.class)
            )
        ).thenReturn(1);

        defaultSyncLogRepository.update(updatedData);

        verify(jdbcTemplate,times(1)).update(eq(GENERATED_UPDATE_QUERY), eq(in));
        verify(syncLogQueryBuilder,times(1)).getUpdateSQlBasedOn(updatedData);
    }

}