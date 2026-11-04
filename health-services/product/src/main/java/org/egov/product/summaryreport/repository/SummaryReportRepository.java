package org.egov.product.summaryreport.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.product.summaryreport.config.SummaryReportConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

/**
 * Read-only aggregate queries for the daily summary report. All queries run against the
 * read replica ({@code summaryReportJdbcTemplate}), are scoped to a single employee
 * ({@code createdBy}) + tenant, and are grouped by calendar day (campaign timezone).
 * <p>
 * No joins / locality lookups - these are pure counts, one query per source table.
 * <p>
 * Central-instance compatible: every table is referenced via the {@code {schema}}
 * placeholder and resolved per-tenant with {@link MultiStateInstanceUtil}, exactly like
 * the sibling service repositories.
 */
@Repository
@Slf4j
public class SummaryReportRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SummaryReportConfiguration config;
    private final MultiStateInstanceUtil multiStateInstanceUtil;

    @Autowired
    public SummaryReportRepository(
            @Qualifier("summaryReportJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            SummaryReportConfiguration config,
            MultiStateInstanceUtil multiStateInstanceUtil) {
        this.jdbcTemplate = jdbcTemplate;
        this.config = config;
        this.multiStateInstanceUtil = multiStateInstanceUtil;
    }

    /**
     * Resolves the timezone for the given tenant (per-tenant override, else app default).
     * The value is already validated as a plain IANA zone id by the config, keeping the
     * inlined SQL injection-safe; we re-guard here defensively.
     */
    private String safeTimezone(String tenantId) {
        String tz = config.getTimezoneForTenant(tenantId);
        if (tz == null || !tz.matches("[A-Za-z0-9_/+\\-]{1,64}")) {
            throw new CustomException("INVALID_TIMEZONE",
                    "Resolved timezone is not a valid zone id for tenant " + tenantId + ": " + tz);
        }
        return tz;
    }

    /** SELECT fragment that buckets epoch-millis createdtime into a yyyy-MM-dd day (tenant-local). */
    private String dayExpr(String tenantId) {
        return dayExpr(tenantId, "createdtime");
    }

    /** Same as {@link #dayExpr(String)} but for a specific (possibly table-qualified) column. */
    private String dayExpr(String tenantId, String column) {
        return "to_char(to_timestamp(" + column + " / 1000) AT TIME ZONE '" + safeTimezone(tenantId)
                + "', 'YYYY-MM-DD')";
    }

    /** Resolves the {schema} placeholder for the given tenant (central-instance aware). */
    private String resolveSchema(String query, String tenantId) {
        try {
            return multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        } catch (InvalidTenantIdException e) {
            throw new CustomException("INVALID_TENANT_ID",
                    "Unable to resolve schema for tenantId: " + tenantId);
        }
    }

    private MapSqlParameterSource baseParams(String createdBy, String tenantId,
                                             Long startDate, Long endDate) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("createdBy", createdBy);
        params.addValue("tenantId", tenantId);
        params.addValue("startDate", startDate);
        params.addValue("endDate", endDate);
        return params;
    }

    /** COUNT(*) grouped by day for a simple audited table. Returns day -> count. */
    private Map<String, Long> countByDay(String table, String createdBy, String tenantId,
                                         Long startDate, Long endDate) {
        String sql = "SELECT " + dayExpr(tenantId) + " AS day, COUNT(*) AS cnt "
                + "FROM " + SCHEMA_REPLACE_STRING + "." + table + " "
                + "WHERE createdby = :createdBy "
                + "AND tenantid = :tenantId "
                + "AND (isdeleted = false OR isdeleted IS NULL) "
                + "AND createdtime >= :startDate AND createdtime <= :endDate "
                + "GROUP BY day";
        sql = resolveSchema(sql, tenantId);
        Map<String, Long> result = new HashMap<>();
        jdbcTemplate.query(sql, baseParams(createdBy, tenantId, startDate, endDate),
                (RowCallbackHandler) rs -> result.put(rs.getString("day"), rs.getLong("cnt")));
        return result;
    }

    public Map<String, Long> householdsRegisteredByDay(String createdBy, String tenantId,
                                                        Long startDate, Long endDate) {
        return countByDay("household", createdBy, tenantId, startDate, endDate);
    }

    public Map<String, Long> individualRegisteredByDay(String createdBy, String tenantId,
                                                       Long startDate, Long endDate) {
        return countByDay("individual", createdBy, tenantId, startDate, endDate);
    }

    public Map<String, Long> beneficiariesRegisteredByDay(String createdBy, String tenantId,
                                                          Long startDate, Long endDate) {
        return countByDay("project_beneficiary", createdBy, tenantId, startDate, endDate);
    }

    /**
     * COUNT of PROJECT_TASK rows treated by the employee per day, filtered to the
     * configured "treated" statuses ({@code summary.report.treated.statuses},
     * default ADMINISTRATION_SUCCESS, VISITED).
     */
    public Map<String, Long> childrenTreatedByDay(String createdBy, String tenantId,
                                                  Long startDate, Long endDate) {
        List<String> statuses = config.getTreatedStatuses();
        String sql = "SELECT " + dayExpr(tenantId) + " AS day, COUNT(*) AS cnt "
                + "FROM " + SCHEMA_REPLACE_STRING + ".project_task "
                + "WHERE createdby = :createdBy "
                + "AND tenantid = :tenantId "
                + "AND (isdeleted = false OR isdeleted IS NULL) "
                + "AND status IN (:statuses) "
                + "AND createdtime >= :startDate AND createdtime <= :endDate "
                + "GROUP BY day";
        sql = resolveSchema(sql, tenantId);
        MapSqlParameterSource params = baseParams(createdBy, tenantId, startDate, endDate);
        params.addValue("statuses", statuses);
        Map<String, Long> result = new HashMap<>();
        jdbcTemplate.query(sql, params,
                (RowCallbackHandler) rs -> result.put(rs.getString("day"), rs.getLong("cnt")));
        return result;
    }

    /**
     * SUM(quantity) of delivered task resources per day, broken down by productVariantId.
     * Only resources whose parent PROJECT_TASK is in a configured "treated" status
     * ({@code summary.report.treated.statuses}, default ADMINISTRATION_SUCCESS, VISITED)
     * are counted. Returns day -> (productVariantId -> quantity).
     */
    public Map<String, Map<String, Long>> stockConsumedByDay(String createdBy, String tenantId,
                                                             Long startDate, Long endDate) {
        List<String> statuses = config.getTreatedStatuses();
        String sql = "SELECT " + dayExpr(tenantId, "tr.createdtime") + " AS day, tr.productvariantid AS pv, "
                + "COALESCE(SUM(tr.quantity), 0) AS qty "
                + "FROM " + SCHEMA_REPLACE_STRING + ".task_resource tr "
                + "JOIN " + SCHEMA_REPLACE_STRING + ".project_task pt "
                + "  ON pt.id = tr.taskid AND pt.tenantid = tr.tenantid "
                + "WHERE tr.createdby = :createdBy "
                + "AND tr.tenantid = :tenantId "
                + "AND (tr.isdeleted = false OR tr.isdeleted IS NULL) "
                + "AND tr.isdelivered = true "
                + "AND (pt.isdeleted = false OR pt.isdeleted IS NULL) "
                + "AND pt.status IN (:statuses) "
                + "AND tr.createdtime >= :startDate AND tr.createdtime <= :endDate "
                + "GROUP BY day, tr.productvariantid";
        sql = resolveSchema(sql, tenantId);
        MapSqlParameterSource params = baseParams(createdBy, tenantId, startDate, endDate);
        params.addValue("statuses", statuses);
        Map<String, Map<String, Long>> result = new HashMap<>();
        jdbcTemplate.query(sql, params, (RowCallbackHandler) rs -> {
            String day = rs.getString("day");
            String productVariantId = rs.getString("pv");
            long qty = rs.getLong("qty");
            result.computeIfAbsent(day, k -> new LinkedHashMap<>()).put(productVariantId, qty);
        });
        return result;
    }
}
