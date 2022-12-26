package org.egov.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.producer.Producer;
import org.egov.repository.rowmapper.HouseholdRowMapper;
import org.egov.web.models.Household;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getDifference;

@Repository
@Slf4j
public class HouseholdRepository extends GenericRepository<Household> {

    @Autowired
    protected HouseholdRepository(Producer producer,
                                  NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                  RedisTemplate<String, Object> redisTemplate,
                                  SelectQueryBuilder selectQueryBuilder,
                                  HouseholdRowMapper householdRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, householdRowMapper, Optional.of("household"));
    }

    public List<String> validateClientReferenceId(List<String> clientRefIds, String tableName, String columnName){
        List<String> idsValidated = clientRefIds.stream().filter(id -> redisTemplate.opsForHash()
                        .entries(tableName).containsKey(id))
                .collect(Collectors.toList());
        List<String> idsToFindInDb = getDifference(clientRefIds, idsValidated);

        if (!idsToFindInDb.isEmpty()) {
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("householdIds", clientRefIds);
            String query = String.format("SELECT clientReferenceId FROM HOUSEHOLD WHERE clientReferenceId IN (:householdIds) AND isDeleted = false fetch first %s rows only",
                    clientRefIds.size());
            idsValidated.addAll(namedParameterJdbcTemplate.queryForList(query, paramMap, String.class));
        }

        return idsValidated;
    }
}
