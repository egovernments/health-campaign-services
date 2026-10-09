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
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.common.models.project.useraction.UserActionSearch;
import org.egov.common.producer.Producer;
import org.egov.common.utils.CommonUtils;
import org.egov.project.repository.rowmapper.UserActionRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

@Repository
@Slf4j
public class UserActionRepository extends GenericRepository<UserAction> {

    private final String selectQuery =
            "SELECT id, clientreferenceid, tenantid, projectid, latitude, longitude, locationaccuracy, boundarycode, action, beneficiarytag, resourcetag, status, additionaldetails, createdby, createdtime, lastmodifiedby, lastmodifiedtime, clientcreatedtime, clientlastmodifiedtime, clientcreatedby, clientlastmodifiedby, rowversion FROM " + SCHEMA_REPLACE_STRING +".user_action ua";
    @Autowired
    protected UserActionRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                   RedisTemplate<String, Object> redisTemplate, SelectQueryBuilder selectQueryBuilder,
                                   UserActionRowMapper rowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, rowMapper, Optional.of("user_action"));
    }

    /**
     * Finds user actions based on search criteria and URL parameters.
     *
     * @param searchObject The search criteria for user actions.
     * @param urlParams    The URL parameters including pagination and filtering information.
     * @return A SearchResponse containing the list of user actions and the total count.
     * @throws QueryBuilderException If there is an error building the query.
     */
    public SearchResponse<UserAction> find(UserActionSearch searchObject, URLParams urlParams) throws QueryBuilderException {
        log.info("Executing find with searchObject: {} and urlParams: {}", searchObject, urlParams);

        String query = ""+selectQuery;

        Map<String, Object> paramsMap = new HashMap<>();
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject, QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();
        query = query.replace("id IN (:id)", "ua.id IN (:id)");
        query = query.replace("clientReferenceId IN (:clientReferenceId)", "ua.clientReferenceId IN (:clientReferenceId)");

        if (CollectionUtils.isEmpty(whereFields)) {
            query = query + " WHERE ua.tenantId=:tenantId ";
        } else {
            query = query + " AND ua.tenantId=:tenantId ";
        }

        if (urlParams.getLastChangedSince() != null) {
            query = query + " AND lastModifiedTime>=:lastModifiedTime ";
        }
        paramsMap.put("tenantId", urlParams.getTenantId());
        paramsMap.put("isDeleted", urlParams.getIncludeDeleted());
        paramsMap.put("lastModifiedTime", urlParams.getLastChangedSince());

        try {
            log.debug("Executing query to fetch total count");
            // Replacing schema placeholder with the schema name for the tenant id
            query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, urlParams.getTenantId());
            Long totalCount = CommonUtils.constructTotalCountCTEAndReturnResult(query, paramsMap, this.namedParameterJdbcTemplate);

            query = query + " ORDER BY ua.id ASC LIMIT :limit OFFSET :offset";
            paramsMap.put("limit", urlParams.getLimit());
            paramsMap.put("offset", urlParams.getOffset());

            log.debug("Executing query to fetch user actions: {}", query);
            List<UserAction> userActionList = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);

            log.info("Successfully fetched user actions: {}", userActionList.size());
            return SearchResponse.<UserAction>builder().response(userActionList).totalCount(totalCount).build();
        } catch (Exception e) {
            log.error("Failed to execute query for finding user actions", e);
            return SearchResponse.<UserAction>builder().response(Collections.emptyList()).totalCount(0L).build();
        }
    }

    /**
     * Finds user actions by their IDs, first checking the cache before querying the database.
     *
     * @param ids         The list of IDs to search for.
     * @param tenantId    The identifier for the tenant
     * @param columnName  The name of the column to search by.
     * @return A SearchResponse containing the list of user actions found.
     */
    public SearchResponse<UserAction> findById(String tenantId, List<String> ids, String columnName) {
        log.info("Executing findById with ids: {} and columnName: {}", ids, columnName);

        List<UserAction> objFound = findInCache(tenantId, ids);

        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, columnName);
            ids.removeAll(objFound.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                    .collect(Collectors.toList()));

            if (ids.isEmpty()) {
                log.info("All requested user actions found in cache");
                return SearchResponse.<UserAction>builder().response(objFound).build();
            }
        }

        String query = String.format(selectQuery + " WHERE ua.%s IN (:ids) ", columnName);
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);

        try {
            log.debug("Executing query to fetch user actions by ID: {}", query);
            // Replacing schema placeholder with the schema name for the tenant id
            query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
            List<UserAction> userActionList = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);

            objFound.addAll(userActionList);
            putInCache(objFound);

            log.info("Successfully fetched user actions by ID: {}", userActionList.size());
            return SearchResponse.<UserAction>builder().response(objFound).build();
        } catch (Exception e) {
            log.error("Failed to execute query for finding user actions by ID", e);
            return SearchResponse.<UserAction>builder().response(Collections.emptyList()).totalCount(0L).build();
        }
    }
}
