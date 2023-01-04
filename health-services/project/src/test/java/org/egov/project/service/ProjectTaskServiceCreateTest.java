package org.egov.project.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.service.IdGenService;
import org.egov.project.helper.TaskRequestTestBuilder;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.web.models.Task;
import org.egov.project.web.models.TaskRequest;
import org.egov.tracer.model.CustomException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTaskServiceCreateTest {

    @InjectMocks
    private ProjectTaskService projectTaskService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private IdGenService idGenService;

    private TaskRequest request;
    @BeforeEach
    void setUp() throws Exception {
        request = TaskRequestTestBuilder.builder().withTask().
                withRequestInfo().withApiOperationUpdate().build();
        List<String> validIds = new ArrayList<>();
        validIds.add("some-id");
        when(projectRepository.validateIds(anyList(), anyString())).thenReturn(validIds);

        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                        any(String.class),
                        eq("project.task.id"), eq(""), anyInt()))
                .thenReturn(idList);
    }

    @Test
    @DisplayName("should set task with row version 1")
    void shouldTaskRowVersionSetToOne() throws Exception {
        List<Task> tasks = projectTaskService.create(request);

        assertEquals(tasks.stream().findAny().get().getRowVersion(), 1);
    }

    @Test
    @DisplayName("should set isDeleted to false for task")
    void shouldTaskIsDeletedFalse() throws Exception {
        List<Task> tasks = projectTaskService.create(request);

        assertFalse(tasks.stream().findAny().get().getIsDeleted());
    }

    @Test
    @DisplayName("should set AuditDetails for task")
    void shouldSetAuditDetailsForTask() throws Exception {
        List<Task> tasks = projectTaskService.create(request);

        assertNotNull(tasks.stream().findAny().get().getAuditDetails().getCreatedBy());
        assertNotNull(tasks.stream().findAny().get().getAuditDetails().getCreatedTime());
        assertNotNull(tasks.stream().findAny().get().getAuditDetails().getLastModifiedBy());
        assertNotNull(tasks.stream().findAny().get().getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should throw exception if projectId does not exist")
    void shouldThrowExceptionIfProjectIdDoesNotExist() {
        when(projectRepository.validateIds(anyList(), anyString())).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> projectTaskService.create(request));
        verify(projectRepository, times(1)).validateIds(anyList(), anyString());
    }
}
