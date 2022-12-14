package org.egov.project.repository;

import org.egov.common.producer.Producer;
import org.egov.project.helper.ProjectStaffTestBuilder;
import org.egov.project.web.models.ProjectStaff;
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
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        projectStaffRepository = new ProjectStaffRepository(namedParameterJdbcTemplate,producer,redisTemplate);
    }

    @Test
    @DisplayName("should find project staff by ids and return the results")
    void shouldFindProductVariantsByIdsAndReturnTheResults() {
        when(hashOperations.multiGet(anyString(), anyList())).thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.queryForObject(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(projectStaffs);

        List<ProjectStaff> result = projectStaffRepository.findById(projectStaffIds);
        assertEquals(projectStaffIds.size(), result.size());
    }

    @Test
    @DisplayName("should find project staff by ids in cache first and return the results")
    void shouldFindProductVariantsByIdsInCacheFirstAndReturnTheResults() {
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
        List<ProjectStaff> returnedProjectList = projectStaffRepository.save(projectStaffList);

        assertEquals(projectStaffList, returnedProjectList);
        verify(producer, times(1)).push(ProjectStaffRepository.SAVE_KAFKA_TOPIC, projectStaffList);
    }




}