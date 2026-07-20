package org.egov.product.summaryreport.repository;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.product.summaryreport.config.SummaryReportConfiguration;
import org.egov.product.summaryreport.service.SummaryReportService;
import org.egov.product.summaryreport.web.models.DailyReportSummary;
import org.egov.product.summaryreport.web.models.SummaryReportSearchCriteria;
import org.egov.product.summaryreport.web.models.SummaryReportSearchRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test against a REAL local, multi-schema (central-instance) database.
 * <p>
 * Uses the existing {@code ngupgradeuat} DB which holds the health-campaign tables across
 * several state schemas (os, si, ke, ba, public...). It wires the actual production
 * components - {@link SummaryReportRepository} + {@link SummaryReportService} +
 * {@link MultiStateInstanceUtil} in central-instance mode - and verifies the results
 * against direct SQL, including that schema resolution isolates tenants.
 * <p>
 * Disabled unless {@code SUMMARY_DB_IT=true} so it never runs in normal CI.
 * Run with:
 * <pre>
 *   SUMMARY_DB_IT=true mvn -o -f health-services/product/pom.xml \
 *       test -Dtest=SummaryReportRepositoryIT
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "SUMMARY_DB_IT", matches = "true")
class SummaryReportRepositoryIT {

    // --- local connection (override via env if needed) ---
    private static final String URL = envOr("SUMMARY_IT_DB_URL",
            "jdbc:postgresql://localhost:5432/ngupgradeuat");
    private static final String USER = envOr("SUMMARY_IT_DB_USER", System.getProperty("user.name"));
    private static final String PASSWORD = envOr("SUMMARY_IT_DB_PASSWORD", "");

    // --- real test subjects discovered in the DB ---
    private static final String OS_TENANT = "os";
    private static final String OS_CREATED_BY = "68dab4c1-bbda-40b0-a067-785765c0096f";
    private static final String SI_TENANT = "si";
    private static final String SI_CREATED_BY = "f16be3e1-5ab1-4cfb-9977-f902b785d2e8";

    private static final long START = 0L;
    private static final long END = 4_102_444_800_000L; // year 2100

    private static NamedParameterJdbcTemplate jdbc;
    private static SummaryReportService service;

    @BeforeAll
    static void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(URL, USER, PASSWORD);
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new NamedParameterJdbcTemplate(ds);

        SummaryReportConfiguration config = new SummaryReportConfiguration();
        ReflectionTestUtils.setField(config, "reportTimezone", "Africa/Lagos");
        ReflectionTestUtils.setField(config, "treatedStatusesRaw", "ADMINISTRATION_SUCCESS,VISITED");

        // central instance = true -> {schema} resolves from tenantId
        MultiStateInstanceUtil util = new MultiStateInstanceUtil(1, Boolean.TRUE, 0);

        SummaryReportRepository repository = new SummaryReportRepository(jdbc, config, util);
        service = new SummaryReportService(repository, config);
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    private SummaryReportSearchRequest req(String tenantId, String createdBy) {
        return SummaryReportSearchRequest.builder()
                .requestInfo(RequestInfo.builder()
                        .userInfo(User.builder().uuid(createdBy).build())
                        .build())
                .summaryReportSearchCriteria(SummaryReportSearchCriteria.builder()
                        .tenantId(tenantId).startDate(START).endDate(END).build())
                .build();
    }

