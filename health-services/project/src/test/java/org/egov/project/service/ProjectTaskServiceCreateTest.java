package org.egov.project.service;

import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.models.project.TaskRequest;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.TaskRequestTestBuilder;
import org.egov.project.repository.ProjectTaskRepository;
import org.egov.project.service.enrichment.ProjectTaskEnrichmentService;
import org.egov.project.validator.task.PtUniqueSubEntityValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProjectTaskServiceCreateTest {

    @InjectMocks
    private ProjectTaskService projectTaskService;
    @Mock
    private ProjectTaskRepository projectTaskRepository;

    @Mock
    private ProjectConfiguration projectConfiguration;

    private TaskRequest request;

    @Mock
    private ProjectTaskEnrichmentService projectTaskEnrichmentService;

    @Mock
    private PtUniqueSubEntityValidator ptUniqueSubEntityValidator;

    private List<Validator<TaskBulkRequest, Task>> validators;

    @BeforeEach
    void setUp() throws Exception {
        request = TaskRequestTestBuilder.builder().withTask().
                withRequestInfo().build();

        validators = Arrays.asList(ptUniqueSubEntityValidator);
        ReflectionTestUtils.setField(projectTaskService, "validators", validators);
        lenient().when(projectConfiguration.getCreateProjectTaskTopic()).thenReturn("save-project-task-topic");
        lenient().when(projectConfiguration.getUpdateProjectTaskTopic()).thenReturn("update-project-task-topic");
        lenient().when(projectConfiguration.getDeleteProjectTaskTopic()).thenReturn("delete-project-task-topic");
    }

    @Test
    @DisplayName("should save individuals")
    void shouldSaveIndividuals() throws Exception {
        projectTaskService.create(request);

        verify(projectTaskRepository, times(1))
                .save(anyList(), anyString());
    }

    @Test
    @DisplayName("should update individuals")
    void shouldUpdateIndividuals() throws Exception {
        projectTaskService.update(request);

        verify(projectTaskRepository, times(1))
                .save(anyList(), anyString());
    }

    @Test
    @DisplayName("should delete individuals")
    void shouldDeleteIndividuals() throws Exception {
        projectTaskService.delete(request);

        verify(projectTaskRepository, times(1))
                .save(anyList(), anyString());
    }

}
