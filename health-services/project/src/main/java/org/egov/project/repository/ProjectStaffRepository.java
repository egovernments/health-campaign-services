package org.egov.project.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.producer.Producer;
import org.egov.project.repository.rowmapper.ProjectStaffRowMapper;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffSearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class ProjectStaffRepository {

    private static final String HASH_KEY = "project-staff";
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final Producer producer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SelectQueryBuilder selectQueryBuilder;
    @Value("${spring.cache.redis.time-to-live:60}")
    private String timeToLive;

    @Autowired
    public ProjectStaffRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                  RedisTemplate<String, Object> redisTemplate, SelectQueryBuilder selectQueryBuilder) {
        this.producer = producer;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.selectQueryBuilder = selectQueryBuilder;
    }

    public List<String> validateProjectStaffId(List<String> ids) {
        List<String> projectStaffIds = ids.stream().filter(id -> redisTemplate.opsForHash()
                        .entries(HASH_KEY).containsKey(id))
                .collect(Collectors.toList());
        if (!projectStaffIds.isEmpty()) {
            return projectStaffIds;
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("projectStaffIds", ids);
        String query = String.format(
                "SELECT id FROM project_staff WHERE id IN (:projectStaffIds) AND isDeleted = false fetch first %s rows only",
                ids.size()
        );
        return namedParameterJdbcTemplate.queryForList(query, paramMap, String.class);
    }

    public List<ProjectStaff> findById(List<String> ids) {
        ArrayList<ProjectStaff> projectStaffsFound = new ArrayList<>();
        Collection<Object> collection = new ArrayList<>(ids);
        List<Object> projectStaffs = redisTemplate.opsForHash()
                .multiGet(HASH_KEY, collection);
        if (!projectStaffs.isEmpty() && !projectStaffs.contains(null)) {
            log.info("Cache hit");
            projectStaffsFound = (ArrayList<ProjectStaff>) projectStaffs.stream()
                    .map(ProjectStaff.class::cast)
                    .collect(Collectors.toList());
            // return only if all the projectStaffs are found in cache
            ids.removeAll(projectStaffsFound.stream().map(ProjectStaff::getId).collect(Collectors.toList()));
            if (ids.isEmpty()) {
                return projectStaffsFound;
            }
        }

        String query = String.format(
                "SELECT * FROM project_staff WHERE id IN (:projectStaffIds) AND isDeleted = false fetch first %s rows only",
                ids.size()
        );
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("projectStaffIds", ids);

        projectStaffsFound.addAll(
                namedParameterJdbcTemplate.query(
                        query,
                        paramMap,
                        new ProjectStaffRowMapper()
                )
        );
        putInCache(projectStaffsFound);
        return projectStaffsFound;
    }

    public List<ProjectStaff> save(List<ProjectStaff> projectStaffs, String topic) {
        producer.push(topic, projectStaffs);
        log.info("Pushed to kafka");
        putInCache(projectStaffs);
        return projectStaffs;
    }

    private void putInCache(List<ProjectStaff> projectStaffs) {
        Map<String, ProjectStaff> projectStaffMap = projectStaffs.stream()
                .collect(Collectors
                        .toMap(ProjectStaff::getId,
                                projectStaff -> projectStaff));
        redisTemplate.opsForHash().putAll(HASH_KEY, projectStaffMap);
        redisTemplate.expire(HASH_KEY, Long.parseLong(timeToLive), TimeUnit.SECONDS);
    }

    public List<ProjectStaff> find(ProjectStaffSearch projectStaffSearch,
                                   Integer limit,
                                   Integer offset,
                                   String tenantId,
                                   Long lastChangedSince,
                                   Boolean includeDeleted) throws QueryBuilderException {
        String query = selectQueryBuilder.build(projectStaffSearch);
        query += " and tenantId=:tenantId ";
        if (Boolean.FALSE.equals(includeDeleted)) {
            query += "and isDeleted=:isDeleted ";
        }
        if (lastChangedSince != null) {
            query += "and lastModifiedTime>=:lastModifiedTime ";
        }
        query += "ORDER BY id ASC LIMIT :limit OFFSET :offset";
        Map<String, Object> paramsMap = selectQueryBuilder.getParamsMap();
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);
        return namedParameterJdbcTemplate.query(query, paramsMap, new ProjectStaffRowMapper());
    }
}

