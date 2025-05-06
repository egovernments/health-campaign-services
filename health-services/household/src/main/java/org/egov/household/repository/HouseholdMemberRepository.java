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


    public SearchResponse<HouseholdMember> find(HouseholdMemberSearch householdMemberSearch,
                                                Integer limit,
                                                Integer offset,
                                                String tenantId,
                                                Long lastChangedSince,
                                                Boolean includeDeleted) {

        Map<String, Object> paramsMap = new HashMap<>();
        StringBuilder queryBuilder = new StringBuilder();

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

        Long totalCount = constructTotalCountCTEAndReturnResult(queryBuilder.toString(), paramsMap, this.namedParameterJdbcTemplate);

        queryBuilder.append(" LIMIT :limit OFFSET :offset");
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);

        List<HouseholdMember> householdMembers = this.namedParameterJdbcTemplate.query(queryBuilder.toString(), paramsMap, this.rowMapper);

        return SearchResponse.<HouseholdMember>builder().totalCount(totalCount).response(householdMembers).build();
    }

    public SearchResponse<HouseholdMember> findById(String tenantId, List<String> ids, String columnName, Boolean includeDeleted) throws InvalidTenantIdException {
        List<HouseholdMember> objFound = findInCache( tenantId, ids).stream()
                .filter(entity -> entity.getIsDeleted().equals(includeDeleted))
                .collect(Collectors.toList());
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

        String query = String.format("SELECT * FROM %s.household_member where %s IN (:ids) AND isDeleted = false", SCHEMA_REPLACE_STRING ,columnName);
        if (null != includeDeleted && includeDeleted) {
            query = String.format("SELECT * FROM %s.household_member WHERE %s IN (:ids)", SCHEMA_REPLACE_STRING, columnName);
        }
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("ids", ids);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        objFound.addAll(this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper));
        putInCache(objFound);
        log.info("returning objects from the database");
        return SearchResponse.<HouseholdMember>builder().response(objFound).build();
    }

    public SearchResponse<HouseholdMember> findIndividual(String tenantId , String individualId) throws InvalidTenantIdException {
        log.info("searching for HouseholdMember with individualId: {}", individualId);
        String query = String.format("SELECT * FROM %s.household_member where individualId = :individualId AND isDeleted = false" , SCHEMA_REPLACE_STRING) ;
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("individualId", individualId);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        List<HouseholdMember> householdMembers = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);
        return SearchResponse.<HouseholdMember>builder().totalCount(Long.valueOf(householdMembers.size())).response(householdMembers).build();
    }

    public SearchResponse<HouseholdMember> findIndividualByHousehold(String tenantId, String householdId, String columnName) throws InvalidTenantIdException {
        log.info("searching for HouseholdMembers with householdId: {}", householdId);
        String query = String.format("SELECT * FROM %s.household_member where %s = :householdId AND isDeleted = false",
                SCHEMA_REPLACE_STRING, columnName);
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("householdId", householdId);

        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        Long totalCount = constructTotalCountCTEAndReturnResult(query, paramMap, this.namedParameterJdbcTemplate);

        List<HouseholdMember> householdMembers = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);

        return SearchResponse.<HouseholdMember>builder().totalCount(totalCount).response(householdMembers).build();
    }
}
