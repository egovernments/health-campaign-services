package org.egov.individual.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.producer.Producer;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.individual.repository.rowmapper.IndividualRowMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test that drives the REAL search flows (find / findById + the new bulk enrichment) against
 * a local Postgres, exercising every divergence: findById (id-only), find identifier==null, find by
 * identifier, proximity without identifier, proximity + identifier. Encryption (service layer), Kafka and
 * Redis are out of scope for search and are mocked / bypassed. Schema placeholder is resolved to 'public'.
 *
 * Requires a local Postgres at localhost:5432 (db=postgres, postgres/postgres) with the individual tables.
 * Enable with -Dlocal.search.it=true (skips silently otherwise so normal CI runs are unaffected).
 */
class IndividualSearchFlowLocalIT {

    private static final String TENANT = "ITEST";
    private IndividualRepository repository;
    private JdbcTemplate jdbc;
    private final boolean enabled = "true".equals(System.getProperty("local.search.it"));

    @BeforeEach
    void setUp() {
        org.junit.jupiter.api.Assumptions.assumeTrue(enabled, "local.search.it not set — skipping local IT");

        DataSource ds = new DriverManagerDataSource(
                "jdbc:postgresql://localhost:5432/postgres", "postgres", "postgres");
        NamedParameterJdbcTemplate npjt = new NamedParameterJdbcTemplate(ds);
        jdbc = new JdbcTemplate(ds);

        MultiStateInstanceUtil multiStateInstanceUtil = mock(MultiStateInstanceUtil.class);
        try {
            lenient().when(multiStateInstanceUtil.replaceSchemaPlaceholder(anyString(), anyString()))
                    .thenAnswer(inv -> ((String) inv.getArgument(0)).replace(SCHEMA_REPLACE_STRING, "public"));
        } catch (Exception ignored) { }

        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        HashOperations hashOps = mock(HashOperations.class);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);   // cache miss -> always hits DB

        repository = new IndividualRepository(mock(Producer.class), npjt, redisTemplate,
                mock(SelectQueryBuilder.class), new IndividualRowMapper());
        ReflectionTestUtils.setField(repository, "multiStateInstanceUtil", multiStateInstanceUtil);
        ReflectionTestUtils.setField(repository, "timeToLive", "60");

