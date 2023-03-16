package org.egov.project.service;

import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.models.project.TaskSearchRequest;
import org.egov.project.helper.TaskTestBuilder;
import org.egov.project.repository.ProjectTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProjectTaskServiceSearchTest {

    @InjectMocks
    private ProjectTaskService projectTaskService;

    @Mock
    private ProjectTaskRepository projectTaskRepository;
    
    private List<Task> projectTasks;

    @BeforeEach
    void setUp() {
        projectTasks = new ArrayList<>();
    }

    @Test
    @DisplayName("should search only by id if only id is present")
    void shouldOnlySearchByIdIfOnlyIdIsPresent() {
        TaskSearchRequest taskSearchRequest = TaskSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .task(TaskSearch.builder().id(Collections.singletonList("some-id")).build()).build();
        when(projectTaskRepository.findById(anyList(), eq("id"), anyBoolean()))
                .thenReturn(Collections.emptyList());

        projectTaskService.search(taskSearchRequest.getTask(), 10, 0, "default",
                null, false);

        verify(projectTaskRepository, times(1))
                .findById(anyList(), eq("id"), anyBoolean());
    }

    @Test
    @DisplayName("should search only by clientReferenceId if only clientReferenceId is present")
    void shouldOnlySearchByClientReferenceIdIfOnlyClientReferenceIdIsPresent() {
        TaskSearchRequest taskSearchRequest = TaskSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .task(TaskSearch.builder().clientReferenceId(Collections.singletonList("some-id")).build()).build();
        when(projectTaskRepository.findById(anyList(), eq("clientReferenceId"), anyBoolean()))
                .thenReturn(Collections.emptyList());

        projectTaskService.search(taskSearchRequest.getTask(), 10, 0, "default",
                null, false);

        verify(projectTaskRepository, times(1)).findById(anyList(),
                eq("clientReferenceId"), anyBoolean());
    }

    @Test
    @DisplayName("should not call findById if more search parameters are available")
    void shouldNotCallFindByIfIfMoreParametersAreAvailable() throws QueryBuilderException {
        TaskSearchRequest taskSearchRequest = TaskSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .task(TaskSearch.builder().id(Collections.singletonList("some-id")).clientReferenceId(Collections.singletonList("some-id")).build()).build();
        when(projectTaskRepository.find(any(TaskSearch.class), anyInt(),
                anyInt(), anyString(), anyLong(), anyBoolean())).thenReturn(Collections.emptyList());

        projectTaskService.search(taskSearchRequest.getTask(), 10, 0,
                "default", 0L, false);

        verify(projectTaskRepository, times(0))
                .findById(anyList(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("should call find if more parameters are available")
    void shouldCallFindIfMoreParametersAreAvailable() throws QueryBuilderException {
        TaskSearchRequest taskSearchRequest = TaskSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .task(TaskSearch.builder().id(Collections.singletonList("some-id")).clientReferenceId(Collections.singletonList("some-id")).build()).build();
        when(projectTaskRepository.find(any(TaskSearch.class), anyInt(),
                anyInt(), anyString(), anyLong(), anyBoolean())).thenReturn(Collections.emptyList());

        projectTaskService.search(taskSearchRequest.getTask(), 10, 0,
                "default", 0L, false);

        verify(projectTaskRepository, times(1))
                .find(any(TaskSearch.class), anyInt(),
                        anyInt(), anyString(), anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("should not raise exception if no search results are found")
    void shouldNotRaiseExceptionIfNoProjectTaskFound() throws Exception {
        when(projectTaskRepository.find(any(TaskSearch.class), any(Integer.class),
                any(Integer.class), any(String.class), eq(null), any(Boolean.class))).thenReturn(Collections.emptyList());
        TaskSearchRequest taskSearchRequest = TaskSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .task(TaskSearch.builder().id(Collections.singletonList("someid")).clientReferenceId(Collections.singletonList("some-id")).build()).build();

        assertDoesNotThrow(() -> projectTaskService.search(taskSearchRequest.getTask(), 10, 0, "default", 
                null, false));
    }


    @Test
    @DisplayName("should not raise exception if no search results are found for search by id")
    void shouldNotRaiseExceptionIfNoProjectTaskFoundForSearchById() throws Exception {
        when(projectTaskRepository.findById(anyList(), anyString(), anyBoolean())).thenReturn(Collections.emptyList());
        TaskSearchRequest taskSearchRequest = TaskSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .task(TaskSearch.builder().id(Collections.singletonList("some-id")).build()).build();

        assertDoesNotThrow(() -> projectTaskService.search(taskSearchRequest.getTask(), 10, 0, 
                "default", null, false));
    }

    @Test
    @DisplayName("should return project task if search criteria is matched")
    void shouldReturnProjectStaffIfSearchCriteriaIsMatched() throws Exception {
        projectTasks.add(TaskTestBuilder.builder().withTask().build());
        when(projectTaskRepository.find(any(TaskSearch.class), any(Integer.class),
                any(Integer.class), any(String.class), eq(null), any(Boolean.class))).thenReturn(projectTasks);
        TaskSearchRequest taskSearchRequest = TaskSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .task(TaskSearch.builder().id(Collections.singletonList("some-id")).projectId("some-id").build()).build();

        List<Task> projectTasks = projectTaskService.search(taskSearchRequest.getTask(), 10, 0, 
                "default", null, false);

        assertEquals(1, projectTasks.size());
    }

    @Test
    @DisplayName("should return from find by id if search criteria has id only")
    void shouldReturnFromFindByIdIfSearchCriteriaHasIdOnly() throws Exception {
        projectTasks.add(TaskTestBuilder.builder().withTask().build());
        when(projectTaskRepository.findById(anyList(), anyString(), anyBoolean())).thenReturn(projectTasks);
        TaskSearchRequest taskSearchRequest = TaskSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .task(TaskSearch.builder().id(Collections.singletonList("some-id")).build()).build();

        List<Task> projectTasks = projectTaskService.search(taskSearchRequest.getTask(), 10, 0,
                "default", null, false);

        assertEquals(1, projectTasks.size());
    }
}
