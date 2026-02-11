package org.egov.facility.repository;

import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilitySearch;
import org.egov.common.producer.Producer;
import org.egov.facility.repository.rowmapper.FacilityRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
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
public class FacilityRepository extends GenericRepository<Facility> {
    @Autowired
    public FacilityRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                              RedisTemplate<String, Object> redisTemplate,
                              SelectQueryBuilder selectQueryBuilder, FacilityRowMapper facilityRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder,
                facilityRowMapper, Optional.of("facility"));
    }

    /**
     * Finds and retrieves a list of Facility objects based on the provided identifiers and criteria.
     *
     * @param tenantId        the tenant identifier to scope the query
     * @param ids             the list of unique identifiers for fetching the Facility objects
     * @param columnName      the column name that specifies the identifier's attribute
     * @param includeDeleted  if true, includes deleted Facility objects in the response; otherwise, filters them out
     * @return a SearchResponse object containing the list of matched Facility objects
     * @throws InvalidTenantIdException if the provided tenantId is invalid
     */
    public SearchResponse<Facility> findById(String tenantId, List<String> ids, String columnName, Boolean includeDeleted) throws InvalidTenantIdException {
        List<Facility> objFound = findInCache(tenantId, ids);
        if (!includeDeleted) {
            objFound = objFound.stream()
                    .filter(entity -> entity.getIsDeleted().equals(false))
                    .collect(Collectors.toList());
        }
        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, columnName);
            ids.removeAll(objFound.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                    .toList());
            if (ids.isEmpty()) {
                return SearchResponse.<Facility>builder().response(objFound).build();
            }
        }

        String query = String.format("SELECT *, a.id as aid,a.tenantid as atenantid, a.clientreferenceid as aclientreferenceid FROM %s.facility f LEFT JOIN %s.address a ON f.addressid = a.id WHERE f.%s IN (:ids) AND isDeleted = false", SCHEMA_REPLACE_STRING, SCHEMA_REPLACE_STRING, columnName);
        if (null != includeDeleted && includeDeleted) {
            query = String.format("SELECT *, a.id as aid,a.tenantid as atenantid, a.clientreferenceid as aclientreferenceid FROM %s.facility f LEFT JOIN %s.address a ON f.addressid = a.id  WHERE f.%s IN (:ids)", SCHEMA_REPLACE_STRING, SCHEMA_REPLACE_STRING, columnName);
        }
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("ids", ids);

        // Replacing schema placeholder with the schema name for the tenant id
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        objFound.addAll(this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper));
        putInCache(objFound);

        /*The totalCount is being set automatically through the builder method.*/
        return SearchResponse.<Facility>builder().response(objFound).build();
    }

    /**
     * Finds and retrieves a list of Facility objects based on the given search criteria and pagination details.
     *
     * @param searchObject    the search object containing query criteria for filtering Facility records
     * @param limit           the maximum number of records to return
     * @param offset          the starting offset for paginated results
     * @param tenantId        the tenant identifier to scope the query
     * @param lastChangedSince timestamp to filter records modified since the given time; can be null
     * @param includeDeleted  if true, includes deleted Facility objects in the response; otherwise, filters them out
     * @return a SearchResponse object containing the list of matched Facility objects and the total count of matched records
     * @throws QueryBuilderException      if query construction fails for any reason
     * @throws InvalidTenantIdException   if the provided tenantId is invalid
     */
    public SearchResponse<Facility> find(FacilitySearch searchObject, Integer limit, Integer offset, String tenantId, Long lastChangedSince, Boolean includeDeleted) throws QueryBuilderException, InvalidTenantIdException {
        String query = String.format("SELECT *, a.id as aid,a.tenantid as atenantid, a.clientreferenceid as aclientreferenceid FROM %s.facility f LEFT JOIN %s.address a ON f.addressid = a.id", SCHEMA_REPLACE_STRING, SCHEMA_REPLACE_STRING);
        Map<String, Object> paramsMap = new HashMap<>();
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject, QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();
        query = query.replace("id IN (:id)", "f.id IN (:id)");

        if(CollectionUtils.isEmpty(whereFields)) {
            query = query + " where f.tenantId=:tenantId ";
        } else {
            query = query + " and f.tenantId=:tenantId ";
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

        // Replacing schema placeholder with the schema name for the tenant id
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);

        Long totalCount = constructTotalCountCTEAndReturnResult(query, paramsMap, this.namedParameterJdbcTemplate);

        query = query + " LIMIT :limit OFFSET :offset";
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);
        List<Facility> facilities =  this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);

        return SearchResponse.<Facility>builder().response(facilities).totalCount(totalCount).build();
    }
}
