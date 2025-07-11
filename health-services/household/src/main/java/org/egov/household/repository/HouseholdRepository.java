package org.egov.household.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.household.Household;
import org.egov.common.producer.Producer;
import org.egov.household.repository.rowmapper.HouseholdRowMapper;
import org.egov.common.models.household.HouseholdSearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanCursor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.constructTotalCountCTEAndReturnResult;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

@Repository
@Slf4j
public class HouseholdRepository extends GenericRepository<Household> {

    private final String searchCriteriaWaypointQuery = "WITH cte_search_criteria_waypoint(s_latitude, s_longitude) AS (VALUES(:s_latitude, :s_longitude))\n";
    private final String calculateDistanceFromTwoWaypointsFormulaQuery = "( 6371.4 * acos (LEAST (GREATEST (cos ( radians(cte_scw.s_latitude) ) * cos( radians(a.latitude) ) * cos( radians(a.longitude) - radians(cte_scw.s_longitude) ) + sin ( radians(cte_scw.s_latitude) ) * sin( radians(a.latitude) ), -1), 1) ) ) AS distance ";
    @Autowired
    protected HouseholdRepository(Producer producer,
                                  NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                  RedisTemplate<String, Object> redisTemplate,
                                  SelectQueryBuilder selectQueryBuilder,
                                  HouseholdRowMapper householdRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, householdRowMapper, Optional.of("household h"));
    }

    /**
     * @param tenantId
     * @param ids
     * @param columnName
     * @param includeDeleted
     * @return
     * @throws InvalidTenantIdException
     *
     * Fetches the household details based on the provided IDs and column name.
     */
    public SearchResponse<Household> findById(String tenantId, List<String> ids, String columnName, Boolean includeDeleted) throws InvalidTenantIdException {
        List<Household> objFound = findInCache( tenantId,ids);
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
                return SearchResponse.<Household>builder().totalCount(Long.valueOf(objFound.size())).response(objFound).build();
            }
        }
        // add the schema placeholder to the query
        String query = String.format("SELECT *, a.id as aid,a.tenantid as atenantid, a.clientreferenceid as aclientreferenceid FROM %s.household h LEFT JOIN %s.address a ON h.addressid = a.id WHERE h.%s IN (:ids) AND isDeleted = false",SCHEMA_REPLACE_STRING , SCHEMA_REPLACE_STRING, columnName);
        if (null != includeDeleted && includeDeleted) {
            query = String.format("SELECT *, a.id as aid,a.tenantid as atenantid, a.clientreferenceid as aclientreferenceid FROM %s.household h LEFT JOIN %s.address a ON h.addressid = a.id  WHERE h.%s IN (:ids)", SCHEMA_REPLACE_STRING , SCHEMA_REPLACE_STRING, columnName);
        }
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("ids", ids);

        // replace the schema placeholder with the tenantId
        query=  multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        Long totalCount = constructTotalCountCTEAndReturnResult(query, paramMap, this.namedParameterJdbcTemplate);

        objFound.addAll(this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper));
        putInCache(objFound);
        return SearchResponse.<Household>builder().totalCount(totalCount).response(objFound).build();
    }

    /**
     * @param searchObject
     * @param limit
     * @param offset
     * @param tenantId
     * @param lastChangedSince
     * @param includeDeleted
     * @return
     * @throws QueryBuilderException
     *
     * Fetch all the household based on the search criteria provided.
     */
    public SearchResponse<Household> find(HouseholdSearch searchObject, Integer limit, Integer offset, String tenantId, Long lastChangedSince, Boolean includeDeleted) throws InvalidTenantIdException {
        // add the schema placeholder to the query
        String query = String.format("SELECT *, a.id as aid,a.tenantid as atenantid, a.clientreferenceid as aclientreferenceid FROM %s.household h LEFT JOIN %s.address a ON h.addressid = a.id", SCHEMA_REPLACE_STRING, SCHEMA_REPLACE_STRING) ;
        Map<String, Object> paramsMap = new HashMap<>();
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject, QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();
        query = query.replace("id IN (:id)", "h.id IN (:id)");
        query = query.replace("clientReferenceId IN (:clientReferenceId)", "h.clientReferenceId IN (:clientReferenceId)");
        // To consider null values present in db as family if family parameter is passed
        if (searchObject.getHouseholdType() != null && searchObject.getHouseholdType().equalsIgnoreCase("FAMILY")) {
            query = query.replace("householdType=:householdType", "(householdType!='COMMUNITY' OR householdType IS NULL)");
        }

        if(CollectionUtils.isEmpty(whereFields)) {
            query = query + " where h.tenantId=:tenantId ";
        } else {
            query = query + " and h.tenantId=:tenantId ";
        }

        if (Boolean.FALSE.equals(includeDeleted)) {
            query = query + "and isDeleted=:isDeleted ";
        }

        if (lastChangedSince != null) {
            query = query + "and lastModifiedTime>=:lastModifiedTime ";
        }
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        // replace the schema placeholder with the tenantId
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        Long totalCount = constructTotalCountCTEAndReturnResult(query, paramsMap, this.namedParameterJdbcTemplate);

        query = query + "ORDER BY h.id ASC LIMIT :limit OFFSET :offset";
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);
        List<Household> households = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);
        return SearchResponse.<Household>builder().totalCount(totalCount).response(households).build();
    }

    /**
     * @param searchObject
     * @param limit
     * @param offset
     * @param tenantId
     * @param includeDeleted
     * @return
     * @throws QueryBuilderException
     *
     * Fetch all the household which falls under the radius provided using longitude and latitude provided.
     */
    public SearchResponse<Household> findByRadius(HouseholdSearch searchObject, Integer limit, Integer offset, String tenantId, Boolean includeDeleted) throws QueryBuilderException, InvalidTenantIdException {
        // add the schema placeholder to the query
        String query = String.format(searchCriteriaWaypointQuery +
                "SELECT * FROM (SELECT h.*, a.*, a.id as aid,a.tenantid as atenantid, a.clientreferenceid as aclientreferenceid, " + calculateDistanceFromTwoWaypointsFormulaQuery + " \n" +
                "FROM %s.household h LEFT JOIN %s.address a ON h.addressid = a.id AND h.tenantid = a.tenantid, cte_search_criteria_waypoint cte_scw ", SCHEMA_REPLACE_STRING, SCHEMA_REPLACE_STRING);
        Map<String, Object> paramsMap = new HashMap<>();
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject, QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();
        query = query.replace("id IN (:id)", "h.id IN (:id)");
        query = query.replace("clientReferenceId IN (:clientReferenceId)", "h.clientReferenceId IN (:clientReferenceId)");
        // To consider null values present in db as family if family parameter is passed
        if (searchObject.getHouseholdType() != null && searchObject.getHouseholdType().equalsIgnoreCase("FAMILY")) {
            query = query.replace("householdType=:householdType", "(householdType!='COMMUNITY' OR householdType IS NULL)");
        }

        if(CollectionUtils.isEmpty(whereFields)) {
            query = query + " where h.tenantId=:tenantId ";
        } else {
            query = query + " and h.tenantId=:tenantId ";
        }

        if (Boolean.FALSE.equals(includeDeleted)) {
            query = query + "and isDeleted=:isDeleted ";
        }
        query = query + " ) AS rt ";
        query = query + " WHERE distance < :distance ";
        paramsMap.put("s_latitude", searchObject.getLatitude());
        paramsMap.put("s_longitude", searchObject.getLongitude());
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("distance", searchObject.getSearchRadius());
        query = query + " ORDER BY distance ASC";
        // replace the schema placeholder with the tenantId
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        Long totalCount = constructTotalCountCTEAndReturnResult(query, paramsMap, this.namedParameterJdbcTemplate);
        query = query + " LIMIT :limit OFFSET :offset ";
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);
        List<Household> households = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);
        return SearchResponse.<Household>builder().totalCount(totalCount).response(households).build();
    }

}
