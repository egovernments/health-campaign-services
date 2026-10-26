package org.egov.project.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilitySearch;
import org.egov.common.producer.Producer;
import org.egov.project.repository.rowmapper.ProjectFacilityRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.constructTotalCountCTEAndReturnResult;
import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

@Repository
@Slf4j
public class ProjectFacilityRepository extends GenericRepository<ProjectFacility> {
    @Autowired
    public ProjectFacilityRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                     RedisTemplate<String, Object> redisTemplate,
                                     SelectQueryBuilder selectQueryBuilder, ProjectFacilityRowMapper projectFacilityRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder,
                projectFacilityRowMapper, Optional.of("project_facility"));
    }

    // NOT EXISTS fragment that hides links to a post facility (additionalDetails.schema == "Post");
    // %s is the facilityId column reference, which differs by query (aliased vs unaliased).
    // additionalDetails "schema" value that marks a facility as a post
    private static final String POST_FACILITY_SCHEMA_MARKER = "Post";

    private static final String POST_HIDING_NOT_EXISTS_CLAUSE =
            "AND NOT EXISTS (SELECT 1 FROM " + SCHEMA_REPLACE_STRING + ".facility fac " +
                    "WHERE fac.id = %s AND fac.additionalDetails->>'schema' = :postFacilitySchema) ";

    /**
     * Finds facility IDs grouped by boundary type for descendants of the given project.
     * Joins PROJECT, PROJECT_ADDRESS, and PROJECT_FACILITY in a single query
     * filtering by projectHierarchy LIKE and boundaryType IN.
     * Leaves out links to a post facility.
     */
    public Map<String, List<String>> findFacilitiesByDescendants(
            String projectId, List<String> boundaryTypes, String tenantId) throws InvalidTenantIdException {

        String query = "SELECT pf.facilityId, addr.boundaryType " +
                "FROM " + SCHEMA_REPLACE_STRING + ".project_facility pf " +
                "JOIN " + SCHEMA_REPLACE_STRING + ".project prj ON pf.projectId = prj.id " +
                "JOIN " + SCHEMA_REPLACE_STRING + ".project_address addr ON prj.id = addr.projectId " +
                "WHERE prj.projectHierarchy LIKE :hierarchyPattern " +
                "AND LOWER(addr.boundaryType) IN (:boundaryTypes) " +
                "AND pf.isDeleted = false " +
                "AND prj.isDeleted = false " +
                String.format(POST_HIDING_NOT_EXISTS_CLAUSE, "pf.facilityId");

        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);

        List<String> lowerBoundaryTypes = boundaryTypes.stream()
                .map(String::toLowerCase).collect(Collectors.toList());

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("hierarchyPattern", "%" + projectId + ".%");
        params.addValue("boundaryTypes", lowerBoundaryTypes);
        params.addValue("postFacilitySchema", POST_FACILITY_SCHEMA_MARKER);

        return executeFacilityQuery(query, params);
    }

    /**
     * Finds facility IDs grouped by boundary type for the given project and its ancestors.
     * Uses specific project IDs (parsed from projectHierarchy) to query directly.
     * Same post-hiding as {@link #findFacilitiesByDescendants}.
     */
    public Map<String, List<String>> findFacilitiesByAncestors(
            List<String> ancestorProjectIds, List<String> boundaryTypes, String tenantId) throws InvalidTenantIdException {

        String query = "SELECT pf.facilityId, addr.boundaryType " +
                "FROM " + SCHEMA_REPLACE_STRING + ".project_facility pf " +
                "JOIN " + SCHEMA_REPLACE_STRING + ".project prj ON pf.projectId = prj.id " +
                "JOIN " + SCHEMA_REPLACE_STRING + ".project_address addr ON prj.id = addr.projectId " +
                "WHERE prj.id IN (:projectIds) " +
                "AND LOWER(addr.boundaryType) IN (:boundaryTypes) " +
                "AND pf.isDeleted = false " +
                "AND prj.isDeleted = false " +
                String.format(POST_HIDING_NOT_EXISTS_CLAUSE, "pf.facilityId");

        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);

        List<String> lowerBoundaryTypes = boundaryTypes.stream()
                .map(String::toLowerCase).collect(Collectors.toList());

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("projectIds", ancestorProjectIds);
        params.addValue("boundaryTypes", lowerBoundaryTypes);
        params.addValue("postFacilitySchema", POST_FACILITY_SCHEMA_MARKER);

        return executeFacilityQuery(query, params);
    }

    /**
     * Overrides the inherited generic criteria search to leave out links to a post facility,
     * unless the search already asks for a specific facilityId or id, in which case the caller
     * gets exactly what it asked for.
     */
    @Override
    public SearchResponse<ProjectFacility> findWithCount(Object searchObject,
                                                          Integer limit,
                                                          Integer offset,
                                                          String tenantId,
                                                          Long lastChangedSince,
                                                          Boolean includeDeleted) throws QueryBuilderException, InvalidTenantIdException {
        String query = selectQueryBuilder.build(searchObject, tableName, SCHEMA_REPLACE_STRING);
        query += " AND tenantId=:tenantId ";
        if (query.contains(tableName + " AND")) {
            query = query.replace(tableName + " AND", tableName + " WHERE");
        }
        if (Boolean.FALSE.equals(includeDeleted)) {
            query += "AND isDeleted=:isDeleted ";
        }
        if (lastChangedSince != null) {
            query += "AND lastModifiedTime>=:lastModifiedTime ";
        }

        Map<String, Object> paramsMap = selectQueryBuilder.getParamsMap();

        ProjectFacilitySearch search = (ProjectFacilitySearch) searchObject;
        boolean callerAskedForSpecificFacility = !CollectionUtils.isEmpty(search.getFacilityId())
                || !CollectionUtils.isEmpty(search.getId());
        if (!callerAskedForSpecificFacility) {
            query += String.format(POST_HIDING_NOT_EXISTS_CLAUSE, "facilityId");
            paramsMap.put("postFacilitySchema", POST_FACILITY_SCHEMA_MARKER);
        }

        query += "ORDER BY id ASC";
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        Long totalCount = constructTotalCountCTEAndReturnResult(query, paramsMap, namedParameterJdbcTemplate);

        query += " LIMIT :limit OFFSET :offset";
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);

        List<ProjectFacility> resultantList = namedParameterJdbcTemplate.query(query, paramsMap, rowMapper);

        return SearchResponse.<ProjectFacility>builder().response(resultantList).totalCount(totalCount).build();
    }

    private Map<String, List<String>> executeFacilityQuery(String query, MapSqlParameterSource params) {
        Map<String, List<String>> facilityMap = new HashMap<>();
        namedParameterJdbcTemplate.query(query, params, rs -> {
            String facilityId = rs.getString("facilityId");
            String boundaryType = rs.getString("boundaryType");
            facilityMap.computeIfAbsent(boundaryType, k -> new ArrayList<>()).add(facilityId);
        });
        return facilityMap;
    }
}