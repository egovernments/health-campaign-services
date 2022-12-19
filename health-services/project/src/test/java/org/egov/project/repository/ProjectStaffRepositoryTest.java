package org.egov.project.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.producer.Producer;
import org.egov.project.helper.ProjectStaffTestBuilder;
import org.egov.project.mapper.ProjectStaffRowMapper;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffSearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.project.service.ProjectStaffService.SAVE_KAFKA_TOPIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectStaffRepositoryTest {

    @InjectMocks
    private ProjectStaffRepository projectStaffRepository;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SelectQueryBuilder selectQueryBuilder;

    @Mock
    private Producer producer;

    @Mock
    private HashOperations hashOperations;

    private List<String> projectStaffIds;

    private List<ProjectStaff> projectStaffs;



    @BeforeEach
    void setUp() {
        projectStaffs = Collections.singletonList(ProjectStaffTestBuilder
                .builder().withId().build());
        projectStaffIds = projectStaffs.stream().map(ProjectStaff::getId)
                .collect(Collectors.toList());
        projectStaffRepository = new ProjectStaffRepository(namedParameterJdbcTemplate, producer, redisTemplate, selectQueryBuilder);
    }

    @Test
    @DisplayName("should find project staff by ids and return the results")
    void shouldFindProjectStaffByIdsAndReturnTheResults() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.multiGet(anyString(), anyList())).thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.queryForObject(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(projectStaffs);

        List<ProjectStaff> result = projectStaffRepository.findById(projectStaffIds);
        assertEquals(projectStaffIds.size(), result.size());
    }

    @Test
    @DisplayName("should find project staff by ids in cache first and return the results")
    void shouldFindProjectStaffByIdsInCacheFirstAndReturnTheResults() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.multiGet(anyString(), anyList())).thenReturn(projectStaffs);

        List<ProjectStaff> result = projectStaffRepository.findById(projectStaffIds);

        assertEquals(projectStaffIds.size(), result.size());
        verify(hashOperations, times(1)).multiGet(anyString(), anyList());
        verify(namedParameterJdbcTemplate, times(0))
                .queryForObject(anyString(), anyMap(), any(RowMapper.class));
    }

    @Test
    @DisplayName("should save list of project staff and return the same successfully")
    void shouldSaveProjectStaffAndReturnSameSuccessfully() {
        ProjectStaff projectStaff = ProjectStaff.builder().build();
        List<ProjectStaff> projectStaffList = new ArrayList<>();
        projectStaffList.add(projectStaff);
        List<ProjectStaff> returnedProjectList = projectStaffRepository.save(projectStaffList,SAVE_KAFKA_TOPIC);

        assertEquals(projectStaffList, returnedProjectList);
        verify(producer, times(1)).push(SAVE_KAFKA_TOPIC, projectStaffList);
    }


    @Test
    @DisplayName("should search and call query builder and namedJdbc template successfully and get results when includeDeleted is false")
    void shouldCallQueryBuilderSuccessfully() throws QueryBuilderException {
        ProjectStaffSearch search = new ProjectStaffSearch();
        int limit = 10;
        int offset = 0;
        String tenantId = "tenantId";
        Long lastChangedSince = 12345L;
        Boolean includeDeleted = false;

        List<ProjectStaff> expectedResult = Collections.emptyList();
        when(selectQueryBuilder.build(search)).thenReturn("some query");
        when(namedParameterJdbcTemplate.query(any(String.class), any(Map.class), any(ProjectStaffRowMapper.class)))
                .thenReturn(expectedResult);

        List<ProjectStaff> result = projectStaffRepository.find(search, limit, offset, tenantId, lastChangedSince, includeDeleted);

        Map<String,Object> params = new HashMap<>();
        params.put("tenantId",tenantId);
        params.put("isDeleted",includeDeleted);
        params.put("lastModifiedTime",lastChangedSince);
        params.put("limit",limit);
        params.put("offset",offset);

        verify(selectQueryBuilder).build(search);
        verify(namedParameterJdbcTemplate).query(
                eq("some query and tenantId=:tenantId and isDeleted=:isDeleted and lastModifiedTime>=:lastModifiedTime  ORDER BY id ASC LIMIT :limit OFFSET :offset "),
                eq(params),
                any(ProjectStaffRowMapper.class));
        assertEquals(expectedResult, result);
    }


    @Test
    @DisplayName("should search and call query builder and namedJdbc template successfully and get results when includeDeleted is true")
    void shouldCallQueryBuilderSuccessfullyWithIncludeDeletedTrue() throws QueryBuilderException {
        ProjectStaffSearch search = new ProjectStaffSearch();
        int limit = 10;
        int offset = 0;
        String tenantId = "tenantId";
        Long lastChangedSince = 12345L;
        Boolean includeDeleted = true;

        List<ProjectStaff> expectedResult = Collections.emptyList();
        when(selectQueryBuilder.build(search)).thenReturn("some query");
        when(namedParameterJdbcTemplate.query(any(String.class), any(Map.class), any(ProjectStaffRowMapper.class)))
                .thenReturn(expectedResult);

        List<ProjectStaff> result = projectStaffRepository.find(search, limit, offset, tenantId, lastChangedSince, includeDeleted);

        Map<String,Object> params = new HashMap<>();
        params.put("tenantId",tenantId);
        params.put("isDeleted",includeDeleted);
        params.put("lastModifiedTime",lastChangedSince);
        params.put("limit",limit);
        params.put("offset",offset);

        verify(selectQueryBuilder).build(search);
        verify(namedParameterJdbcTemplate).query(
                eq("some query and tenantId=:tenantId and lastModifiedTime>=:lastModifiedTime  ORDER BY id ASC LIMIT :limit OFFSET :offset "),
                eq(params),
                any(ProjectStaffRowMapper.class));
        assertEquals(expectedResult, result);
    }



}