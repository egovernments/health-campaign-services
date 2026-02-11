package org.egov.individual.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.individual.helper.IndividualSearchTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.repository.rowmapper.AddressRowMapper;
import org.egov.individual.repository.rowmapper.IdentifierRowMapper;
import org.egov.individual.repository.rowmapper.IndividualRowMapper;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.individual.repository.rowmapper.SkillRowMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndividualRepositoryTest {

    @InjectMocks
    private IndividualRepository individualRepository;

    @Mock
    private MultiStateInstanceUtil multiStateInstanceUtil;

    @Mock(lenient = true)
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
        ReflectionTestUtils.setField(individualRepository, "multiStateInstanceUtil", multiStateInstanceUtil);
    }

    @Test
    @DisplayName("should find by id from db and return all the dependent entities as well if present")
    void shouldFindByIdFromDbAndReturnAllTheDependentEntitiesAsWellIfPresent() throws Exception {
        String tenantId = "default";
        String id = "some-id";
        Individual individual = IndividualTestBuilder.builder().withId(id).build();

        when(multiStateInstanceUtil.replaceSchemaPlaceholder(anyString(), eq(tenantId)))
                .thenAnswer(invocation -> invocation.getArgument(0)); // no-op

        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(IndividualRowMapper.class)))
                .thenReturn(Collections.singletonList(individual));

        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(AddressRowMapper.class)))
                .thenReturn(Collections.emptyList());

        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(IdentifierRowMapper.class)))
                .thenReturn(Collections.emptyList());

        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(ResultSetExtractor.class)))
                .thenReturn(1L);

        individualRepository.findById(tenantId, List.of(id), "id", false);

        verify(namedParameterJdbcTemplate).query(nullable(String.class), anyMap(), any(IndividualRowMapper.class));
        verify(namedParameterJdbcTemplate).query(nullable(String.class), anyMap(), any(AddressRowMapper.class));
        verify(namedParameterJdbcTemplate).query(nullable(String.class), anyMap(), any(IdentifierRowMapper.class));
        verify(namedParameterJdbcTemplate).query(nullable(String.class), anyMap(), any(ResultSetExtractor.class));
    }


    @Test
    @DisplayName("should find by other params from db and return all the dependent entities as well if present")
    void shouldFindOtherParamsFromDbAndReturnAllTheDependentEntitiesAsWellIfPresent() throws QueryBuilderException, InvalidTenantIdException {
        IndividualSearch individualSearch = IndividualSearchTestBuilder.builder()
                .byId()
                .byClientReferenceId()
                .byUserUUID()
                .byGender()
                .byName()
                .byDateOfBirth()
                .byBoundaryCode()
                .build();

        Individual individual = IndividualTestBuilder.builder().withId().build();

        when(multiStateInstanceUtil.replaceSchemaPlaceholder(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0)); // no-op

        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(IndividualRowMapper.class)))
                .thenReturn(Collections.singletonList(individual));

        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(AddressRowMapper.class)))
                .thenReturn(Collections.emptyList());

        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(IdentifierRowMapper.class)))
                .thenReturn(Collections.emptyList());

        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(ResultSetExtractor.class)))
                .thenReturn(0L);

        individualRepository.find(individualSearch, 2, 0, "default", null, true);

        verify(namedParameterJdbcTemplate, times(1))
                .query(nullable(String.class), anyMap(), any(IndividualRowMapper.class));
        verify(namedParameterJdbcTemplate, times(1))
                .query(nullable(String.class), anyMap(), any(AddressRowMapper.class));
        verify(namedParameterJdbcTemplate, times(1))
                .query(nullable(String.class), anyMap(), any(IdentifierRowMapper.class));
        verify(namedParameterJdbcTemplate, times(1))
                .query(nullable(String.class), anyMap(), any(ResultSetExtractor.class));
    }

    @Test
    @DisplayName("should find only by identifier")
    void shouldFindOnlyByIdentifier() throws QueryBuilderException, InvalidTenantIdException {
        String individualId = "some-id";

        IndividualSearch individualSearch = IndividualSearchTestBuilder.builder()
                .byIdentifier()
                .build();

        Identifier identifier = Identifier.builder()
                .individualId(individualId)
                .identifierId("some-identifier-id")
                .identifierType("SYSTEM_GENERATED")
                .build();

        Individual individual = IndividualTestBuilder.builder()
                .withId(individualId)
                .build();

        // Stub schema replacement
        when(multiStateInstanceUtil.replaceSchemaPlaceholder(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Stub queries
        when(namedParameterJdbcTemplate.query(anyString(), anyMap(), any(IdentifierRowMapper.class)))
                .thenReturn(Collections.singletonList(identifier));

        when(namedParameterJdbcTemplate.query(anyString(), anyMap(), any(IndividualRowMapper.class)))
                .thenReturn(Collections.singletonList(individual));

        when(namedParameterJdbcTemplate.query(anyString(), anyMap(), any(AddressRowMapper.class)))
                .thenReturn(Collections.emptyList());

        when(namedParameterJdbcTemplate.query(anyString(), anyMap(), any(ResultSetExtractor.class)))
                .thenReturn(1L);

        lenient().when(namedParameterJdbcTemplate.query(
                contains("individual_skill"),
                anyMap(),
                any(SkillRowMapper.class)
        )).thenReturn(Collections.emptyList());


        // Act
        individualRepository.find(individualSearch, 2, 0, "default", null, true);

        // Verify
        verify(namedParameterJdbcTemplate, atLeastOnce())
                .query(anyString(), anyMap(), any(IdentifierRowMapper.class));
        verify(namedParameterJdbcTemplate)
                .query(anyString(), anyMap(), any(IndividualRowMapper.class));
        verify(namedParameterJdbcTemplate)
                .query(anyString(), anyMap(), any(AddressRowMapper.class));
        verify(namedParameterJdbcTemplate)
                .query(anyString(), anyMap(), any(SkillRowMapper.class));
    }

    @Test
    @DisplayName("should find by other params and identifier from db and return all the dependent entities as well if present")
    void shouldFindOtherParamsAndIdentifierFromDbAndReturnAllTheDependentEntitiesAsWellIfPresent() throws QueryBuilderException, InvalidTenantIdException {
        IndividualSearch individualSearch = IndividualSearchTestBuilder.builder()
                .byId()
                .byClientReferenceId()
                .byUserUUID()
                .byGender()
                .byName()
                .byDateOfBirth()
                .byBoundaryCode()
                .byIdentifier()
                .build();

        Individual individual = IndividualTestBuilder.builder().withId().build();

        when(multiStateInstanceUtil.replaceSchemaPlaceholder(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(namedParameterJdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("individual_identifier")),
                anyMap(),
                any(IdentifierRowMapper.class)
        )).thenReturn(Collections.singletonList(
                Identifier.builder()
                        .identifierId("some-identifier-id")
                        .identifierType("SYSTEM_GENERATED")
                        .individualId("some-id")
                        .build()
        ));

        when(namedParameterJdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("FROM {schema}.individual")),
                anyMap(),
                any(IndividualRowMapper.class)
        )).thenReturn(Collections.singletonList(individual));

        when(namedParameterJdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("individual_address")),
                anyMap(),
                any(AddressRowMapper.class)
        )).thenReturn(Collections.emptyList());

        when(namedParameterJdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("individual_skill")),
                anyMap(),
                any(SkillRowMapper.class)
        )).thenReturn(Collections.emptyList());


        // Act
        individualRepository.find(individualSearch, 2, 0, "default", null, true);

        // Verify
        verify(namedParameterJdbcTemplate, times(1)).query(anyString(), anyMap(), any(IndividualRowMapper.class));
        verify(namedParameterJdbcTemplate, times(1)).query(anyString(), anyMap(), any(AddressRowMapper.class));
        verify(namedParameterJdbcTemplate, atLeastOnce()).query(anyString(), anyMap(), any(IdentifierRowMapper.class));
        verify(namedParameterJdbcTemplate, times(1)).query(anyString(), anyMap(), any(SkillRowMapper.class));

    }

}