        seed();
    }

    @AfterEach
    void tearDown() {
        if (enabled && jdbc != null) cleanup();
    }

    private void cleanup() {
        jdbc.execute("DELETE FROM public.individual_skill WHERE individualid LIKE 'IT-%'");
        jdbc.execute("DELETE FROM public.individual_identifier WHERE individualid LIKE 'IT-%'");
        jdbc.execute("DELETE FROM public.individual_address WHERE individualid LIKE 'IT-%'");
        jdbc.execute("DELETE FROM public.address WHERE id LIKE 'IT-AD-%'");
        jdbc.execute("DELETE FROM public.individual WHERE id LIKE 'IT-%'");
    }

    private void seed() {
        cleanup();
        // individuals (tenant ITEST)
        ind("IT-1", "Alpha", "9990001111");
        ind("IT-2", "Beta", "9990002222");
        ind("IT-3", "Gamma", "9990003333");   // located via identifier
        ind("IT-4", "Delta", "9990004444");   // located via proximity

        // addresses: IT-1 has TWO (different type -> both kept by rn=1 per type), IT-2 one, IT-4 one with geo
        addr("IT-AD-1a", 0.0, 0.0);
        addr("IT-AD-1b", 0.0, 0.0);
        addr("IT-AD-2", 0.0, 0.0);
        addr("IT-AD-4", 12.9716, 77.5946);    // Bangalore-ish
        link("IT-1", "IT-AD-1a", "PERMANENT");
        link("IT-1", "IT-AD-1b", "CORRESPONDENCE");
        link("IT-2", "IT-AD-2", "PERMANENT");
        link("IT-4", "IT-AD-4", "PERMANENT");

        // identifiers: IT-1 has 2 (grouping), IT-3 has the one we search by
        identifier("IT-ID-1a", "IT-1", "SYSTEM_GENERATED", "SYS-1");
        identifier("IT-ID-1b", "IT-1", "AADHAAR", "AAD-1");
        identifier("IT-ID-3", "IT-3", "SYSTEM_GENERATED", "FIND-ME-3");

        // skills: IT-1 one, IT-2 one
        skill("IT-SK-1", "IT-1");
        skill("IT-SK-2", "IT-2");
    }

    private void ind(String id, String given, String mobile) {
        jdbc.update("INSERT INTO public.individual (id, tenantid, givenname, mobilenumber, isdeleted, createdtime) " +
                "VALUES (?,?,?,?,false, 1000)", id, TENANT, given, mobile);
    }
    private void addr(String id, double lat, double lon) {
        jdbc.update("INSERT INTO public.address (id, tenantid, latitude, longitude, type) VALUES (?,?,?,?,?)",
                id, TENANT, lat, lon, "PERMANENT");
    }
    private void link(String indId, String addrId, String type) {
        jdbc.update("INSERT INTO public.individual_address (individualid, addressid, type, isdeleted, lastmodifiedtime) " +
                "VALUES (?,?,?,false, 2000)", indId, addrId, type);
    }
    private void identifier(String id, String indId, String type, String value) {
        jdbc.update("INSERT INTO public.individual_identifier (id, individualid, identifiertype, identifierid, isdeleted) " +
                "VALUES (?,?,?,?,false)", id, indId, type, value);
    }
    private void skill(String id, String indId) {
        jdbc.update("INSERT INTO public.individual_skill (id, individualid, type, level, experience, isdeleted) " +
                "VALUES (?,?, 'MASON','L1','2', false)", id, indId);
    }

    // ----------------------------- divergence flows -----------------------------

    @Test
    @DisplayName("Branch A — findById (id-only): bulk-enriches; grouping is correct per individual")
    void findByIdBulkEnriches() throws Exception {
        SearchResponse<Individual> resp = repository.findById(TENANT, List.of("IT-1", "IT-2"), "id", false);
        List<Individual> list = resp.getResponse();
        assertEquals(2, list.size());

        Individual i1 = byId(list, "IT-1");
        assertEquals(2, i1.getAddress().size(), "IT-1 should have 2 addresses (one per type)");
        assertEquals(2, i1.getIdentifiers().size(), "IT-1 should have 2 identifiers");
        assertEquals(1, i1.getSkills().size(), "IT-1 should have 1 skill");

        Individual i2 = byId(list, "IT-2");
        assertEquals(1, i2.getAddress().size());
        assertTrue(i2.getIdentifiers() == null || i2.getIdentifiers().isEmpty(), "IT-2 has no identifiers");
        assertEquals(1, i2.getSkills().size());
    }

    @Test
    @DisplayName("Branch B2 — find identifier==null (by mobile): bulk-enriches")
    void findByOtherParamsBulkEnriches() throws Exception {
        IndividualSearch search = IndividualSearch.builder()
                .mobileNumber(new ArrayList<>(List.of("9990001111"))).build();
        SearchResponse<Individual> resp = repository.find(search, 10, 0, TENANT, null, false);
        assertEquals(1, resp.getResponse().size());
        Individual i1 = resp.getResponse().get(0);
        assertEquals("IT-1", i1.getId());
        assertEquals(2, i1.getAddress().size());
        assertEquals(2, i1.getIdentifiers().size());
        assertEquals(1, i1.getSkills().size());
    }

    @Test
    @DisplayName("Branch B3 — find by identifier: locates individual then bulk-enriches (full identifier set)")
    void findByIdentifierBulkEnriches() throws Exception {
        IndividualSearch search = IndividualSearch.builder()
                .identifier(Identifier.builder().identifierType("SYSTEM_GENERATED").identifierId("FIND-ME-3").build())
                .build();
        SearchResponse<Individual> resp = repository.find(search, 10, 0, TENANT, null, false);
        assertEquals(1, resp.getResponse().size());
        Individual i3 = resp.getResponse().get(0);
        assertEquals("IT-3", i3.getId());
        // identifiers now come from the bulk-by-id path -> the individual's full set (here, 1)
        assertEquals(1, i3.getIdentifiers().size());
        assertEquals("FIND-ME-3", i3.getIdentifiers().get(0).getIdentifierId());
    }

    @Test
    @DisplayName("Branch B1b — proximity, no identifier: findByRadius bulk-enriches")
    void findByProximityBulkEnriches() throws Exception {
        IndividualSearch search = IndividualSearch.builder()
                .latitude(12.9716).longitude(77.5946).searchRadius(50.0).build();
        SearchResponse<Individual> resp = repository.find(search, 10, 0, TENANT, null, false);
        assertTrue(resp.getResponse().stream().anyMatch(i -> "IT-4".equals(i.getId())),
                "IT-4 should be within radius");
        Individual i4 = byId(resp.getResponse(), "IT-4");
        assertEquals(1, i4.getAddress().size());
    }

    @Test
    @DisplayName("Branch B1a — proximity + identifier: flow executes without error (pre-existing empty-branch behavior)")
    void findByProximityAndIdentifierExecutes() throws Exception {
        IndividualSearch search = IndividualSearch.builder()
                .latitude(12.9716).longitude(77.5946).searchRadius(50.0)
                .identifier(Identifier.builder().identifierType("SYSTEM_GENERATED").identifierId("FIND-ME-3").build())
                .build();
        // Pre-existing inverted-condition guard in findByRadius means this branch returns an empty response;
        // the point here is that the divergence executes and the enrichment code does not blow up.
        SearchResponse<Individual> resp = repository.find(search, 10, 0, TENANT, null, false);
        assertNotNull(resp);
    }

    private static Individual byId(List<Individual> list, String id) {
        return list.stream().filter(i -> id.equals(i.getId())).findFirst()
                .orElseThrow(() -> new AssertionError("missing " + id));
    }
}
