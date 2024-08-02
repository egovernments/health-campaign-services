package org.egov.project.repository;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.common.models.project.useraction.UserActionSearch;
import org.egov.common.producer.Producer;
import org.egov.common.utils.CommonUtils;
import org.egov.project.repository.rowmapper.LocationCaptureRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.CommonUtils.getIdMethod;

@Repository
@Slf4j
public class LocationCaptureRepository extends GenericRepository<UserAction> {

    @Autowired
    protected LocationCaptureRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate, RedisTemplate<String, Object> redisTemplate, SelectQueryBuilder selectQueryBuilder, LocationCaptureRowMapper locationCaptureRowMapper) {
        // Initialize the repository with producer, JDBC template, Redis template, query builder, and row mapper
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, locationCaptureRowMapper, Optional.of("user_location"));
    }

    /**
     * Finds user locations based on search criteria and URL parameters.
     *
     * @param searchObject The search criteria for user locations.
     * @param urlParams    The URL parameters including pagination and filtering information.
     * @return A SearchResponse containing the list of user locations and the total count.
     */
    public SearchResponse<UserAction> find(UserActionSearch searchObject, URLParams urlParams) {
        log.info("Executing find with searchObject: {} and urlParams: {}", searchObject, urlParams);

        String query = "SELECT id, clientreferenceid, tenantid, projectid, latitude, longitude, locationaccuracy, boundarycode, action, createdby, createdtime, lastmodifiedby, lastmodifiedtime, clientcreatedtime, clientlastmodifiedtime, clientcreatedby, clientlastmodifiedby, additionaldetails FROM user_location ul ";

        Map<String, Object> paramsMap = new HashMap<>();
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject, QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();
        query = query.replace("id IN (:id)", "ul.id IN (:id)");
        query = query.replace("clientReferenceId IN (:clientReferenceId)", "ul.clientReferenceId IN (:clientReferenceId)");

        if (CollectionUtils.isEmpty(whereFields)) {
            query = query + " WHERE ul.tenantId=:tenantId ";
        } else {
            query = query + " AND ul.tenantId=:tenantId ";
        }

        if (urlParams.getLastChangedSince() != null) {
            query = query + " AND ul.lastModifiedTime>=:lastModifiedTime ";
        }
        paramsMap.put("tenantId", urlParams.getTenantId());
        paramsMap.put("lastModifiedTime", urlParams.getLastChangedSince());

        try {
            log.debug("Executing query to fetch total count");
            Long totalCount = CommonUtils.constructTotalCountCTEAndReturnResult(query, paramsMap, this.namedParameterJdbcTemplate);

            query = query + " ORDER BY ul.id ASC LIMIT :limit OFFSET :offset";
            paramsMap.put("limit", urlParams.getLimit());
            paramsMap.put("offset", urlParams.getOffset());

            log.debug("Executing query to fetch user locations: {}", query);
            List<UserAction> locationCaptureList = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);

            log.info("Successfully fetched user locations: {}", locationCaptureList.size());
            return SearchResponse.<UserAction>builder().response(locationCaptureList).totalCount(totalCount).build();
        } catch (Exception e) {
            log.error("Failed to execute query for finding user locations", e);
            return SearchResponse.<UserAction>builder().response(Collections.emptyList()).totalCount(0L).build();
        }
    }

    /**
     * Finds user locations by their IDs, first checking the cache before querying the database.
     *
     * @param ids         The list of IDs to search for.
     * @param columnName  The name of the column to search by.
     * @return A SearchResponse containing the list of user locations found.
     */
    public SearchResponse<UserAction> findById(List<String> ids, String columnName) {
        log.info("Executing findById with ids: {} and columnName: {}", ids, columnName);

        List<UserAction> objFound = findInCache(ids);

        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, columnName);
            ids.removeAll(objFound.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                    .collect(Collectors.toList()));

            if (ids.isEmpty()) {
                log.info("All requested user locations found in cache");
                return SearchResponse.<UserAction>builder().response(objFound).build();
            }
        }

        String query = String.format("SELECT id, clientreferenceid, tenantid, projectid, latitude, longitude, locationaccuracy, boundarycode, action, createdby, createdtime, lastmodifiedby, lastmodifiedtime, clientcreatedtime, clientlastmodifiedtime, clientcreatedby, clientlastmodifiedby, additionaldetails FROM user_location ul WHERE ul.%s IN (:ids)", columnName);
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);

        try {
            log.debug("Executing query to fetch user locations by ID: {}", query);
            List<UserAction> locationCaptureList = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);

            objFound.addAll(locationCaptureList);
            putInCache(objFound);

            log.info("Successfully fetched user locations by ID: {}", locationCaptureList.size());
            return SearchResponse.<UserAction>builder().response(objFound).build();
        } catch (Exception e) {
            log.error("Failed to execute query for finding user locations by ID", e);
            return SearchResponse.<UserAction>builder().response(Collections.emptyList()).build();
        }
    }
}
