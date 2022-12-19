package org.egov.project.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.producer.Producer;
import org.egov.project.mapper.ProjectStaffRowMapper;
import org.egov.project.web.models.AdditionalFields;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffSearch;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class ProjectStaffRepository {

    private final Producer producer;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SelectQueryBuilder selectQueryBuilder;

    @Autowired
    public ProjectStaffRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                  Producer producer,
                                  RedisTemplate<String, Object> redisTemplate,
                                  SelectQueryBuilder selectQueryBuilder) {
        this.producer = producer;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.selectQueryBuilder = selectQueryBuilder;
    }

    public List<ProjectStaff> save(List<ProjectStaff> projectStaffs, String topic) {
        producer.push(topic, projectStaffs);
        putInCache(projectStaffs);
        return projectStaffs;
    }

    public List<ProjectStaff> findById(List<String> ids) {
        Collection<Object> collection = new ArrayList<>(ids);
        List<Object> projectStaff = redisTemplate.opsForHash().multiGet("project-staff", collection);
        if (projectStaff != null && !projectStaff.isEmpty()) {
            log.info("Cache hit");
            return projectStaff.stream().map(ProjectStaff.class::cast).collect(Collectors.toList());
        }
        String query = "SELECT * FROM project_staff WHERE id IN (:ids) and isDeleted = false";
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);
        try {
            return namedParameterJdbcTemplate.queryForObject(query, paramMap,
                    ((resultSet, i) -> {
                        List<ProjectStaff> projectStaffs = new ArrayList<>();
                        try {
                            mapRow(resultSet, projectStaffs);
                            while (resultSet.next()) {
                                mapRow(resultSet, projectStaffs);
                            }
                        } catch (Exception e) {
                            throw new CustomException("ERROR_IN_SELECT", e.getMessage());
                        }
                        putInCache(projectStaffs);
                        return projectStaffs;
                    }));
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        }
    }

    private void putInCache(List<ProjectStaff> projectStaffs) {
        Map<String, ProjectStaff> projectStaffMap = projectStaffs.stream()
                .collect(Collectors
                        .toMap(ProjectStaff::getId,
                                projectStaff -> projectStaff));
        redisTemplate.opsForHash().putAll("project-staff", projectStaffMap);
    }

    private void mapRow(ResultSet resultSet, List<ProjectStaff> projectStaffs) throws SQLException, JsonProcessingException {
        ProjectStaff projectStaff = ProjectStaff.builder()
                .id(resultSet.getString("id"))
                .projectId(resultSet.getString("projectId"))
                .tenantId(resultSet.getString("tenantId"))
                .channel(resultSet.getString("channel"))
                .userId(resultSet.getString("userId"))
                .startDate(resultSet.getLong("startDate"))
                .endDate(resultSet.getLong("endDate"))
                .isDeleted(resultSet.getBoolean("isDeleted"))
                .rowVersion(resultSet.getInt("rowVersion"))
                .additionalFields(new ObjectMapper()
                        .readValue(resultSet.getString("additionalDetails"),
                                AdditionalFields.class))
                .auditDetails(AuditDetails.builder()
                        .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                        .createdTime(resultSet.getLong("createdTime"))
                        .createdBy(resultSet.getString("createdBy"))
                        .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                        .build())
                .build();
        projectStaffs.add(projectStaff);
    }

    public List<ProjectStaff> find(ProjectStaffSearch projectStaffSearch,
                                   Integer limit,
                                   Integer offset,
                                   String tenantId,
                                   Long lastChangedSince,
                                   Boolean includeDeleted) throws QueryBuilderException {
        String query = selectQueryBuilder.build(projectStaffSearch);
        query += " and tenantId=:tenantId ";

        if (!includeDeleted) {
            query += "and isDeleted=:isDeleted ";
        }
        if(lastChangedSince != null){
            query += "and lastModifiedTime>=:lastModifiedTime ";
        }
        query += " ORDER BY id ASC LIMIT :limit OFFSET :offset ";
        Map<String, Object> paramsMap = selectQueryBuilder.getParamsMap();
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);
        List<ProjectStaff> projectStaffs = namedParameterJdbcTemplate.query(query, paramsMap, new ProjectStaffRowMapper());
        return projectStaffs;
    }
}

