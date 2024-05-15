package org.egov.project.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffBulkRequest;
import org.egov.common.service.IdGenService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.ProjectStaffBulkRequestTestBuilder;
import org.egov.project.service.enrichment.ProjectStaffEnrichmentService;
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
class ProjectStaffEnrichmentTest {

    @InjectMocks
    private ProjectStaffEnrichmentService enrichmentService;

    @Mock
    private IdGenService idGenService;
    @Mock
    private ProjectConfiguration projectConfiguration;

    private ProjectStaffBulkRequest request;

    @BeforeEach
    void setUp() throws Exception {
        request = ProjectStaffBulkRequestTestBuilder.builder().withOneProjectStaff()
                .withRequestInfo().build();

        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                        any(String.class),
                        eq("project.staff.id"), eq(""), anyInt()))
                .thenReturn(idList);

        lenient().when(projectConfiguration.getProjectStaffIdFormat()).thenReturn("project.staff.id");
    }

    @Test
    @DisplayName("should set ProjectStaff with row version 1")
    void shouldProjectStaffRowVersionSetToOne() throws Exception {
        enrichmentService.create(request.getProjectStaff(),  request);

        ProjectStaff ProjectStaff = request.getProjectStaff().get(0);
        assertEquals(1, ProjectStaff.getRowVersion());
    }

    @Test
    @DisplayName("should set isDeleted to false for ProjectStaff")
    void shouldProjectStaffIsDeletedFalse() throws Exception {
        enrichmentService.create(request.getProjectStaff(),  request);

        ProjectStaff ProjectStaff = request.getProjectStaff().get(0);
        assertFalse(ProjectStaff.getIsDeleted());
    }

    @Test
    @DisplayName("should increase row version by one")
    void shouldProjectStaffRowVersionIncrement() throws Exception {
        enrichmentService.update(request.getProjectStaff(),  request);

        ProjectStaff ProjectStaff = request.getProjectStaff().get(0);
        assertEquals(ProjectStaff.getRowVersion(), 2);
    }

    @Test
    @DisplayName("should set AuditDetails for ProjectStaff")
    void shouldSetAuditDetailsForProjectStaff() throws Exception {
        enrichmentService.create(request.getProjectStaff(),  request);

        ProjectStaff ProjectStaff = request.getProjectStaff().get(0);
        assertNotNull(ProjectStaff.getAuditDetails().getCreatedBy());
        assertNotNull(ProjectStaff.getAuditDetails().getCreatedTime());
        assertNotNull(ProjectStaff.getAuditDetails().getLastModifiedBy());
        assertNotNull(ProjectStaff.getAuditDetails().getLastModifiedTime());
    }
}
