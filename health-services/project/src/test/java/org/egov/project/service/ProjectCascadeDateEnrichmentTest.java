package org.egov.project.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectRequest;
import org.egov.common.producer.Producer;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.service.enrichment.ProjectEnrichment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProjectCascadeDateEnrichmentTest {

    private static final String DATE_TOPIC = "update-project-date";
    private static final String FULL_TOPIC = "update-project";
    private static final String TENANT_ID = "demo";

    @InjectMocks
    private ProjectEnrichment projectEnrichment;

    @Mock
    private Producer producer;
    @Mock
    private ProjectConfiguration projectConfiguration;

    private Project request;
    private Project projectFromDb;

    @BeforeEach
    void setUp() {
        lenient().when(projectConfiguration.getKafkaBatchSize()).thenReturn(100);
        lenient().when(projectConfiguration.getUpdateProjectDateTopic()).thenReturn(DATE_TOPIC);
        lenient().when(projectConfiguration.getUpdateProjectTopic()).thenReturn(FULL_TOPIC);

        request = Project.builder().id("root").tenantId(TENANT_ID).startDate(200L).endDate(400L).build();

        List<Project> descendants = Arrays.asList(
                Project.builder().id("d1").tenantId(TENANT_ID).startDate(1L).endDate(2L).build(),
                Project.builder().id("d2").tenantId(TENANT_ID).startDate(1L).endDate(2L).build());

        projectFromDb = Project.builder().id("root").tenantId(TENANT_ID).startDate(100L).endDate(500L).build();
        projectFromDb.setDescendants(descendants);
    }

    @Test
    @DisplayName("cascade publishes descendants to the date-only persister topic")
    void cascadePushesToDateTopic() {
        projectEnrichment.enrichProjectCascadingDatesOnUpdate(request, projectFromDb, RequestInfo.builder().build());

        verify(producer).push(eq(TENANT_ID), eq(DATE_TOPIC), any(ProjectRequest.class));
    }

    @Test
    @DisplayName("cascade never publishes descendants to the full update topic")
    void cascadeDoesNotPushToFullUpdateTopic() {
        projectEnrichment.enrichProjectCascadingDatesOnUpdate(request, projectFromDb, RequestInfo.builder().build());

        verify(producer, never()).push(any(), eq(FULL_TOPIC), any());
    }

    @Test
    @DisplayName("descendants take the requested start and end dates")
    void descendantsTakeRequestedDates() {
        ArgumentCaptor<ProjectRequest> captor = ArgumentCaptor.forClass(ProjectRequest.class);

        projectEnrichment.enrichProjectCascadingDatesOnUpdate(request, projectFromDb, RequestInfo.builder().build());

        verify(producer).push(eq(TENANT_ID), eq(DATE_TOPIC), captor.capture());
        List<Project> pushed = captor.getValue().getProjects();
        assertEquals(2, pushed.size());
        pushed.forEach(p -> {
            assertEquals(200L, p.getStartDate());
            assertEquals(400L, p.getEndDate());
        });
    }
}
