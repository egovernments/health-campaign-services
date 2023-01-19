package org.egov.individual.service;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.service.IdGenService;
import org.egov.individual.helper.IndividualRequestTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.ApiOperation;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Disabled
class IndividualServiceDeleteTest {

    @InjectMocks
    private IndividualService individualService;

    @Mock
    private IdGenService idGenService;

    @Mock
    private IndividualRepository individualRepository;

    @BeforeEach
    void setUp() {

    }

    @Test
    @DisplayName("should delete the individual and related entities")
    void shouldDeleteTheIndividualAndRelatedEntities() {
        Individual requestIndividual = IndividualTestBuilder.builder()
                .withClientReferenceId()
                .withRowVersion()
                .build();
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withApiOperation(ApiOperation.DELETE)
                .withRequestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .withIndividuals(requestIndividual)
                .build();
        List<Individual> individualsInDb = new ArrayList<>();
        individualsInDb.add(IndividualTestBuilder.builder()
                .withClientReferenceId()
                .withId()
                .withName()
                .withTenantId()
                .withAddressHavingAuditDetails()
                .withIdentifiersHavingAuditDetails()
                .withRowVersion()
                .withAuditDetails()
                .build());
        when(individualRepository.findById(anyList(), eq("clientReferenceId"), eq(Boolean.FALSE)))
                .thenReturn(individualsInDb);

        List<Individual> result = individualService.update(request);

        assertEquals(requestIndividual.getRowVersion(),
                result.stream().findFirst().get().getRowVersion());
        assertNotNull(result.stream().findFirst().get().getAuditDetails());
        assertTrue(result.stream().findFirst().get().getAddress().stream().findFirst().get().getIsDeleted());
        assertTrue(result.stream().findFirst().get().getIdentifiers().stream().findFirst().get().getIsDeleted());
        assertEquals(2, result.stream().findFirst().get().getAddress().stream().findFirst().get().getRowVersion());
        assertEquals(2, result.stream().findFirst().get().getIdentifiers().stream().findFirst().get().getRowVersion());
        verify(individualRepository, times(1)).save(anyList(), anyString());
    }
}
