package org.egov.project.service;

import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.ProjectBeneficiary;
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

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProjectBeneficiaryEnrichmentServiceUpdateTest {

    private final String HOUSEHOLD_RESPONSE_FILE_NAME = "/responses/mdms-household-response.json";

    private final String INDIVIDUAL_RESPONSE_FILE_NAME = "/responses/mdms-individual-response.json";


    @InjectMocks
    private ProjectBeneficiaryEnrichmentService projectBeneficiaryEnrichmentService;

    @Mock
    private IdGenService idGenService;

    @Mock
    private ProjectBeneficiaryRepository projectBeneficiaryRepository;

    @Mock
    private ProjectConfiguration projectConfiguration;

    private BeneficiaryBulkRequest request;

    private List<String> projectBeneficiaryIds;

    @BeforeEach
    void setUp() throws Exception {
        request = BeneficiaryBulkRequestTestBuilder.builder()
                .withOneProjectBeneficiary()
                .build();
        projectBeneficiaryIds = request.getProjectBeneficiaries().stream().map(ProjectBeneficiary::getId)
                .collect(Collectors.toList());

        lenient().when(projectConfiguration.getUpdateProjectBeneficiaryTopic()).thenReturn("update-topic");
    }

    private void mockFindById() {
        lenient().when(projectBeneficiaryRepository.findById(
                eq(projectBeneficiaryIds),
                eq(false),
                anyString())
        ).thenReturn(request.getProjectBeneficiaries());
    }

    @Test
    @DisplayName("should update the lastModifiedTime in the result")
    void shouldUpdateTheLastModifiedTimeInTheResult() throws Exception {
        Long time = request.getProjectBeneficiaries().get(0).getAuditDetails().getLastModifiedTime();
        mockFindById();

        projectBeneficiaryEnrichmentService.update(request.getProjectBeneficiaries(), request);

        assertNotEquals(time, request.getProjectBeneficiaries().get(0).getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should update the row version in the result")
    void shouldUpdateTheRowVersionInTheResult() throws Exception {
        Integer rowVersion = request.getProjectBeneficiaries().get(0).getRowVersion();
        mockFindById();

        projectBeneficiaryEnrichmentService.update(request.getProjectBeneficiaries(), request);

        assertEquals(rowVersion, request.getProjectBeneficiaries().get(0).getRowVersion() - 1);
    }

    @Test
    @DisplayName("should fetch existing records using id")
    void shouldFetchExistingRecordsUsingId() throws Exception {
        mockFindById();
        projectBeneficiaryEnrichmentService.update(request.getProjectBeneficiaries(), request);
        verify(projectBeneficiaryRepository, times(1)).findById(anyList(), eq(false), anyString());
    }

    @Test
    @DisplayName("should enrich ids from existing entities")
    void shouldEnrichIdsFromExistingEntities() throws Exception {
        mockFindById();

        BeneficiaryBulkRequest requestWithoutClientReferenceId = BeneficiaryBulkRequestTestBuilder.builder()
                .withOneProjectBeneficiary()
                .build();
        requestWithoutClientReferenceId.getProjectBeneficiaries().get(0).setClientReferenceId(null);

        projectBeneficiaryEnrichmentService.update(request.getProjectBeneficiaries(), request);
        assertNotNull(request.getProjectBeneficiaries().get(0).getClientReferenceId());
    }
}
