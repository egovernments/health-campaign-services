package org.digit.health.sync.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.digit.health.sync.helper.SyncSearchRequestTestBuilder;
import org.digit.health.sync.web.models.dao.SyncData;
import org.digit.health.sync.web.models.request.SyncSearchDto;
import org.digit.health.sync.web.models.request.SyncSearchMapper;
import org.digit.health.sync.web.models.request.SyncSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SyncRepositoryTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @Mock
    SyncQueryBuilder syncQueryBuilder;

    @InjectMocks
    private SyncRepository syncRepository;

    @BeforeEach
    void setUp() {
        syncRepository = new SyncRepository(jdbcTemplate,syncQueryBuilder);
    }

    @Test
    @DisplayName("should successfully get results from sync repository")
    void shouldSuccessfullyGetResultsFromSyncRepository()  {
        SyncSearchRequest syncSearchRequest = SyncSearchRequestTestBuilder.builder().build();
        List<SyncData> searchedData = new ArrayList<>();
        searchedData.add(SyncData.builder().build());
        SyncSearchDto syncSearchDto = SyncSearchMapper.INSTANCE.toDTO(syncSearchRequest);

        when(jdbcTemplate.query(any(String.class),any(BeanPropertyRowMapper.class))).thenReturn(searchedData);
        syncRepository.findByCriteria(syncSearchDto);
        verify(syncQueryBuilder,times(1)).getSQlBasedOn(syncSearchDto);
    }
}