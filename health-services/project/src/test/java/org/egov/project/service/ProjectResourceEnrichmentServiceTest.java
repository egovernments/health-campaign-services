package org.egov.project.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.common.service.IdGenService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.ProjectResourceBulkRequestTestBuilder;
import org.egov.project.service.enrichment.ProjectResourceEnrichmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ProjectResourceEnrichmentServiceTest {

    @InjectMocks
    private ProjectResourceEnrichmentService enrichmentService;

    @Mock
    private ProjectResourceBulkRequest request;

    @Mock
    private IdGenService idGenService;

    @Mock
    private ProjectConfiguration configuration;

    @BeforeEach
    void setUp() throws Exception {
        request = ProjectResourceBulkRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();

        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                        any(String.class),
                        eq("project.resource.id"), eq(""), anyInt()))
                .thenReturn(idList);

        lenient().when(configuration.getProjectResourceIdFormat()).thenReturn("project.resource.id");
    }

    @Test
    @DisplayName("should set ProjectResource with row version 1")
    void shouldProjectResourceRowVersionSetToOne() throws Exception {
        enrichmentService.create(request.getProjectResource(),  request);

        ProjectResource projectResource = request.getProjectResource().get(0);
        assertEquals(1, projectResource.getRowVersion());
    }

    @Test
    @DisplayName("should set isDeleted to false for ProjectResource")
    void shouldProjectResourceIsDeletedFalse() throws Exception {
        enrichmentService.create(request.getProjectResource(),  request);

        ProjectResource projectResource = request.getProjectResource().get(0);
        assertFalse(projectResource.getIsDeleted());
    }

    @Test
    @DisplayName("should increase row version by one")
    void shouldProjectResourceRowVersionIncrement() throws Exception {
        enrichmentService.update(request.getProjectResource(),  request);

        ProjectResource projectResource = request.getProjectResource().get(0);
        assertEquals(projectResource.getRowVersion(), 2);
    }

    @Test
    @DisplayName("should set AuditDetails for ProjectResource")
    void shouldSetAuditDetailsForProjectResource() throws Exception {
        enrichmentService.create(request.getProjectResource(),  request);

        ProjectResource projectResource = request.getProjectResource().get(0);
        assertNotNull(projectResource.getAuditDetails().getCreatedBy());
        assertNotNull(projectResource.getAuditDetails().getCreatedTime());
        assertNotNull(projectResource.getAuditDetails().getLastModifiedBy());
        assertNotNull(projectResource.getAuditDetails().getLastModifiedTime());
    }

}
