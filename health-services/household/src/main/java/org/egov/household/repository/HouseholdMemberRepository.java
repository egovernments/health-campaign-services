package org.egov.household.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.producer.Producer;
import org.egov.household.repository.rowmapper.HouseholdMemberRowMapper;
import org.egov.household.web.models.HouseholdMember;
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

import static org.egov.common.utils.CommonUtils.getIdMethod;

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
        objFound = findInCache(ids);
        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, columnName);
            ids.removeAll(objFound.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                    .collect(Collectors.toList()));
            if (ids.isEmpty()) {
                return objFound;
            }
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

    public List<HouseholdMember> findIndividualByHousehold(String householdId, String columnName) {
        String query = String.format("SELECT * FROM household_member where %s = :householdId AND isDeleted = false",
                columnName);
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("householdId", householdId);
        return this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);
    }
}
