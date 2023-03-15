package org.egov.project.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.models.project.TaskRequest;
import org.egov.common.service.IdGenService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.TaskRequestTestBuilder;
import org.egov.project.service.enrichment.ProjectTaskEnrichmentService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProjectTaskEnrichmentTest {

    @InjectMocks
    private ProjectTaskEnrichmentService projectTaskEnrichmentService;

    @Mock
    private IdGenService idGenService;

    private TaskBulkRequest request;

    @Mock
    private ProjectConfiguration projectConfiguration;

    @BeforeEach
    void setUp() throws Exception {
        TaskRequest taskRequest = TaskRequestTestBuilder.builder().withTask().
                withRequestInfo().build();
        request = TaskBulkRequest.builder().tasks(Collections.singletonList(taskRequest.getTask()))
                .requestInfo(taskRequest.getRequestInfo()).build();

        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                        any(String.class),
                        eq("project.task.id"), eq(""), anyInt()))
                .thenReturn(idList);

        when(projectConfiguration.getProjectTaskIdFormat()).thenReturn("project.task.id");
    }

    @Test
    @DisplayName("should set task with row version 1")
    void shouldTaskRowVersionSetToOne() throws Exception {
        projectTaskEnrichmentService.create(request.getTasks(),  request);

        Task task = request.getTasks().get(0);
        assertEquals(1, task.getRowVersion());
    }

    @Test
    @DisplayName("should set isDeleted to false for task")
    void shouldTaskIsDeletedFalse() throws Exception {
        projectTaskEnrichmentService.create(request.getTasks(),  request);

        Task task = request.getTasks().get(0);
        assertFalse(task.getIsDeleted());
    }

    @Test
    @DisplayName("should set AuditDetails for task")
    void shouldSetAuditDetailsForTask() throws Exception {
        projectTaskEnrichmentService.create(request.getTasks(),  request);

        Task task = request.getTasks().get(0);
        assertNotNull(task.getAuditDetails().getCreatedBy());
        assertNotNull(task.getAuditDetails().getCreatedTime());
        assertNotNull(task.getAuditDetails().getLastModifiedBy());
        assertNotNull(task.getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should set resource ID for task")
    void shouldSetResourceIDForTask() throws Exception {
        projectTaskEnrichmentService.create(request.getTasks(),  request);

        Task task = request.getTasks().get(0);
        assertNotNull(task.getResources().get(0).getId());
        assertNotNull(task.getResources().get(1).getId());
    }

    @Test
    @DisplayName("should set audit details for task resources")
    void shouldSetAuditDetailsForTaskResources() throws Exception {
        projectTaskEnrichmentService.create(request.getTasks(),  request);

        Task task = request.getTasks().get(0);
        assertNotNull(task.getResources().stream().findAny().get().getAuditDetails().getCreatedBy());
        assertNotNull(task.getResources().stream().findAny().get().getAuditDetails().getCreatedTime());
        assertNotNull(task.getResources().stream().findAny().get().getAuditDetails().getLastModifiedBy());
        assertNotNull(task.getResources().stream().findAny().get().getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should set isDeleted for task resources")
    void shouldSetIsDeletedAndRowVersionsForTaskResources() throws Exception {
        projectTaskEnrichmentService.create(request.getTasks(),  request);

        Task task = request.getTasks().get(0);
        assertFalse(task.getResources().stream().findAny().get().getIsDeleted());
    }

    @Test
    @DisplayName("should set resources task id")
    void shouldSetTaskResourcesTaskId() throws Exception {
        projectTaskEnrichmentService.create(request.getTasks(),  request);

        Task task = request.getTasks().get(0);
        assertNotNull(task.getResources().stream().findAny().get().getTaskId());
    }
}
