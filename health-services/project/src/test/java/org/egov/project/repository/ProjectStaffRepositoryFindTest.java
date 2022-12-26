package org.egov.project.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.project.helper.ProjectStaffTestBuilder;
import org.egov.project.repository.rowmapper.ProjectStaffRowMapper;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffSearch;
import org.egov.project.web.models.ProjectStaffSearchRequest;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectStaffRepositoryFindTest {
    @InjectMocks
    private ProjectStaffRepository projectStaffRepository;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private SelectQueryBuilder selectQueryBuilder;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ProjectStaffRowMapper projectStaffRowMapper;

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
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        ReflectionTestUtils.setField(projectStaffRepository, "timeToLive", "60");
    }

    @Test
    @DisplayName("should find project staff by ids and return the results")
    void shouldFindProjectStaffsByIdsAndReturnTheResults() {
        when(hashOperations.multiGet(anyString(), anyList())).thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(projectStaffs);

        List<ProjectStaff> result = projectStaffRepository.findById(projectStaffIds);

        assertEquals(projectStaffIds.size(), result.size());
    }

    @Test
    @DisplayName("get project staff from db for the search request")
    void shouldReturnProjectStaffsFromDBForSearchRequest() throws QueryBuilderException {
        ProjectStaffSearch projectStaffSearch = ProjectStaffSearch.builder().id("ID101").build();
        ProjectStaffSearchRequest projectStaffSearchRequest = ProjectStaffSearchRequest.builder().projectStaff(projectStaffSearch)
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();
        when(selectQueryBuilder.build(any(Object.class))).thenReturn("Select * from project_staff where id='ID101' and name=`project' and isdeleted=false");
        when(namedParameterJdbcTemplate.query(any(String.class), any(Map.class), any(ProjectStaffRowMapper.class)))
                .thenReturn(projectStaffs);

        List<ProjectStaff> projectStaffResponse = projectStaffRepository.find(projectStaffSearchRequest.getProjectStaff(),
                2, 0, "default", null, false);

        assertEquals(1, projectStaffResponse.size());
    }


    @Test
    @DisplayName("get project staff from db which are deleted")
    void shouldReturnProjectStaffFromDBForSearchRequestWithDeletedIncluded() throws QueryBuilderException {
        projectStaffs = new ArrayList<>();
        projectStaffs.add(ProjectStaffTestBuilder.builder().withId().withAuditDetails().withDeleted().build());
        projectStaffs.add(ProjectStaffTestBuilder.builder().withId().withAuditDetails().build());
        ProjectStaffSearch projectStaffSearch = ProjectStaffSearch.builder().id("ID101").build();
        ProjectStaffSearchRequest projectStaffSearchRequest = ProjectStaffSearchRequest.builder()
                .projectStaff(projectStaffSearch).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();
        when(selectQueryBuilder.build(any(Object.class))).thenReturn("Select * from project_staff where id='ID101' and name=`project'");
        when(namedParameterJdbcTemplate.query(any(String.class), any(Map.class), any(ProjectStaffRowMapper.class)))
                .thenReturn(projectStaffs);

        List<ProjectStaff> projectStaffResponse = projectStaffRepository.find(projectStaffSearchRequest.getProjectStaff(), 2,
                0, "default", null, true);

        assertEquals(2, projectStaffResponse.size());
    }
}
