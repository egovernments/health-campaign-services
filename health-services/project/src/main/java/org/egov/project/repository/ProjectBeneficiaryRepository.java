package org.egov.project.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.producer.Producer;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.project.repository.rowmapper.ProjectBeneficiaryRowMapper;
import org.egov.common.models.project.ProjectBeneficiarySearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.constructTotalCountCTEAndReturnResult;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;
import static org.egov.project.Constants.REGISTERED_BY_TEAM;

@Repository
@Slf4j
public class ProjectBeneficiaryRepository extends GenericRepository<ProjectBeneficiary> {

    @Autowired
    public ProjectBeneficiaryRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                           RedisTemplate<String, Object> redisTemplate,
                                           SelectQueryBuilder selectQueryBuilder, ProjectBeneficiaryRowMapper projectBeneficiaryRowMapper,
                                           MultiStateInstanceUtil multiStateInstanceUtil) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder,
                projectBeneficiaryRowMapper, Optional.of("project_beneficiary"));
    }

    /**
     * Finds and retrieves a paginated list of ProjectBeneficiaries based on the given search criteria.
     *
     * @param householdMemberSearch the search criteria object containing filtering parameters such as project ID,
     *                              beneficiary ID, date of registration, and tags.
     * @param limit the maximum number of results to return.
     * @param offset the starting point in the result set to retrieve data.
     * @param tenantId the tenant ID for filtering the search within a particular tenant's context.
     * @param lastChangedSince the timestamp to filter results modified since the specified time.
     * @param includeDeleted a flag indicating whether to include deleted records in the search results.
     * @return a SearchResponse containing a list of ProjectBeneficiaries and the total count of matched records.
     * @throws InvalidTenantIdException if an invalid tenant ID is provided.
     */
    public SearchResponse<ProjectBeneficiary> find(ProjectBeneficiarySearch householdMemberSearch,
                                                Integer limit,
                                                Integer offset,
                                                String tenantId,
                                                Long lastChangedSince,
                                                Boolean includeDeleted) throws InvalidTenantIdException {

        Map<String, Object> paramsMap = new HashMap<>();
        StringBuilder queryBuilder = new StringBuilder();

        String query = String.format("SELECT * FROM %s.project_beneficiary ", SCHEMA_REPLACE_STRING);

        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(householdMemberSearch, QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString().trim();

        query = query + " AND tenantId=:tenantId ";

        if (query.contains(this.tableName + " AND")) {
            query = query.replace(this.tableName + " AND", this.tableName + " WHERE");
        }

        queryBuilder.append(query);

        if (Boolean.FALSE.equals(includeDeleted)) {
            queryBuilder.append("AND isDeleted=:isDeleted ");
        }

        if (lastChangedSince != null) {
            queryBuilder.append("AND lastModifiedTime>=:lastModifiedTime ");
        }

        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);

        queryBuilder.append(" ORDER BY id ASC ");
        // Replacing schema placeholder with the schema name for the tenant id
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(queryBuilder.toString(), tenantId);
        Long totalCount = constructTotalCountCTEAndReturnResult(query, paramsMap, this.namedParameterJdbcTemplate);

        query += " LIMIT :limit OFFSET :offset";
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);

        List<ProjectBeneficiary> projectBeneficiaries = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);

        return SearchResponse.<ProjectBeneficiary>builder().totalCount(totalCount).response(projectBeneficiaries).build();
    }

    /**
     * Loads beneficiaries by clientReferenceId for the team assignment API. Hand rolled rather
     * than going through find(), because the caller wants exactly these rows with no total-count
     * CTE, and because it needs to page a list far larger than one request should build a
     * reflective search model for.
     *
     * @param projectId optional narrowing; null to match across every project in the tenant
     */
    public List<ProjectBeneficiary> findByClientReferenceIds(String tenantId, List<String> clientReferenceIds,
                                                             String projectId) throws InvalidTenantIdException {
        if (clientReferenceIds == null || clientReferenceIds.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = String.format("SELECT * FROM %s.project_beneficiary "
                + "WHERE tenantId = :tenantId AND isDeleted = false "
                + "AND clientReferenceId IN (:clientReferenceIds) ", SCHEMA_REPLACE_STRING)
                + (projectId != null ? "AND projectId = :projectId " : "")
                + "ORDER BY id ASC";

        Map<String, Object> paramsMap = new HashMap<>();
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("clientReferenceIds", clientReferenceIds);
        if (projectId != null) {
            paramsMap.put("projectId", projectId);
        }
        return this.namedParameterJdbcTemplate.query(
                multiStateInstanceUtil.replaceSchemaPlaceholder(sql, tenantId), paramsMap, this.rowMapper);
    }

    /**
     * One page of the ids currently attributed to a team code, read through the GIN index on
     * additionalDetails.
     *
     * Ids only, and offset paged rather than keyset paged. A GIN index has no ordering, so it
     * cannot satisfy a keyset cursor - measured, a keyset page over this predicate re-reads the
     * whole team every time (69 ms/page on a 1000 member team, 763 ms when the planner falls back
     * to a primary key scan), which makes a full page-through quadratic. Offset paging is safe
     * here only because the caller uses it in a read-only phase that completes before the first
     * write; see BeneficiaryTeamService.relocateTeamCode.
     *
     * @param projectId optional narrowing; null to match across every project in the tenant
     */
    public List<String> findIdsByRegisteredByTeam(String tenantId, String teamCode, String projectId,
                                                  Integer limit, Integer offset)
            throws InvalidTenantIdException {
        String sql = String.format("SELECT id FROM %s.project_beneficiary "
                + "WHERE tenantId = :tenantId AND isDeleted = false "
                // key and value are both bound and the document is assembled by postgres, so a team
                // code containing a quote cannot corrupt the json the way String.format would.
                // Verified: jsonb_build_object still lets the planner use the GIN index.
                + "AND additionaldetails -> 'fields' @> "
                + "jsonb_build_array(jsonb_build_object('key', :teamKey, 'value', :teamCode)) ",
                SCHEMA_REPLACE_STRING)
                + (projectId != null ? "AND projectId = :projectId " : "")
                + "ORDER BY id ASC LIMIT :limit OFFSET :offset";

        Map<String, Object> paramsMap = new HashMap<>();
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("teamKey", REGISTERED_BY_TEAM);
        paramsMap.put("teamCode", teamCode);
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);
        if (projectId != null) {
            paramsMap.put("projectId", projectId);
        }
        return this.namedParameterJdbcTemplate.queryForList(
                multiStateInstanceUtil.replaceSchemaPlaceholder(sql, tenantId), paramsMap, String.class);
    }

    /**
     * Loads beneficiaries by id for the relocate write phase.
     *
     * Deliberately not findById(): that one consults the Redis cache, which can hand back a
     * beneficiary whose additionalFields predate an in-flight update, and the previousTeamCode
     * recorded in the history has to reflect what is actually stored. It also mutates the id list
     * it is given.
     */
    public List<ProjectBeneficiary> findByIds(String tenantId, List<String> ids) throws InvalidTenantIdException {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = String.format("SELECT * FROM %s.project_beneficiary "
                + "WHERE tenantId = :tenantId AND isDeleted = false AND id IN (:ids) "
                + "ORDER BY id ASC", SCHEMA_REPLACE_STRING);
        Map<String, Object> paramsMap = new HashMap<>();
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("ids", ids);
        return this.namedParameterJdbcTemplate.query(
                multiStateInstanceUtil.replaceSchemaPlaceholder(sql, tenantId), paramsMap, this.rowMapper);
    }

    /**
     * Which of these clientReferenceIds actually exist, as a one column projection.
     *
     * Used to make the team assignment all-or-nothing without ever holding beneficiary rows: the
     * result is a set of strings the size of the input, where loading the rows themselves would
     * cost orders of magnitude more per record once the additionalDetails jsonb is parsed.
     *
     * @param projectId optional narrowing; a beneficiary outside that project counts as missing
     */
    public Set<String> findExistingClientReferenceIds(String tenantId, List<String> clientReferenceIds,
                                                      String projectId) throws InvalidTenantIdException {
        if (clientReferenceIds == null || clientReferenceIds.isEmpty()) {
            return Collections.emptySet();
        }
        String sql = String.format("SELECT clientReferenceId FROM %s.project_beneficiary "
                + "WHERE tenantId = :tenantId AND isDeleted = false "
                + "AND clientReferenceId IN (:clientReferenceIds) ", SCHEMA_REPLACE_STRING)
                + (projectId != null ? "AND projectId = :projectId " : "");

        Map<String, Object> paramsMap = new HashMap<>();
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("clientReferenceIds", clientReferenceIds);
        if (projectId != null) {
            paramsMap.put("projectId", projectId);
        }
        return new HashSet<>(this.namedParameterJdbcTemplate.queryForList(
                multiStateInstanceUtil.replaceSchemaPlaceholder(sql, tenantId), paramsMap, String.class));
    }

    /**
     * One keyset page of the beneficiaries a set of users registered, for the team assignment API.
     * createdBy holds the egov-user uuid, and ProjectBeneficiarySearch has no field for it, so the
     * reflective query builder cannot express this at all.
     *
     * Keyset rather than offset paging: a user's registrations can run to tens of thousands, and
     * offset paging both degrades on every page and can skip or repeat rows when records are
     * inserted mid-run. Paging while writing is safe here because the predicate is createdBy,
     * which this operation never modifies - only additionalDetails changes.
     *
     * @param afterId  the last id of the previous page, or null for the first page
     * @param projectId optional narrowing; null to match across every project in the tenant
     */
    public List<ProjectBeneficiary> findPageByCreatedBy(String tenantId, List<String> userUuids, String projectId,
                                                        String afterId, Integer limit)
            throws InvalidTenantIdException {
        if (userUuids == null || userUuids.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = String.format("SELECT * FROM %s.project_beneficiary "
                + "WHERE tenantId = :tenantId AND isDeleted = false "
                + "AND createdBy IN (:userUuids) ", SCHEMA_REPLACE_STRING)
                + (projectId != null ? "AND projectId = :projectId " : "")
                + (afterId != null ? "AND id > :afterId " : "")
                + "ORDER BY id ASC LIMIT :limit";

        Map<String, Object> paramsMap = new HashMap<>();
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("userUuids", userUuids);
        paramsMap.put("limit", limit);
        if (projectId != null) {
            paramsMap.put("projectId", projectId);
        }
        if (afterId != null) {
            paramsMap.put("afterId", afterId);
        }
        return this.namedParameterJdbcTemplate.query(
                multiStateInstanceUtil.replaceSchemaPlaceholder(sql, tenantId), paramsMap, this.rowMapper);
    }

    /**
     * Finds and retrieves a list of ProjectBeneficiary records based on the provided identifiers.
     * The method allows filtering of deleted records and also leverages caching for faster retrieval.
     * If not all records are found in the cache, it queries the database for the missing records.
     *
     * @param tenantId The tenant identifier used to retrieve data within a specific tenant's context.
     * @param ids A list of unique identifiers to search for ProjectBeneficiary records.
     * @param columnName The name of the column used for matching the provided identifiers.
     * @param includeDeleted A flag indicating whether deleted ProjectBeneficiary records should be included in the result.
     * @return A SearchResponse containing the retrieved ProjectBeneficiary records.
     * @throws InvalidTenantIdException If the provided tenant ID is invalid or does not exist.
     */
    public SearchResponse<ProjectBeneficiary> findById(String tenantId, List<String> ids, String columnName, Boolean includeDeleted) throws InvalidTenantIdException {
        List<ProjectBeneficiary> objFound = findInCache(tenantId, ids);
        if (!includeDeleted) {
            objFound = objFound.stream()
                    .filter(entity -> entity.getIsDeleted().equals(false))
                    .collect(Collectors.toList());
        }
        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, columnName);
            ids.removeAll(objFound.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                    .collect(Collectors.toList()));
            if (ids.isEmpty()) {
                log.info("all objects were found in the cache, returning objects");
                return SearchResponse.<ProjectBeneficiary>builder().response(objFound).build();
            }
        }

        String query = String.format("SELECT * FROM %s.project_beneficiary where %s IN (:ids) AND isDeleted = false", SCHEMA_REPLACE_STRING, columnName);
        if (null != includeDeleted && includeDeleted) {
            query = String.format("SELECT * FROM %s.project_beneficiary WHERE %s IN (:ids)", SCHEMA_REPLACE_STRING, columnName);
        }
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("ids", ids);
        // Replacing schema placeholder with the schema name for the tenant id
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);

        objFound.addAll(this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper));
        putInCache(objFound);
        log.info("returning objects from the database");
        return SearchResponse.<ProjectBeneficiary>builder().response(objFound).build();
    }
}

