package org.egov.project.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.producer.Producer;
import org.egov.project.repository.rowmapper.ProjectBeneficiaryRowMapper;
import org.egov.project.web.models.ProjectBeneficiary;
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
public class ProjectBeneficiaryRepository extends GenericRepository<ProjectBeneficiary> {

    @Autowired
    public ProjectBeneficiaryRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                        RedisTemplate<String, Object> redisTemplate,
                                        SelectQueryBuilder selectQueryBuilder, ProjectBeneficiaryRowMapper projectBeneficiaryRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder,
                projectBeneficiaryRowMapper, Optional.of("project_beneficiary"));
    }

    public List<ProjectBeneficiary> findById(List<String> ids, String columnName, Boolean includeDeleted) {
        List<ProjectBeneficiary> objFound;
        Map<Object, Object> redisMap = this.redisTemplate.opsForHash().entries(tableName);
        List<String> foundInCache = ids.stream().filter(redisMap::containsKey).collect(Collectors.toList());
        objFound = foundInCache.stream().map(id -> (ProjectBeneficiary)redisMap.get(id)).collect(Collectors.toList());
        log.info("Cache hit: {}", !objFound.isEmpty());
        ids.removeAll(foundInCache);
        if (ids.isEmpty()) {
            return objFound;
        }

        String query = String.format("SELECT * FROM project_beneficiary WHERE %s IN (:ids) AND isDeleted = false", columnName);
        if (null != includeDeleted && includeDeleted) {
            query = String.format("SELECT * FROM project_beneficiary WHERE %s IN (:ids)", columnName);
        }

        Map<String, Object> paramMap = new HashMap();
        paramMap.put("ids", ids);

        objFound.addAll(this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper));
        putInCache(objFound);
        return objFound;
    }

}

