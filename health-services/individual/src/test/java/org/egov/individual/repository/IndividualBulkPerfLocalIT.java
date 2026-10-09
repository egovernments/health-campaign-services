package org.egov.individual.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.models.individual.Individual;
import org.egov.common.producer.Producer;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.individual.repository.rowmapper.IndividualRowMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
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
import java.util.List;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Same-infra local before/after for the enrichment change: seeds N individuals (each with addresses,
 * identifiers, skills) in the local Postgres and times the OLD per-record N+1 (enrichIndividuals) vs the
 * NEW bulk path (enrichIndividualsInBulk) on the identical rows. Isolates exactly what changed: ~3N
 * per-record round-trips collapse to 3 set-based queries.
 *
 * Enable with -Dlocal.search.it=true. Requires local Postgres (localhost:5432, postgres/postgres).
 */
class IndividualBulkPerfLocalIT {

    private static final String TENANT = "ITEST";
    private static final int N = 100;          // individuals
    private IndividualRepository repository;
    private JdbcTemplate jdbc;
    private final boolean enabled = "true".equals(System.getProperty("local.search.it"));

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(enabled, "local.search.it not set — skipping local perf IT");
        DataSource ds = new DriverManagerDataSource(
                "jdbc:postgresql://localhost:5432/postgres", "postgres", "postgres");
        NamedParameterJdbcTemplate npjt = new NamedParameterJdbcTemplate(ds);
        jdbc = new JdbcTemplate(ds);

        MultiStateInstanceUtil util = mock(MultiStateInstanceUtil.class);
        try {
            lenient().when(util.replaceSchemaPlaceholder(anyString(), anyString()))
                    .thenAnswer(inv -> ((String) inv.getArgument(0)).replace(SCHEMA_REPLACE_STRING, "public"));
        } catch (Exception ignored) { }

        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        HashOperations hashOps = mock(HashOperations.class);
        lenient().when(redis.opsForHash()).thenReturn(hashOps);

        repository = new IndividualRepository(mock(Producer.class), npjt, redis,
                mock(SelectQueryBuilder.class), new IndividualRowMapper());
        ReflectionTestUtils.setField(repository, "multiStateInstanceUtil", util);
        ReflectionTestUtils.setField(repository, "timeToLive", "60");

        seed();
    }

    @AfterEach
    void tearDown() {
        if (enabled && jdbc != null) cleanup();
    }

    private void cleanup() {
        jdbc.execute("DELETE FROM public.individual_skill WHERE individualid LIKE 'PERF-%'");
        jdbc.execute("DELETE FROM public.individual_identifier WHERE individualid LIKE 'PERF-%'");
        jdbc.execute("DELETE FROM public.individual_address WHERE individualid LIKE 'PERF-%'");
        jdbc.execute("DELETE FROM public.address WHERE id LIKE 'PERF-AD-%'");
        jdbc.execute("DELETE FROM public.individual WHERE id LIKE 'PERF-%'");
    }

    private void seed() {
        cleanup();
        for (int i = 1; i <= N; i++) {
            String id = "PERF-" + i;
            jdbc.update("INSERT INTO public.individual (id, tenantid, givenname, mobilenumber, isdeleted, createdtime) VALUES (?,?,?,?,false,1000)",
                    id, TENANT, "Name" + i, "9" + String.format("%09d", i));
            // 2 addresses (distinct type)
            for (String type : new String[]{"PERMANENT", "CORRESPONDENCE"}) {
                String aid = "PERF-AD-" + i + "-" + type;
                jdbc.update("INSERT INTO public.address (id, tenantid, type) VALUES (?,?,?)", aid, TENANT, type);
                jdbc.update("INSERT INTO public.individual_address (individualid, addressid, type, isdeleted, lastmodifiedtime) VALUES (?,?,?,false,2000)",
                        id, aid, type);
            }
            // 2 identifiers
            jdbc.update("INSERT INTO public.individual_identifier (id, individualid, identifiertype, identifierid, isdeleted) VALUES (?,?,?,?,false)",
                    "PERF-ID-" + i + "-a", id, "SYSTEM_GENERATED", "SYS-" + i);
            jdbc.update("INSERT INTO public.individual_identifier (id, individualid, identifiertype, identifierid, isdeleted) VALUES (?,?,?,?,false)",
                    "PERF-ID-" + i + "-b", id, "AADHAAR", "AAD-" + i);
            // 1 skill
            jdbc.update("INSERT INTO public.individual_skill (id, individualid, type, level, experience, isdeleted) VALUES (?,?, 'MASON','L1','2', false)",
                    "PERF-SK-" + i, id);
        }
    }

    private List<Individual> freshList() {
        List<Individual> list = new ArrayList<>();
        for (int i = 1; i <= N; i++) {
            list.add(Individual.builder().id("PERF-" + i).tenantId(TENANT).build());
        }
        return list;
    }

    @Test
    @DisplayName("PERF: old N+1 (enrichIndividuals) vs new bulk (enrichIndividualsInBulk) on N individuals")
    void compareEnrichmentPerformance() {
        // warmup (JIT + connection pool) — one run of each, untimed
        ReflectionTestUtils.invokeMethod(repository, "enrichIndividuals", freshList(), Boolean.FALSE);
        ReflectionTestUtils.invokeMethod(repository, "enrichIndividualsInBulk", freshList(), Boolean.FALSE);

        int reps = 5;
        long n1Total = 0, bulkTotal = 0;
        List<Individual> sample = null;
        for (int r = 0; r < reps; r++) {
            List<Individual> a = freshList();
            long t0 = System.nanoTime();
            ReflectionTestUtils.invokeMethod(repository, "enrichIndividuals", a, Boolean.FALSE);
            n1Total += (System.nanoTime() - t0);

            List<Individual> b = freshList();
            long t1 = System.nanoTime();
            ReflectionTestUtils.invokeMethod(repository, "enrichIndividualsInBulk", b, Boolean.FALSE);
            bulkTotal += (System.nanoTime() - t1);
            sample = b;
        }

        double n1Ms = n1Total / 1_000_000.0 / reps;
        double bulkMs = bulkTotal / 1_000_000.0 / reps;

        // correctness: bulk enrichment produced the right sub-entity counts per individual
        Individual one = sample.get(0);
        assertEquals(2, one.getAddress().size());
        assertEquals(2, one.getIdentifiers().size());
        assertEquals(1, one.getSkills().size());

        System.out.println("\n================= ENRICHMENT PERF (local, N=" + N + " individuals, " + reps + " reps avg) =================");
        System.out.printf("  OLD  N+1  (enrichIndividuals)       : %.1f ms   (~%d per-record queries: %d x 3)%n", n1Ms, N * 3, N);
        System.out.printf("  NEW  bulk (enrichIndividualsInBulk) : %.1f ms   (3 set-based queries)%n", bulkMs);
        System.out.printf("  speedup: %.1fx   (query round-trips %d -> 3)%n", (bulkMs > 0 ? n1Ms / bulkMs : 0), N * 3);
        System.out.println("=======================================================================================\n");
    }
}
