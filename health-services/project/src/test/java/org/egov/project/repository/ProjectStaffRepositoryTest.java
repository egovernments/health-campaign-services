package org.egov.project.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.producer.Producer;
import org.egov.project.helper.ProjectStaffRequestTestBuilder;
import org.egov.project.helper.ProjectStaffTestBuilder;
import org.egov.project.repository.rowmapper.ProjectStaffRowMapper;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffRequest;
import org.egov.project.web.models.ProjectStaffSearch;
import org.egov.project.web.models.ProjectStaffSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
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
    private SelectQueryBuilder selectQueryBuilder;

    @Mock
    private Producer producer;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations hashOperations;

    private List<String> projectStaffIds;

    private List<ProjectStaff> projectStaffs;

    @BeforeEach
    void setUp() {

        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        ReflectionTestUtils.setField(projectStaffRepository, "timeToLive", "60");


        projectStaffs = Collections.singletonList(ProjectStaffTestBuilder
                .builder().withId().build());
        projectStaffIds = projectStaffs.stream().map(ProjectStaff::getId)
                .collect(Collectors.toList());

    }


    @Test
    @DisplayName("should validate and return valid project staff Ids")
    void shouldValidateAndReturnValidProjectStaffIds() {
        List<String> projectStaffIds = new ArrayList<>();
        projectStaffIds.add("some-id");
        projectStaffIds.add("some-other-id");
        List<String> validProjectStaffids = new ArrayList<>(projectStaffIds);
        when(namedParameterJdbcTemplate.queryForList(any(String.class), any(Map.class), eq(String.class)))
                .thenReturn(validProjectStaffids);

        List<String> result = projectStaffRepository.validateProjectStaffId(projectStaffIds);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("should validate by id and return valid project staff")
    void shouldValidateByIdAndReturnValidProjectStaff() {
        List<ProjectStaff> projectStaffs = new ArrayList<>();
        projectStaffs.add(ProjectStaffTestBuilder.builder().goodProjectStaff().withId("123").build());
        when(namedParameterJdbcTemplate.query(any(String.class), any(Map.class), any(RowMapper.class)))
                .thenReturn(projectStaffs);

        List<ProjectStaff> result = projectStaffRepository.findById(Arrays.asList("123"));

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("should return ids from cache for projectStaffIds")
    void shouldValidateAndReturnValidProjectStaffIdsFromCache() {
        List<String> projectStaffs = new ArrayList<>();
        projectStaffs.add("some-id");
        projectStaffs.add("some-other-id");
        HashMap<Object, Object> hashMap = new HashMap<>();
        hashMap.put("some-id", ProjectStaffTestBuilder.builder().goodProjectStaff().build());
        when(hashOperations.entries(any(Object.class))).thenReturn(hashMap);

        List<String> result = projectStaffRepository.validateProjectStaffId(projectStaffs);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("should validate and return empty list if no ids are valid")
    void shouldValidateAndReturnEmptyListIfNoIdsAreValid() {
        List<String> projectStaffIds = new ArrayList<>();
        projectStaffIds.add("some-id");
        projectStaffIds.add("some-other-id");
        when(namedParameterJdbcTemplate.queryForList(any(String.class), any(Map.class), eq(String.class)))
                .thenReturn(Collections.emptyList());

        List<String> result = projectStaffRepository.validateProjectStaffId(projectStaffIds);

        assertEquals(0, result.size());
    }


    @Test
    @DisplayName("should check if project is sent to kafka topic or not")
    void shouldSendProjectStaffToKafkaTopic() throws Exception{
        ProjectStaffRequest projectStaffRequest = ProjectStaffRequestTestBuilder.builder().withRequestInfo().addGoodProjectStaff()
                .withApiOperationCreate().build();

        projectStaffRepository.save(projectStaffRequest.getProjectStaff(), "save-project-staff-topic");

        verify(producer, times(1)).push(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("data should be cached after sending data to kafka topic")
    void shouldCacheDataAfterSendingToKafkaTopic() throws Exception{
        ProjectStaffRequest projectStaffRequest = ProjectStaffRequestTestBuilder.builder().withRequestInfo().addGoodProjectStaff()
                .withApiOperationCreate().build();

        projectStaffRepository.save(projectStaffRequest.getProjectStaff(), "save-project-staff-topic");

        InOrder inOrder = inOrder(producer, hashOperations);

        inOrder.verify(producer, times(1)).push(any(String.class), any(Object.class));
        inOrder.verify(hashOperations, times(1))
                .putAll(any(String.class), any(Map.class));
    }

    @Test
    @DisplayName("data validate all project staff Ids from DB or cache")
    void shouldValidateAllProjectStaffIdsFromDbOrCache() throws Exception{
        when(namedParameterJdbcTemplate.query(any(String.class), any(Map.class), any(RowMapper.class))).thenReturn(Arrays.asList(
                ProjectStaffTestBuilder.builder().goodProjectStaff().withId("ID103").build(),
                ProjectStaffTestBuilder.builder().goodProjectStaff().withId("ID104").build()));
        when(hashOperations.multiGet(anyString(), anyList())).thenReturn(Arrays.asList(
                ProjectStaffTestBuilder.builder().goodProjectStaff().withId("ID101").build(),
                ProjectStaffTestBuilder.builder().goodProjectStaff().withId("ID102").build()));

        List<ProjectStaff> validProjectStaffs = projectStaffRepository.findById(new ArrayList(Arrays.asList("ID101", "ID102", "ID103", "ID104")));

        assertEquals(4, validProjectStaffs.size());
    }

    @Test
    @DisplayName("get projects staffs from db which are deleted")
    void shouldReturnProjectStaffFromDBForSearchRequestWithDeletedIncluded() throws QueryBuilderException {
        List<ProjectStaff> projectStaffs = new ArrayList<>();
        projectStaffs.add(ProjectStaffTestBuilder.builder().goodProjectStaff().withId("ID101").build());
        projectStaffs.add(ProjectStaffTestBuilder.builder().goodProjectStaff().withId("ID101").withIsDeleted().build());
        ProjectStaffSearch projectStaffSearch = ProjectStaffSearch.builder().id("ID101").userId("user-id").build();
        ProjectStaffSearchRequest projectStaffSearchRequest = ProjectStaffSearchRequest.builder().projectStaff(projectStaffSearch)
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();
        when(selectQueryBuilder.build(any(Object.class))).thenReturn("Select * from project_staff where id='ID101' and userId=`user-id'");
        when(namedParameterJdbcTemplate.query(any(String.class), any(Map.class), any(ProjectStaffRowMapper.class)))
                .thenReturn(projectStaffs);

        List<ProjectStaff> projectStaffsResponse = projectStaffRepository.find(projectStaffSearchRequest.getProjectStaff(), 2,
                0, "default", null, true);

        assertEquals(2, projectStaffsResponse.size());
    }


    @Test
    @DisplayName("should find project staff by ids and return the results")
    void shouldFindProjectStaffByIdsAndReturnTheResults() {
        List<ProjectStaff> projectStaffList = getProjectStaffs();

        when(hashOperations.multiGet(anyString(), anyList())).thenReturn(projectStaffList);


        List<ProjectStaff> result = projectStaffRepository.findById(projectStaffIds);

        System.out.println("projectStaffList "+projectStaffList.size()+ " result"+result.size());
        assertEquals(projectStaffIds.size(), result.size());
    }

    private List<ProjectStaff> getProjectStaffs() {
        ProjectStaff projectStaff = ProjectStaff.builder().build();
        List<ProjectStaff> projectStaffList = new ArrayList<>();
        projectStaffList.add(projectStaff);
        return projectStaffList;
    }

    @Test
    @DisplayName("should find project staff by ids in cache first and return the results")
    void shouldFindProjectStaffByIdsInCacheFirstAndReturnTheResults() {
        List<ProjectStaff> projectStaffList = getProjectStaffs();
        when(hashOperations.multiGet(anyString(), anyList())).thenReturn(projectStaffList);

        List<ProjectStaff> result = projectStaffRepository.findById(projectStaffIds);

        assertEquals(projectStaffIds.size(), result.size());
        verify(hashOperations, times(1)).multiGet(anyString(), anyList());
        verify(namedParameterJdbcTemplate, times(0))
                .queryForObject(anyString(), anyMap(), any(RowMapper.class));
    }

    @Test
    @DisplayName("should save list of project staff and return the same successfully")
    void shouldSaveProjectStaffAndReturnSameSuccessfully() {
        List<ProjectStaff> projectStaffList = getProjectStaffs();

        List<ProjectStaff> returnedProjectList = projectStaffRepository.save(projectStaffList,SAVE_KAFKA_TOPIC);

        assertEquals(projectStaffList, returnedProjectList);
        verify(producer, times(1)).push(SAVE_KAFKA_TOPIC, projectStaffList);
    }

}