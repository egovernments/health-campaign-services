package org.egov.individual.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.individual.repository.rowmapper.TeamCodeMappingRowMapper;
import org.egov.individual.repository.rowmapper.TeamCodeRowMapper;
import org.egov.individual.web.models.TeamCode;
import org.egov.individual.web.models.TeamCodeMapping;
import org.egov.individual.web.models.TeamCodeSearch;
import org.egov.individual.web.models.TeamCodeStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

/**
 * Read-only access to individual_team_code and individual_team_code_mapping.
 *
 * Nothing here writes. Both tables are populated by egov-persister off
 * save-individual-team-code-topic, save-individual-team-code-mapping-topic and
 * update-individual-team-code-mapping-topic, so a row appears only once the consumer has caught
 * up - mint team codes, confirm they landed, then assign.
 *
 * status and assignedCount are no longer stored on individual_team_code. They are derived here
 * from the live membership rows, because a counter maintained by reading an asynchronously
 * written table cannot be kept correct. The API response shape is unchanged.
 */
@Repository
@Slf4j
public class TeamCodeRepository {

    /**
     * Counts the individuals currently on a code, for the assignedCount the response carries.
     * Only ever evaluated for the rows actually returned, never as a filter - see HAS_LIVE_MEMBER
     * for that.
     */
    /**
     * Existence test for the status filter. EXISTS short circuits on the first live member, where
     * LIVE_COUNT has to count them all - measured 21 ms against 65 ms for the UNASSIGNED filter
     * over 5000 codes, because that one evaluates the subquery for every candidate row.
     */
    private static final String HAS_LIVE_MEMBER = "EXISTS (SELECT 1 FROM " + SCHEMA_REPLACE_STRING
            + ".individual_team_code_mapping m WHERE m.tenantId = tc.tenantId "
            + "AND m.teamCode = tc.teamCode AND m.status = 'ASSIGNED' AND m.isDeleted = false)";

    private static final String LIVE_COUNT = "(SELECT COUNT(*) FROM " + SCHEMA_REPLACE_STRING
            + ".individual_team_code_mapping m WHERE m.tenantId = tc.tenantId "
            + "AND m.teamCode = tc.teamCode AND m.status = 'ASSIGNED' AND m.isDeleted = false)";

    private static final String FIND_BY_TEAM_CODES_QUERY = String.format(
            "SELECT tc.*, %s AS assignedCount FROM %s.individual_team_code tc "
            + "WHERE tc.tenantId = :tenantId AND tc.teamCode IN (:teamCodes) AND tc.isDeleted = false",
            LIVE_COUNT, SCHEMA_REPLACE_STRING);

    private static final String FIND_MAPPINGS_QUERY = String.format(
            "SELECT * FROM %s.individual_team_code_mapping "
            + "WHERE tenantId = :tenantId AND teamCode IN (:teamCodes) AND isDeleted = false",
            SCHEMA_REPLACE_STRING);

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final TeamCodeRowMapper teamCodeRowMapper;

    private final TeamCodeMappingRowMapper teamCodeMappingRowMapper;

    private final MultiStateInstanceUtil multiStateInstanceUtil;

