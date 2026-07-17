package org.egov.individual.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.individual.Address;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.individual.Skill;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.individual.helper.IndividualSearchTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.repository.rowmapper.IdentifierRowMapper;
import org.egov.individual.repository.rowmapper.IndividualRowMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    /** Plain substring matcher for a SQL string. */
    private static ArgumentMatcher<String> sqlContains(String needle) {
        return sql -> sql != null && sql.contains(needle);
    }

    /**
     * Matcher for one of the 3 bulk enrichment sub-queries: it targets the sub-table AND uses the bulk
     * bind param ':individualIds'. Requiring ':individualIds' distinguishes it from the main search /
     * count query, which may also reference individual_address (e.g. for a boundary join).
     */
    private static ArgumentMatcher<String> bulkSql(String table) {
        return sql -> sql != null && sql.contains(table) && sql.contains(":individualIds");
    }

    /**
     * Enrichment now goes through the bulk path (queryGroupedByIndividualId → ResultSetExtractor overload),
     * one query per sub-table. Stub each sub-table bulk query to return its grouped map, and any other
     * ResultSetExtractor call (the total-count CTE) to return a Long. All lenient so paths that don't run
     * a count (identifier branch) don't trip strict-stubs.
     */
    private void stubBulkEnrichment(Map<String, List<Address>> addressMap,
                                    Map<String, List<Identifier>> identifierMap,
                                    Map<String, List<Skill>> skillMap,
                                    long count) {
        lenient().when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(ResultSetExtractor.class)))
                .thenReturn(count);
        lenient().when(namedParameterJdbcTemplate.query(argThat(bulkSql("individual_address")), anyMap(), any(ResultSetExtractor.class)))
                .thenReturn(addressMap);
        lenient().when(namedParameterJdbcTemplate.query(argThat(bulkSql("individual_identifier")), anyMap(), any(ResultSetExtractor.class)))
                .thenReturn(identifierMap);
        lenient().when(namedParameterJdbcTemplate.query(argThat(bulkSql("individual_skill")), anyMap(), any(ResultSetExtractor.class)))
                .thenReturn(skillMap);
    }

    @Test
    @DisplayName("findById: fetches from db and bulk-enriches address/identifiers/skills")
    void shouldFindByIdFromDbAndReturnAllTheDependentEntitiesAsWellIfPresent() throws Exception {
        String tenantId = "default";
        String id = "some-id";
        Individual individual = IndividualTestBuilder.builder().withTenantId(tenantId).withId(id).build();

        when(multiStateInstanceUtil.replaceSchemaPlaceholder(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0)); // no-op

        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(IndividualRowMapper.class)))
                .thenReturn(Collections.singletonList(individual));

        stubBulkEnrichment(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 1L);

        individualRepository.findById(tenantId, List.of(id), "id", false);

        verify(namedParameterJdbcTemplate).query(nullable(String.class), anyMap(), any(IndividualRowMapper.class));
        // enrichment is now 3 bulk (ResultSetExtractor) queries, one per sub-table
        verify(namedParameterJdbcTemplate).query(argThat(bulkSql("individual_address")), anyMap(), any(ResultSetExtractor.class));
        verify(namedParameterJdbcTemplate).query(argThat(bulkSql("individual_identifier")), anyMap(), any(ResultSetExtractor.class));
        verify(namedParameterJdbcTemplate).query(argThat(bulkSql("individual_skill")), anyMap(), any(ResultSetExtractor.class));
    }

    @Test
    @DisplayName("find by other params (identifier==null): bulk-enriches in 3 set-based queries")
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

        Individual individual = IndividualTestBuilder.builder().withTenantId("default").withId().build();

        when(multiStateInstanceUtil.replaceSchemaPlaceholder(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0)); // no-op

        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(IndividualRowMapper.class)))
                .thenReturn(Collections.singletonList(individual));

        stubBulkEnrichment(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 0L);

        individualRepository.find(individualSearch, 2, 0, "default", null, true);

        verify(namedParameterJdbcTemplate, times(1)).query(nullable(String.class), anyMap(), any(IndividualRowMapper.class));
        verify(namedParameterJdbcTemplate, times(1)).query(argThat(bulkSql("individual_address")), anyMap(), any(ResultSetExtractor.class));
        verify(namedParameterJdbcTemplate, times(1)).query(argThat(bulkSql("individual_identifier")), anyMap(), any(ResultSetExtractor.class));
        verify(namedParameterJdbcTemplate, times(1)).query(argThat(bulkSql("individual_skill")), anyMap(), any(ResultSetExtractor.class));
    }

    @Test
    @DisplayName("find only by identifier: search identifiers, then bulk-enrich the matched individual")
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
                .withTenantId("default")
                .withId(individualId)
                .build();

        when(multiStateInstanceUtil.replaceSchemaPlaceholder(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // identifier search (RowMapper overload) locates the individual
        when(namedParameterJdbcTemplate.query(anyString(), anyMap(), any(IdentifierRowMapper.class)))
                .thenReturn(Collections.singletonList(identifier));

        when(namedParameterJdbcTemplate.query(anyString(), anyMap(), any(IndividualRowMapper.class)))
                .thenReturn(Collections.singletonList(individual));

        // enrichment via bulk (ResultSetExtractor overload); no count in the identifier branch
        stubBulkEnrichment(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 0L);

        individualRepository.find(individualSearch, 2, 0, "default", null, true);

        verify(namedParameterJdbcTemplate, atLeastOnce()).query(anyString(), anyMap(), any(IdentifierRowMapper.class));
        verify(namedParameterJdbcTemplate).query(anyString(), anyMap(), any(IndividualRowMapper.class));
        verify(namedParameterJdbcTemplate).query(argThat(bulkSql("individual_address")), anyMap(), any(ResultSetExtractor.class));
        verify(namedParameterJdbcTemplate).query(argThat(bulkSql("individual_identifier")), anyMap(), any(ResultSetExtractor.class));
        verify(namedParameterJdbcTemplate).query(argThat(bulkSql("individual_skill")), anyMap(), any(ResultSetExtractor.class));
    }

    @Test
    @DisplayName("find by other params + identifier: bulk-enriches the matched individual")
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

        Individual individual = IndividualTestBuilder.builder().withTenantId("default").withId().build();

        when(multiStateInstanceUtil.replaceSchemaPlaceholder(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // identifier search returns the matching identifier (locates individual)
        when(namedParameterJdbcTemplate.query(
                argThat(sqlContains("individual_identifier")),
                anyMap(),
                any(IdentifierRowMapper.class)
        )).thenReturn(Collections.singletonList(
                Identifier.builder()
                        .identifierId("some-identifier-id")
                        .identifierType("SYSTEM_GENERATED")
                        .individualId("some-id")
                        .build()
        ));

        when(namedParameterJdbcTemplate.query(anyString(), anyMap(), any(IndividualRowMapper.class)))
                .thenReturn(Collections.singletonList(individual));

        stubBulkEnrichment(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 0L);

        individualRepository.find(individualSearch, 2, 0, "default", null, true);

        verify(namedParameterJdbcTemplate, times(1)).query(anyString(), anyMap(), any(IndividualRowMapper.class));
        verify(namedParameterJdbcTemplate, atLeastOnce()).query(anyString(), anyMap(), any(IdentifierRowMapper.class));
        verify(namedParameterJdbcTemplate).query(argThat(bulkSql("individual_address")), anyMap(), any(ResultSetExtractor.class));
        verify(namedParameterJdbcTemplate).query(argThat(bulkSql("individual_skill")), anyMap(), any(ResultSetExtractor.class));
    }

    // ---------------------------------------------------------------------------------------------
    // New tests for the bulk enrichment machinery
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("queryGroupedByIndividualId groups mapped rows by the individualId column")
    void queryGroupedByIndividualIdGroupsRowsByIndividualId() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, true, true, false);
        when(rs.getString("individualId")).thenReturn("i1", "i1", "i2");
        RowMapper<String> rowMapper = (r, rowNum) -> "row" + rowNum;

        when(namedParameterJdbcTemplate.query(anyString(), anyMap(), any(ResultSetExtractor.class)))
                .thenAnswer(inv -> ((ResultSetExtractor<?>) inv.getArgument(2)).extractData(rs));

        @SuppressWarnings("unchecked")
        Map<String, List<String>> grouped = (Map<String, List<String>>) ReflectionTestUtils.invokeMethod(
                individualRepository, "queryGroupedByIndividualId",
                "SELECT ... individualId ...", new HashMap<String, Object>(), rowMapper);

        assertEquals(2, grouped.size());
        assertEquals(Arrays.asList("row0", "row1"), grouped.get("i1"));
        assertEquals(Collections.singletonList("row2"), grouped.get("i2"));
    }

    @Test
    @DisplayName("enrichIndividualsInBulk stitches bulk results onto the matching individual (missing -> empty list)")
    void enrichIndividualsInBulkStitchesResultsPerIndividual() throws InvalidTenantIdException {
        Individual i1 = IndividualTestBuilder.builder().withTenantId("default").withId("i1").build();
        Individual i2 = IndividualTestBuilder.builder().withTenantId("default").withId("i2").build();

        Address a1 = Address.builder().build();
        Identifier id1 = Identifier.builder().individualId("i1").build();
        Skill s2 = Skill.builder().build();

        Map<String, List<Address>> addressMap = new HashMap<>();
        addressMap.put("i1", Collections.singletonList(a1));
        Map<String, List<Identifier>> identifierMap = new HashMap<>();
        identifierMap.put("i1", Collections.singletonList(id1));
        Map<String, List<Skill>> skillMap = new HashMap<>();
        skillMap.put("i2", Collections.singletonList(s2));

        when(multiStateInstanceUtil.replaceSchemaPlaceholder(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        stubBulkEnrichment(addressMap, identifierMap, skillMap, 0L);

        ReflectionTestUtils.invokeMethod(individualRepository, "enrichIndividualsInBulk",
                Arrays.asList(i1, i2), Boolean.FALSE);

        assertEquals(Collections.singletonList(a1), i1.getAddress());
        assertEquals(Collections.singletonList(id1), i1.getIdentifiers());
        assertTrue(i1.getSkills().isEmpty());

        assertTrue(i2.getAddress().isEmpty());
        assertTrue(i2.getIdentifiers().isEmpty());
        assertEquals(Collections.singletonList(s2), i2.getSkills());
    }

    @Test
    @DisplayName("bulk sub-method short-circuits on empty id list without querying")
    void bulkSubMethodReturnsEmptyForEmptyIdsWithoutQuerying() {
        @SuppressWarnings("unchecked")
        Map<String, List<Address>> result = (Map<String, List<Address>>) ReflectionTestUtils.invokeMethod(
                individualRepository, "getAddressForIndividuals",
                "default", Collections.<String>emptyList(), Boolean.FALSE);

        assertTrue(result.isEmpty());
        verify(namedParameterJdbcTemplate, never()).query(anyString(), anyMap(), any(ResultSetExtractor.class));
    }
}
