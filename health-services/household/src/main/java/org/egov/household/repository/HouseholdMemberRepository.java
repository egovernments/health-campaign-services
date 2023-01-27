package org.egov.household.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.producer.Producer;
import org.egov.household.repository.rowmapper.HouseholdMemberRowMapper;
import org.egov.household.repository.rowmapper.HouseholdRowMapper;
import org.egov.household.web.models.Household;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.HouseholdMemberSearch;
import org.egov.household.web.models.HouseholdSearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    public List<HouseholdMember> findById(List<String> ids, String columnName, Boolean includeDeleted) {
        List<HouseholdMember> objFound;
        Map<Object, Object> redisMap = this.redisTemplate.opsForHash().entries(tableName);
        List<String> foundInCache = ids.stream().filter(redisMap::containsKey).collect(Collectors.toList());
        objFound = foundInCache.stream().map(id -> (HouseholdMember)redisMap.get(id)).collect(Collectors.toList());
        log.info("Cache hit: {}", !objFound.isEmpty());
        ids.removeAll(foundInCache);
        if (ids.isEmpty()) {
            return objFound;
        }

        String query = String.format("SELECT * FROM household_member where %s IN (:ids) AND isDeleted = false", columnName);
        if (null != includeDeleted && includeDeleted) {
            query = String.format("SELECT * FROM household_member WHERE %s IN (:ids)", columnName);
        }
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("ids", ids);

        objFound.addAll(this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper));
        putInCache(objFound);
        return objFound;
    }

    public List<HouseholdMember> findIndividual(String individualId) {
        String query = "SELECT * FROM household_member where individualId = :individualId AND isDeleted = false";
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("individualId", individualId);

        return this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);
    }

    public List<HouseholdMember> findIndividualByHousehold(String householdId) {
        String query = "SELECT * FROM household_member where householdId = :householdId AND isDeleted = false";
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("householdId", householdId);
        return this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);
    }
}