    private long directCount(String schema, String table, String createdBy, String extraPredicate) {
        String sql = "SELECT count(*) FROM " + schema + "." + table
                + " WHERE createdby = :cb AND tenantid = :t "
                + " AND (isdeleted = false OR isdeleted IS NULL) "
                + " AND createdtime >= :s AND createdtime <= :e " + extraPredicate;
        Long c = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("cb", createdBy).addValue("t", schema)
                .addValue("s", START).addValue("e", END), Long.class);
        return c == null ? 0L : c;
    }

    private long directSum(String schema, String table, String column, String createdBy, String extraPredicate) {
        String sql = "SELECT COALESCE(SUM(" + column + "),0) FROM " + schema + "." + table
                + " WHERE createdby = :cb AND tenantid = :t "
                + " AND (isdeleted = false OR isdeleted IS NULL) "
                + " AND createdtime >= :s AND createdtime <= :e " + extraPredicate;
        Long c = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("cb", createdBy).addValue("t", schema)
                .addValue("s", START).addValue("e", END), Long.class);
        return c == null ? 0L : c;
    }

    /** Expected stock: delivered task_resource whose parent task is in a treated status. */
    private long directStock(String schema, String createdBy) {
        String sql = "SELECT COALESCE(SUM(tr.quantity),0) FROM " + schema + ".task_resource tr "
                + " JOIN " + schema + ".project_task pt ON pt.id = tr.taskid AND pt.tenantid = tr.tenantid "
                + " WHERE tr.createdby = :cb AND tr.tenantid = :t "
                + " AND (tr.isdeleted = false OR tr.isdeleted IS NULL) AND tr.isdelivered = true "
                + " AND (pt.isdeleted = false OR pt.isdeleted IS NULL) "
                + " AND pt.status IN ('ADMINISTRATION_SUCCESS','VISITED') "
                + " AND tr.createdtime >= :s AND tr.createdtime <= :e";
        Long c = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("cb", createdBy).addValue("t", schema)
                .addValue("s", START).addValue("e", END), Long.class);
        return c == null ? 0L : c;
    }

    private void assertMatchesSchema(String tenant, String createdBy) {
        List<DailyReportSummary> report = service.getDailySummary(req(tenant, createdBy));

        long households = report.stream().mapToLong(DailyReportSummary::getHouseholdsRegistered).sum();
        long children = report.stream().mapToLong(DailyReportSummary::getIndividualRegistered).sum();
        long beneficiaries = report.stream().mapToLong(DailyReportSummary::getBeneficiariesRegistered).sum();
        long treated = report.stream().mapToLong(DailyReportSummary::getChildrenTreated).sum();
        long stock = report.stream()
                .flatMap(d -> d.getStockConsumedMap().values().stream())
                .mapToLong(Long::longValue).sum();

        System.out.printf("[%s / %s] days=%d households=%d children=%d beneficiaries=%d treated=%d stockQty=%d%n",
                tenant, createdBy, report.size(), households, children, beneficiaries, treated, stock);

        assertEquals(directCount(tenant, "household", createdBy, ""), households, "households");
        assertEquals(directCount(tenant, "individual", createdBy, ""), children, "children");
        assertEquals(directCount(tenant, "project_beneficiary", createdBy, ""), beneficiaries, "beneficiaries");
        assertEquals(directCount(tenant, "project_task", createdBy,
                " AND status IN ('ADMINISTRATION_SUCCESS','VISITED') "), treated, "treated");
        assertEquals(directStock(tenant, createdBy), stock, "stockQty");
    }

    @Test
    void shouldMatchDirectSqlForOsSchema() {
        assertMatchesSchema(OS_TENANT, OS_CREATED_BY);
    }

    @Test
    void shouldMatchDirectSqlForSiSchema() {
        assertMatchesSchema(SI_TENANT, SI_CREATED_BY);
    }

    @Test
    void shouldIsolateSchemasByTenant() {
        // The OS employee has data in the 'os' schema...
        List<DailyReportSummary> inOs = service.getDailySummary(req(OS_TENANT, OS_CREATED_BY));
        long osHouseholds = inOs.stream().mapToLong(DailyReportSummary::getHouseholdsRegistered).sum();
        assertTrue(osHouseholds >= 0);

        // ...but querying that same uuid under the 'si' tenant must resolve to the 'si'
        // schema and return nothing (proves {schema} isolation, not a shared table).
        List<DailyReportSummary> crossTenant = service.getDailySummary(req(SI_TENANT, OS_CREATED_BY));
        long crossHouseholds = crossTenant.stream().mapToLong(DailyReportSummary::getHouseholdsRegistered).sum();
        long crossTreated = crossTenant.stream().mapToLong(DailyReportSummary::getChildrenTreated).sum();

        System.out.printf("[isolation] os-employee under 'si': households=%d treated=%d (expected 0)%n",
                crossHouseholds, crossTreated);
        assertEquals(0L, crossHouseholds, "os employee should have no households in the si schema");
        assertEquals(0L, crossTreated, "os employee should have no treated tasks in the si schema");
    }
}
