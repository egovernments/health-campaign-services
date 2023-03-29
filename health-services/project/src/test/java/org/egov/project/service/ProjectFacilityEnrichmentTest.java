package org.egov.project.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.common.service.IdGenService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.ProjectFacilityBulkRequestTestBuilder;
import org.egov.project.service.enrichment.ProjectFacilityEnrichmentService;
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
class ProjectFacilityEnrichmentTest {

    @InjectMocks
    private ProjectFacilityEnrichmentService enrichmentService;

    @Mock
    private IdGenService idGenService;
    @Mock
    private ProjectConfiguration projectConfiguration;

    private ProjectFacilityBulkRequest request;

    @BeforeEach
    void setUp() throws Exception {
        request = ProjectFacilityBulkRequestTestBuilder.builder().withOneProjectFacility()
                .withRequestInfo().build();

        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                        any(String.class),
                        eq("project.facility.id"), eq(""), anyInt()))
                .thenReturn(idList);

        lenient().when(projectConfiguration.getProjectFacilityIdFormat()).thenReturn("project.facility.id");
    }

    @Test
    @DisplayName("should set ProjectFacility with row version 1")
    void shouldProjectFacilityRowVersionSetToOne() throws Exception {
        enrichmentService.create(request.getProjectFacilities(),  request);

        ProjectFacility ProjectFacility = request.getProjectFacilities().get(0);
        assertEquals(1, ProjectFacility.getRowVersion());
    }

    @Test
    @DisplayName("should set isDeleted to false for ProjectFacility")
    void shouldProjectFacilityIsDeletedFalse() throws Exception {
        enrichmentService.create(request.getProjectFacilities(),  request);

        ProjectFacility ProjectFacility = request.getProjectFacilities().get(0);
        assertFalse(ProjectFacility.getIsDeleted());
    }

    @Test
    @DisplayName("should increase row version by one")
    void shouldProjectFacilityRowVersionIncrement() throws Exception {
        enrichmentService.update(request.getProjectFacilities(),  request);

        ProjectFacility ProjectFacility = request.getProjectFacilities().get(0);
        assertEquals(ProjectFacility.getRowVersion(), 2);
    }

    @Test
    @DisplayName("should set AuditDetails for ProjectFacility")
    void shouldSetAuditDetailsForProjectFacility() throws Exception {
        enrichmentService.create(request.getProjectFacilities(),  request);

        ProjectFacility ProjectFacility = request.getProjectFacilities().get(0);
        assertNotNull(ProjectFacility.getAuditDetails().getCreatedBy());
        assertNotNull(ProjectFacility.getAuditDetails().getCreatedTime());
        assertNotNull(ProjectFacility.getAuditDetails().getLastModifiedBy());
        assertNotNull(ProjectFacility.getAuditDetails().getLastModifiedTime());
    }
}
