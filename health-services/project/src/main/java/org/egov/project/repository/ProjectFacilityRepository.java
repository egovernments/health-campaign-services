package org.egov.project.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.producer.Producer;
import org.egov.project.repository.rowmapper.ProjectFacilityRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    /**
     * Finds facility IDs grouped by boundary type for descendants of the given project.
     * Joins PROJECT, PROJECT_ADDRESS, and PROJECT_FACILITY in a single query
     * filtering by projectHierarchy LIKE and boundaryType IN.
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
                "AND prj.isDeleted = false";

        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);

        List<String> lowerBoundaryTypes = boundaryTypes.stream()
                .map(String::toLowerCase).collect(Collectors.toList());

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("hierarchyPattern", "%" + projectId + ".%");
        params.addValue("boundaryTypes", lowerBoundaryTypes);

        return executeFacilityQuery(query, params);
    }

    /**
     * Finds facility IDs grouped by boundary type for the given project and its ancestors.
     * Uses specific project IDs (parsed from projectHierarchy) to query directly.
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
                "AND prj.isDeleted = false";

        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);

        List<String> lowerBoundaryTypes = boundaryTypes.stream()
                .map(String::toLowerCase).collect(Collectors.toList());

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("projectIds", ancestorProjectIds);
        params.addValue("boundaryTypes", lowerBoundaryTypes);

        return executeFacilityQuery(query, params);
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