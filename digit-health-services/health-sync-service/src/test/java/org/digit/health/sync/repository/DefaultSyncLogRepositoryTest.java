package org.digit.health.sync.repository;

import org.digit.health.sync.helper.SyncSearchRequestTestBuilder;
import org.digit.health.sync.web.models.dao.SyncLogData;
import org.digit.health.sync.web.models.request.SyncLogSearchDto;
import org.digit.health.sync.web.models.request.SyncLogSearchMapper;
import org.digit.health.sync.web.models.request.SyncLogSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultSyncLogRepositoryTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @Mock
    @Qualifier("defaultSyncLogQueryBuilder")
    SyncLogQueryBuilder syncLogQueryBuilder;

    @InjectMocks
    private DefaultSyncLogRepository defaultSyncLogRepository;

    @BeforeEach
    void setUp() {
        defaultSyncLogRepository = new DefaultSyncLogRepository(jdbcTemplate, syncLogQueryBuilder);
    }

    @Test
    @DisplayName("should successfully get results from sync repository")
    void shouldSuccessfullyGetResultsFromSyncRepository()  {
        SyncLogSearchRequest syncLogSearchRequest = SyncSearchRequestTestBuilder.builder().withReferenceId().build();
        List<SyncLogData> searchedData = new ArrayList<>();
        searchedData.add(SyncLogData.builder().build());
        SyncLogSearchDto syncLogSearchDto = SyncLogSearchMapper.INSTANCE.toDTO(syncLogSearchRequest);

        when(syncLogQueryBuilder.getSQlBasedOn(any(SyncLogSearchDto.class))).thenReturn("");
        when(jdbcTemplate.query(any(String.class),any(BeanPropertyRowMapper.class))).thenReturn(searchedData);

        List<SyncLogData> results = defaultSyncLogRepository.findByCriteria(syncLogSearchDto);

        assertTrue(
                searchedData.size() == results.size() &&
                        searchedData.containsAll(results) &&
                        results.containsAll(searchedData)
        );

        verify(syncLogQueryBuilder,times(1)).getSQlBasedOn(syncLogSearchDto);
    }
}