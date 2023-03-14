package org.egov.project.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.service.IdGenService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.BeneficiaryBulkRequestTestBuilder;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.service.enrichment.ProjectBeneficiaryEnrichmentService;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProjectBeneficiaryEnrichmentServiceCreateTest {

    @InjectMocks
    private ProjectBeneficiaryEnrichmentService projectBeneficiaryEnrichmentService;

    @Mock
    private IdGenService idGenService;

    @Mock
    private ProjectBeneficiaryRepository projectBeneficiaryRepository;

    @Mock
    private ProjectConfiguration projectConfiguration;

    private BeneficiaryBulkRequest request;

    @BeforeEach
    void setUp() throws Exception {
        request = BeneficiaryBulkRequestTestBuilder.builder()
                .withOneProjectBeneficiary()
                .build();
        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                        any(String.class),
                        eq("project.beneficiary.id"), eq(""), anyInt()))
                .thenReturn(idList);
        lenient().when(projectConfiguration.getProjectBeneficiaryIdFormat()).thenReturn("project.beneficiary.id");
    }

    @Test
    @DisplayName("should enrich the formatted id in project beneficiary")
    void shouldEnrichTheFormattedIdInProjectBeneficiary() throws Exception {
        projectBeneficiaryEnrichmentService
                .create(request.getProjectBeneficiaries(), request);

        assertEquals("some-id", request.getProjectBeneficiaries().get(0).getId());
    }

    @Test
    @DisplayName("should call idGenService")
    void shouldCallIdGenService() throws Exception {
        projectBeneficiaryEnrichmentService
                .create(request.getProjectBeneficiaries(), request);

        verify(idGenService, times(1)).getIdList(any(RequestInfo.class),
                any(String.class),
                eq("project.beneficiary.id"), eq(""), anyInt());
    }

    @Test
    @DisplayName("should update audit details before pushing the project beneficiary to kafka")
    void shouldUpdateAuditDetailsBeforePushingTheProjectBeneficiariesToKafka() throws Exception {
        projectBeneficiaryEnrichmentService
                .create(request.getProjectBeneficiaries(), request);

        assertNotNull(request.getProjectBeneficiaries().get(0).getAuditDetails().getCreatedBy());
        assertNotNull(request.getProjectBeneficiaries().get(0).getAuditDetails().getCreatedTime());
        assertNotNull(request.getProjectBeneficiaries().get(0).getAuditDetails().getLastModifiedBy());
        assertNotNull(request.getProjectBeneficiaries().get(0).getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should set row version as 1 and deleted as false")
    void shouldSetRowVersionAs1AndDeletedAsFalse() throws Exception {
        projectBeneficiaryEnrichmentService
                .create(request.getProjectBeneficiaries(), request);

        assertEquals(1, request.getProjectBeneficiaries().get(0).getRowVersion());
        assertFalse(request.getProjectBeneficiaries().get(0).getIsDeleted());
    }
}