package org.egov.individual.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.individual.helper.IndividualSearchTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.repository.rowmapper.AddressRowMapper;
import org.egov.individual.repository.rowmapper.IdentifierRowMapper;
import org.egov.individual.repository.rowmapper.IndividualRowMapper;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualSearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndividualRepositoryTest {

    @InjectMocks
    private IndividualRepository individualRepository;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private SelectQueryBuilder selectQueryBuilder;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private IndividualRowMapper individualRowMapper;

    @Mock
    private HashOperations hashOperations;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        ReflectionTestUtils.setField(individualRepository, "timeToLive", "60");
    }

    @Test
    @DisplayName("should find by id from db and return all the dependent entities as well if present")
    void shouldFindByIdFromDbAndReturnAllTheDependentEntitiesAsWellIfPresent() throws QueryBuilderException {
        IndividualSearch individualSearch = IndividualSearchTestBuilder.builder()
                .byId()
                .build();
        Individual individual = IndividualTestBuilder.builder()
                .withId()
                .build();
        when(hashOperations.entries(anyString())).thenReturn(Collections.emptyMap());
        when(namedParameterJdbcTemplate.query(anyString(), anyMap(), any(IndividualRowMapper.class)))
                .thenReturn(Collections.singletonList(individual));

        individualRepository.findById(Arrays.asList("some-id"), "id", false);

        verify(namedParameterJdbcTemplate, times(1))
                .query(anyString(), anyMap(), any(IndividualRowMapper.class));
        verify(namedParameterJdbcTemplate, times(1))
                .query(anyString(), anyMap(), any(AddressRowMapper.class));
        verify(namedParameterJdbcTemplate, times(1))
                .query(anyString(), anyMap(), any(IdentifierRowMapper.class));
    }
}