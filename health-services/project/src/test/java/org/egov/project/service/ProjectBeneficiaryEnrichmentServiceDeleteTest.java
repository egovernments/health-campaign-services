package org.egov.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.service.IdGenService;
import org.egov.project.helper.BeneficiaryBulkRequestTestBuilder;
import org.egov.project.service.enrichment.ProjectBeneficiaryEnrichmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class ProjectBeneficiaryEnrichmentServiceDeleteTest {

    @InjectMocks
    private ProjectBeneficiaryEnrichmentService projectBeneficiaryEnrichmentService;

    @Mock
    private IdGenService idGenService;

    @Mock
    private ObjectMapper objectMapper;

    private BeneficiaryBulkRequest request;


    @BeforeEach
    void setUp() {
        request = BeneficiaryBulkRequestTestBuilder.builder()
                .withOneProjectBeneficiary()
                .build();
    }

    @Test
    @DisplayName("should delete the individual and related entities")
    void shouldDeleteTheIndividualAndRelatedEntities() throws Exception{

        projectBeneficiaryEnrichmentService.delete(request.getProjectBeneficiaries(), request);

        assertNotNull(request.getProjectBeneficiaries().get(0).getAuditDetails());
        assertTrue(request.getProjectBeneficiaries().get(0).getIsDeleted());
        assertEquals(2, request.getProjectBeneficiaries().get(0).getRowVersion());
    }
}