    public TeamCodeRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                              TeamCodeRowMapper teamCodeRowMapper,
                              TeamCodeMappingRowMapper teamCodeMappingRowMapper,
                              MultiStateInstanceUtil multiStateInstanceUtil) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.teamCodeRowMapper = teamCodeRowMapper;
        this.teamCodeMappingRowMapper = teamCodeMappingRowMapper;
        this.multiStateInstanceUtil = multiStateInstanceUtil;
    }

    public List<TeamCode> findByTeamCodes(String tenantId, List<String> teamCodes) throws InvalidTenantIdException {
        if (CollectionUtils.isEmpty(teamCodes)) {
            return Collections.emptyList();
        }
        String query = multiStateInstanceUtil.replaceSchemaPlaceholder(FIND_BY_TEAM_CODES_QUERY, tenantId);
        MapSqlParameterSource paramSource = new MapSqlParameterSource();
        paramSource.addValue("tenantId", tenantId);
        paramSource.addValue("teamCodes", teamCodes);
        return namedParameterJdbcTemplate.query(query, paramSource, teamCodeRowMapper);
    }

    /**
     * Pages team codes matching the criteria, with the total count of matches ignoring limit and
     * offset.
     */
    public SearchResponse<TeamCode> search(TeamCodeSearch criteria, Integer limit, Integer offset, String tenantId,
                                           Boolean includeDeleted) throws InvalidTenantIdException {
        MapSqlParameterSource paramSource = new MapSqlParameterSource();
        String where = buildSearchPredicate(criteria, tenantId, includeDeleted, paramSource);

        String countQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(
                String.format("SELECT COUNT(*) FROM %s.individual_team_code tc %s", SCHEMA_REPLACE_STRING, where),
                tenantId);
        Long totalCount = namedParameterJdbcTemplate.queryForObject(countQuery, paramSource, Long.class);

        String query = multiStateInstanceUtil.replaceSchemaPlaceholder(
                String.format("SELECT tc.*, %s AS assignedCount FROM %s.individual_team_code tc %s "
                        + "ORDER BY tc.teamCode ASC LIMIT :limit OFFSET :offset",
                        LIVE_COUNT, SCHEMA_REPLACE_STRING, where),
                tenantId);
        paramSource.addValue("limit", limit);
        paramSource.addValue("offset", offset);
        List<TeamCode> teamCodes = namedParameterJdbcTemplate.query(query, paramSource, teamCodeRowMapper);

        return SearchResponse.<TeamCode>builder()
                .totalCount(totalCount == null ? 0L : totalCount)
                .response(teamCodes)
                .build();
    }

    /**
     * @param onlyAssigned true to return the current members only, false to include the
     *                     memberships that have since been unassigned
     */
    public List<TeamCodeMapping> findMappingsByTeamCodes(String tenantId, List<String> teamCodes,
                                                         boolean onlyAssigned) throws InvalidTenantIdException {
        if (CollectionUtils.isEmpty(teamCodes)) {
            return Collections.emptyList();
        }
        String sql = FIND_MAPPINGS_QUERY + (onlyAssigned ? " AND status = 'ASSIGNED'" : "")
                + " ORDER BY teamCode ASC, assignedTime ASC";
        String query = multiStateInstanceUtil.replaceSchemaPlaceholder(sql, tenantId);
        MapSqlParameterSource paramSource = new MapSqlParameterSource();
        paramSource.addValue("tenantId", tenantId);
        paramSource.addValue("teamCodes", teamCodes);
        return namedParameterJdbcTemplate.query(query, paramSource, teamCodeMappingRowMapper);
    }

    /**
     * status is derived, so filtering on it becomes an existence test against the live membership
     * rows rather than a column comparison. INACTIVE means the code has been retired, which is
     * carried by isDeleted.
     */
    private String buildSearchPredicate(TeamCodeSearch criteria, String tenantId, Boolean includeDeleted,
                                        MapSqlParameterSource paramSource) {
        List<String> predicates = new ArrayList<>();
        predicates.add("tc.tenantId = :tenantId");
        paramSource.addValue("tenantId", tenantId);

        if (!Boolean.TRUE.equals(includeDeleted)) {
            predicates.add("tc.isDeleted = false");
        }
        if (criteria != null && !CollectionUtils.isEmpty(criteria.getId())) {
            predicates.add("tc.id IN (:id)");
            paramSource.addValue("id", criteria.getId());
        }
        if (criteria != null && !CollectionUtils.isEmpty(criteria.getTeamCode())) {
            predicates.add("tc.teamCode IN (:teamCode)");
            paramSource.addValue("teamCode", criteria.getTeamCode());
        }
        if (criteria != null && !CollectionUtils.isEmpty(criteria.getStatus())) {
            predicates.add(derivedStatusPredicate(criteria.getStatus()));
        }
        if (criteria != null && !CollectionUtils.isEmpty(criteria.getIndividualId())) {
            predicates.add("EXISTS (SELECT 1 FROM " + SCHEMA_REPLACE_STRING + ".individual_team_code_mapping m "
                    + "WHERE m.tenantId = tc.tenantId AND m.teamCode = tc.teamCode "
                    + "AND m.individualId IN (:individualId) AND m.status = 'ASSIGNED' AND m.isDeleted = false)");
            paramSource.addValue("individualId", criteria.getIndividualId());
        }
        return "WHERE " + String.join(" AND ", predicates);
    }

    private String derivedStatusPredicate(List<TeamCodeStatus> statuses) {
        List<String> clauses = new ArrayList<>();
        if (statuses.contains(TeamCodeStatus.ASSIGNED)) {
            clauses.add("(" + HAS_LIVE_MEMBER + " AND tc.isDeleted = false)");
        }
        if (statuses.contains(TeamCodeStatus.UNASSIGNED)) {
            clauses.add("(NOT " + HAS_LIVE_MEMBER + " AND tc.isDeleted = false)");
        }
        if (statuses.contains(TeamCodeStatus.INACTIVE)) {
            clauses.add("tc.isDeleted = true");
        }
        return "(" + String.join(" OR ", clauses) + ")";
    }
}
