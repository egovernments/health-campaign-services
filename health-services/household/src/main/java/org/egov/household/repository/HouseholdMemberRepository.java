package org.egov.household.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberSearch;
import org.egov.common.producer.Producer;
import org.egov.household.repository.rowmapper.HouseholdMemberRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
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
public class HouseholdMemberRepository extends GenericRepository<HouseholdMember> {

    @Autowired
    protected HouseholdMemberRepository(Producer producer,
                                        NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                        RedisTemplate<String, Object> redisTemplate,
                                        SelectQueryBuilder selectQueryBuilder,
                                        HouseholdMemberRowMapper householdMemberRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, householdMemberRowMapper, Optional.of("household_member"));
    }


    /**
     * This method fetches the list of household members based on the search criteria provided.
     *
     * @param householdMemberSearch The criteria used to filter the household members.
     * @param limit                The maximum number of records to return.
     * @param offset               The starting point for fetching records.
     * @param tenantId             The tenant ID for which the records are being fetched.
     * @param lastChangedSince     The timestamp to filter records that have changed since this time.
     * @param includeDeleted       Flag to include deleted records in the result.
     * @return SearchResponse<HouseholdMember> A response object containing the list of household members and total count.
     */
    public SearchResponse<HouseholdMember> find(HouseholdMemberSearch householdMemberSearch,
                                                Integer limit,
                                                Integer offset,
                                                String tenantId,
                                                Long lastChangedSince,
                                                Boolean includeDeleted) throws InvalidTenantIdException {

        Map<String, Object> paramsMap = new HashMap<>();
        StringBuilder queryBuilder = new StringBuilder();

        // Check if the tenant ID is valid
        String query = "SELECT * FROM %s.household_member";
        query =String.format(query, SCHEMA_REPLACE_STRING);
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
        // replace the schema placeholder with the actual tenant ID
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(queryBuilder.toString(), tenantId);
        Long totalCount = constructTotalCountCTEAndReturnResult(query, paramsMap, this.namedParameterJdbcTemplate);

        // Add pagination to the query
        queryBuilder = new StringBuilder(query).append(" LIMIT :limit OFFSET :offset");
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);

        List<HouseholdMember> householdMembers = this.namedParameterJdbcTemplate.query(queryBuilder.toString(), paramsMap, this.rowMapper);

        return SearchResponse.<HouseholdMember>builder().totalCount(totalCount).response(householdMembers).build();
    }

    /**
     * This method fetches a list of household members based on their IDs.
     *
     * @param tenantId       The tenant ID for which the records are being fetched.
     * @param ids            The list of IDs of the household members to be fetched.
     * @param columnName     The name of the column to be used for filtering.
     * @param includeDeleted Flag to include deleted records in the result.
     * @return SearchResponse<HouseholdMember> A response object containing the list of household members and total count.
     */
    public SearchResponse<HouseholdMember> findById(String tenantId, List<String> ids, String columnName, Boolean includeDeleted) throws InvalidTenantIdException {
        List<HouseholdMember> objFound = findInCache( tenantId, ids).stream()
                .filter(entity -> entity.getIsDeleted().equals(includeDeleted))
                .collect(Collectors.toList());
        // Check if the list of IDs is empty
        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, columnName);
            ids.removeAll(objFound.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                    .toList());
            if (ids.isEmpty()) {
                log.info("all objects were found in the cache, returning objects");
                return SearchResponse.<HouseholdMember>builder().response(objFound).build();
            }
        }

        // If the list of IDs is not empty, proceed to fetch from the database
        String query = String.format("SELECT * FROM %s.household_member where %s IN (:ids) AND isDeleted = false", SCHEMA_REPLACE_STRING ,columnName);
        if (null != includeDeleted && includeDeleted) {
            // If includeDeleted is true, fetch all records including deleted ones
            query = String.format("SELECT * FROM %s.household_member WHERE %s IN (:ids)", SCHEMA_REPLACE_STRING, columnName);
        }
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("ids", ids);
        // replace the schema placeholder with the actual tenant ID
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        objFound.addAll(this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper));
        putInCache(objFound);
        log.info("returning objects from the database");
        return SearchResponse.<HouseholdMember>builder().response(objFound).build();
    }

    /**
     * This method fetches a list of household members based on their individual IDs.
     *
     * @param tenantId       The tenant ID for which the records are being fetched.
     * @param individualId   The individual ID of the household members to be fetched.
     * @return SearchResponse<HouseholdMember> A response object containing the list of household members and total count.
     */
    public SearchResponse<HouseholdMember> findIndividual(String tenantId , String individualId) throws InvalidTenantIdException {
        log.info("searching for HouseholdMember with individualId: {}", individualId);
        // add the schema placeholder to the query
        String query = String.format("SELECT * FROM %s.household_member where individualId = :individualId AND isDeleted = false" , SCHEMA_REPLACE_STRING) ;
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("individualId", individualId);
        // replace the schema placeholder with the actual tenant ID
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        List<HouseholdMember> householdMembers = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);
        return SearchResponse.<HouseholdMember>builder().totalCount(Long.valueOf(householdMembers.size())).response(householdMembers).build();
    }

    /**
     * This method fetches a list of household members based on their household ID.
     *
     * @param tenantId       The tenant ID for which the records are being fetched.
     * @param householdId    The household ID of the household members to be fetched.
     * @param columnName     The name of the column to be used for filtering.
     * @return SearchResponse<HouseholdMember> A response object containing the list of household members and total count.
     */
    public SearchResponse<HouseholdMember> findIndividualByHousehold(String tenantId, String householdId, String columnName) throws InvalidTenantIdException {
        log.info("searching for HouseholdMembers with householdId: {}", householdId);
        // add the schema placeholder to the query
        String query = String.format("SELECT * FROM %s.household_member where %s = :householdId AND isDeleted = false",
                SCHEMA_REPLACE_STRING, columnName);
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("householdId", householdId);
        // replace the schema placeholder with the actual tenant ID
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        Long totalCount = constructTotalCountCTEAndReturnResult(query, paramMap, this.namedParameterJdbcTemplate);

        List<HouseholdMember> householdMembers = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);

        return SearchResponse.<HouseholdMember>builder().totalCount(totalCount).response(householdMembers).build();
    }
}
