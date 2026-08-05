package org.egov.individual.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.models.individual.Address;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.Skill;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.individual.repository.rowmapper.AddressRowMapper;
import org.egov.individual.repository.rowmapper.IdentifierRowMapper;
import org.egov.individual.repository.rowmapper.IndividualRowMapper;
import org.egov.individual.repository.rowmapper.SkillRowMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndividualRepositoryBatchEnrichmentTest {

    private static final String TENANT_ID = "default";

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
    void setUp() throws Exception {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        ReflectionTestUtils.setField(individualRepository, "timeToLive", "60");
        ReflectionTestUtils.setField(individualRepository, "multiStateInstanceUtil", multiStateInstanceUtil);
        lenient().when(multiStateInstanceUtil.replaceSchemaPlaceholder(nullable(String.class), eq(TENANT_ID)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(ResultSetExtractor.class)))
                .thenReturn(3L);
    }

    private Individual individual(String id, String clientReferenceId) {
        return Individual.builder()
                .id(id)
                .clientReferenceId(clientReferenceId)
                .tenantId(TENANT_ID)
                .isDeleted(Boolean.FALSE)
                .build();
    }

    private Address address(String individualId, String type) {
        return Address.builder().individualId(individualId).type(null).id(individualId + "-" + type).build();
    }

    private Identifier identifier(String id, String individualId, String individualClientReferenceId) {
        return Identifier.builder()
                .id(id)
                .individualId(individualId)
                .individualClientReferenceId(individualClientReferenceId)
                .identifierType("SYSTEM_GENERATED")
                .build();
    }

    private Skill skill(String id, String individualId) {
        return Skill.builder().id(id).individualId(individualId).build();
    }

    private void stubIndividuals(List<Individual> individuals) {
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(IndividualRowMapper.class)))
                .thenReturn(individuals);
    }

    @Test
    @DisplayName("child queries must be issued once regardless of how many individuals are returned")
    void shouldIssueExactlyOneQueryPerChildTableForManyIndividuals() throws Exception {
        List<Individual> individuals = Arrays.asList(
                individual("id-1", "cr-1"), individual("id-2", "cr-2"), individual("id-3", "cr-3"));
        stubIndividuals(individuals);
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(AddressRowMapper.class)))
                .thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(IdentifierRowMapper.class)))
                .thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(SkillRowMapper.class)))
                .thenReturn(Collections.emptyList());

        individualRepository.findById(TENANT_ID, List.of("id-1", "id-2", "id-3"), "id", false);

        // the whole point of the refactor: constant, not proportional to the number of individuals
        verify(namedParameterJdbcTemplate, times(1))
                .query(nullable(String.class), anyMap(), any(AddressRowMapper.class));
        verify(namedParameterJdbcTemplate, times(1))
                .query(nullable(String.class), anyMap(), any(IdentifierRowMapper.class));
        verify(namedParameterJdbcTemplate, times(1))
                .query(nullable(String.class), anyMap(), any(SkillRowMapper.class));
    }

    @Test
    @DisplayName("each individual receives only its own children, and multiple addresses are preserved")
    void shouldGroupChildrenByOwningIndividual() throws Exception {
        List<Individual> individuals = Arrays.asList(
                individual("id-1", "cr-1"), individual("id-2", "cr-2"), individual("id-3", "cr-3"));
        stubIndividuals(individuals);
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(AddressRowMapper.class)))
                .thenReturn(Arrays.asList(
                        address("id-1", "PERMANENT"), address("id-1", "CORRESPONDENCE"), address("id-2", "PERMANENT")));
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(IdentifierRowMapper.class)))
                .thenReturn(Arrays.asList(identifier("idf-1", "id-1", "cr-1"), identifier("idf-2", "id-2", "cr-2")));
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(SkillRowMapper.class)))
                .thenReturn(Collections.singletonList(skill("sk-1", "id-2")));

        List<Individual> result = individualRepository
                .findById(TENANT_ID, List.of("id-1", "id-2", "id-3"), "id", false).getResponse();

        Individual first = result.stream().filter(i -> "id-1".equals(i.getId())).findFirst().orElseThrow();
        Individual second = result.stream().filter(i -> "id-2".equals(i.getId())).findFirst().orElseThrow();
        Individual third = result.stream().filter(i -> "id-3".equals(i.getId())).findFirst().orElseThrow();

        // an individual can have multiple addresses
        assertEquals(2, first.getAddress().size());
        assertTrue(first.getAddress().stream().allMatch(a -> "id-1".equals(a.getIndividualId())));
        assertEquals(1, second.getAddress().size());
        assertEquals("id-2", second.getAddress().get(0).getIndividualId());

        assertEquals(1, first.getIdentifiers().size());
        assertEquals("idf-1", first.getIdentifiers().get(0).getId());
        assertEquals(1, second.getSkills().size());
        assertTrue(first.getSkills().isEmpty());

        // a childless individual gets non-null, empty, mutable lists, as the per-row queries used to give
        assertNotNull(third.getAddress());
        assertNotNull(third.getIdentifiers());
        assertNotNull(third.getSkills());
        assertTrue(third.getAddress().isEmpty());
        assertDoesNotThrow(() -> third.getAddress().add(address("id-3", "PERMANENT")));
        assertDoesNotThrow(() -> third.getIdentifiers().add(identifier("idf-3", "id-3", "cr-3")));
        assertDoesNotThrow(() -> third.getSkills().add(skill("sk-3", "id-3")));
    }

    @Test
    @DisplayName("a row matching both identifier keys is returned once, as SQL OR would return it")
    void shouldNotDuplicateIdentifierMatchingBothKeys() throws Exception {
        stubIndividuals(Collections.singletonList(individual("id-1", "cr-1")));
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(AddressRowMapper.class)))
                .thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(SkillRowMapper.class)))
                .thenReturn(Collections.emptyList());
        // same row reachable via individualId AND via individualClientReferenceId
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(IdentifierRowMapper.class)))
                .thenReturn(Collections.singletonList(identifier("idf-1", "id-1", "cr-1")));

        List<Individual> result = individualRepository
                .findById(TENANT_ID, List.of("id-1"), "id", false).getResponse();

        assertEquals(1, result.get(0).getIdentifiers().size());
        assertEquals("idf-1", result.get(0).getIdentifiers().get(0).getId());
    }

    @Test
    @DisplayName("identifiers reachable only by client reference id are still resolved when both keys are set")
    void shouldResolveIdentifiersByClientReferenceIdWhenBothKeysPresent() throws Exception {
        stubIndividuals(Collections.singletonList(individual("id-1", "cr-1")));
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(AddressRowMapper.class)))
                .thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(SkillRowMapper.class)))
                .thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(IdentifierRowMapper.class)))
                .thenReturn(Collections.singletonList(identifier("idf-1", null, "cr-1")));

        List<Individual> result = individualRepository
                .findById(TENANT_ID, List.of("id-1"), "id", false).getResponse();

        assertEquals(1, result.get(0).getIdentifiers().size());
        assertEquals("idf-1", result.get(0).getIdentifiers().get(0).getId());
    }

    @Test
    @DisplayName("batched SQL uses IN lists, keeps the address isDeleted filter outside the window, and orders last")
    void shouldBuildBatchedSqlWithCorrectClauseOrdering() throws Exception {
        stubIndividuals(Arrays.asList(individual("id-1", "cr-1"), individual("id-2", "cr-2")));
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(AddressRowMapper.class)))
                .thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(IdentifierRowMapper.class)))
                .thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(SkillRowMapper.class)))
                .thenReturn(Collections.emptyList());

        individualRepository.findById(TENANT_ID, List.of("id-1", "id-2"), "id", false);

        ArgumentCaptor<String> addressSql = ArgumentCaptor.forClass(String.class);
        verify(namedParameterJdbcTemplate)
                .query(addressSql.capture(), anyMap(), any(AddressRowMapper.class));
        String address = addressSql.getValue();
        assertTrue(address.contains("individualId IN (:individualIds)"));
        // soft-delete filter must stay on the outer query, after rn = 1, so a deleted latest row still wins rn
        assertTrue(address.indexOf("ia.isDeleted = false") > address.indexOf("ia.rn = 1"));

        ArgumentCaptor<String> identifierSql = ArgumentCaptor.forClass(String.class);
        verify(namedParameterJdbcTemplate)
                .query(identifierSql.capture(), anyMap(), any(IdentifierRowMapper.class));
        String identifier = identifierSql.getValue();
        assertTrue(identifier.contains("ii.individualId IN (:individualIds)"));
        assertTrue(identifier.contains("ii.individualClientReferenceId IN (:clientReferenceIds)"));

        ArgumentCaptor<String> skillSql = ArgumentCaptor.forClass(String.class);
        verify(namedParameterJdbcTemplate)
                .query(skillSql.capture(), anyMap(), any(SkillRowMapper.class));
        assertTrue(skillSql.getValue().contains("individualId IN (:individualIds)"));
    }

    @Test
    @DisplayName("identifier query omits the client reference arm when no individual carries both keys")
    void shouldOmitClientReferenceArmWhenNoIndividualHasBothKeys() throws Exception {
        stubIndividuals(Collections.singletonList(individual("id-1", null)));
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(AddressRowMapper.class)))
                .thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(IdentifierRowMapper.class)))
                .thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.query(nullable(String.class), anyMap(), any(SkillRowMapper.class)))
                .thenReturn(Collections.emptyList());

        individualRepository.findById(TENANT_ID, List.of("id-1"), "id", false);

        ArgumentCaptor<String> identifierSql = ArgumentCaptor.forClass(String.class);
        verify(namedParameterJdbcTemplate)
                .query(identifierSql.capture(), anyMap(), any(IdentifierRowMapper.class));
        // an empty collection would expand to IN (), which Postgres rejects
        assertFalse(identifierSql.getValue().contains("clientReferenceIds"));
    }

    @Test
    @DisplayName("no child query is issued when the individual query returns nothing")
    void shouldNotQueryChildTablesWhenNoIndividualsFound() throws Exception {
        stubIndividuals(Collections.emptyList());

        individualRepository.findById(TENANT_ID, List.of("missing-id"), "id", false);

        verify(namedParameterJdbcTemplate, times(0))
                .query(anyString(), anyMap(), any(AddressRowMapper.class));
        verify(namedParameterJdbcTemplate, times(0))
                .query(anyString(), anyMap(), any(IdentifierRowMapper.class));
        verify(namedParameterJdbcTemplate, times(0))
                .query(anyString(), anyMap(), any(SkillRowMapper.class));
    }
}
