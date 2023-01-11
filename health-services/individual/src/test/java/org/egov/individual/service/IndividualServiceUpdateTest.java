package org.egov.individual.service;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.service.IdGenService;
import org.egov.individual.helper.IndividualRequestTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.ApiOperation;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.egov.tracer.model.CustomException;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndividualServiceUpdateTest {

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
    @DisplayName("should throw exception if ids are null")
    void shouldThrowExceptionIfIdsAreNull() {
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withApiOperation(ApiOperation.UPDATE)
                .withRequestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .withIndividuals(IndividualTestBuilder.builder()
                        .withName()
                        .withTenantId()
                        .withAddress()
                        .build())
                .build();

        assertThrows(CustomException.class, () -> individualService.update(request));
    }

    @Test
    @DisplayName("should throw exception if entities do not exist in db")
    void shouldThrowExceptionIfEntitiesDoNotExistInDb() {
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withApiOperation(ApiOperation.UPDATE)
                .withRequestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .withIndividuals(IndividualTestBuilder.builder()
                        .withClientReferenceId()
                        .withName()
                        .withTenantId()
                        .withAddress()
                        .build())
                .build();
        when(individualRepository.findById(anyList(), eq("clientReferenceId"), eq(Boolean.FALSE)))
                .thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> individualService.update(request));
    }

    @Test
    @DisplayName("should check row versions if entities are valid")
    void shouldCheckRowVersionsIfEntitiesAreValid() {
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withApiOperation(ApiOperation.UPDATE)
                .withRequestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .withIndividuals(IndividualTestBuilder.builder()
                        .withClientReferenceId()
                        .withName()
                        .withTenantId()
                        .withAddress()
                        .withRowVersion()
                        .build())
                .build();
        List<Individual> individualsInDb = new ArrayList<>();
        individualsInDb.add(IndividualTestBuilder.builder()
                .withClientReferenceId()
                .withId()
                .withName()
                .withTenantId()
                .withAddress()
                .withRowVersion()
                .withAuditDetails()
                .build());
        when(individualRepository.findById(anyList(), eq("clientReferenceId"), eq(Boolean.FALSE)))
                .thenReturn(individualsInDb);

        assertDoesNotThrow(() -> individualService.update(request));
    }

    @Test
    @DisplayName("should throw exception if row versions do not match")
    void shouldThrowExceptionIfRowVersionsDoNotMatch() {
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withApiOperation(ApiOperation.UPDATE)
                .withRequestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .withIndividuals(IndividualTestBuilder.builder()
                        .withClientReferenceId()
                        .withName()
                        .withTenantId()
                        .withAddress()
                        .withRowVersion(2)
                        .build())
                .build();
        List<Individual> individualsInDb = new ArrayList<>();
        individualsInDb.add(IndividualTestBuilder.builder()
                .withClientReferenceId()
                .withId()
                .withName()
                .withTenantId()
                .withAddress()
                .withRowVersion()
                .build());
        when(individualRepository.findById(anyList(), eq("clientReferenceId"), eq(Boolean.FALSE)))
                .thenReturn(individualsInDb);

        assertThrows(CustomException.class, () -> individualService.update(request));
    }

    @Test
    @DisplayName("should enrich for update")
    void shouldEnrichForUpdate() {
        Individual requestIndividual = IndividualTestBuilder.builder()
                .withClientReferenceId()
                .withName("some-new-family-name", "some-new-given-name")
                .withTenantId()
                .withAddress()
                .withRowVersion()
                .build();
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withApiOperation(ApiOperation.UPDATE)
                .withRequestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .withIndividuals(requestIndividual)
                .build();
        List<Individual> individualsInDb = new ArrayList<>();
        individualsInDb.add(IndividualTestBuilder.builder()
                .withClientReferenceId()
                .withId()
                .withName()
                .withTenantId()
                .withAddress()
                .withRowVersion()
                .withAuditDetails()
                .build());
        when(individualRepository.findById(anyList(), eq("clientReferenceId"), eq(Boolean.FALSE)))
                .thenReturn(individualsInDb);

        List<Individual> result = individualService.update(request);

        assertEquals(requestIndividual.getRowVersion(),
                result.stream().findFirst().get().getRowVersion());
        assertNotNull(result.stream().findFirst().get().getAuditDetails());
    }

    @Test
    @DisplayName("should enrich for delete")
    void shouldEnrichForDelete() {
        Individual requestIndividual = IndividualTestBuilder.builder()
                .withClientReferenceId()
                .withName("some-new-family-name", "some-new-given-name")
                .withTenantId()
                .withAddress()
                .withIdentifiers()
                .withRowVersion()
                .withIdentifiers()
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
                .withIdentifiers()
                .withAuditDetails()
                .build());
        when(individualRepository.findById(anyList(), eq("clientReferenceId"), eq(Boolean.FALSE)))
                .thenReturn(individualsInDb);

        List<Individual> result = individualService.update(request);

        assertEquals(requestIndividual.getRowVersion(),
                result.stream().findFirst().get().getRowVersion());
        assertNotNull(result.stream().findFirst().get().getAuditDetails());
        assertTrue(result.stream().findFirst().get().getIsDeleted());
    }

    @Test
    @DisplayName("should save the updated entities")
    void shouldSaveTheUpdatedEntities() {
        Individual requestIndividual = IndividualTestBuilder.builder()
                .withClientReferenceId()
                .withName("some-new-family-name", "some-new-given-name")
                .withTenantId()
                .withAddress()
                .withRowVersion()
                .build();
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withApiOperation(ApiOperation.UPDATE)
                .withRequestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .withIndividuals(requestIndividual)
                .build();
        List<Individual> individualsInDb = new ArrayList<>();
        individualsInDb.add(IndividualTestBuilder.builder()
                .withClientReferenceId()
                .withId()
                .withName()
                .withTenantId()
                .withAddress()
                .withRowVersion()
                .withAuditDetails()
                .build());
        when(individualRepository.findById(anyList(), eq("clientReferenceId"), eq(Boolean.FALSE)))
                .thenReturn(individualsInDb);

        List<Individual> result = individualService.update(request);

        assertEquals(requestIndividual.getRowVersion(),
                result.stream().findFirst().get().getRowVersion());
        assertNotNull(result.stream().findFirst().get().getAuditDetails());
        verify(individualRepository, times(1)).save(anyList(), anyString());
    }

}
