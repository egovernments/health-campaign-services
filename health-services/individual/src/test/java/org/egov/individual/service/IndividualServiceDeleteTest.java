package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.Validator;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.helper.IndividualRequestTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;
import org.egov.individual.web.models.IndividualRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndividualServiceDeleteTest {

    @InjectMocks
    private IndividualService individualService;

    @Mock
    private IdGenService idGenService;

    @Mock
    private IndividualRepository individualRepository;

    @Mock
    private NullIdValidator nullIdValidator;

    @Mock
    private NonExistentEntityValidator nonExistentEntityValidator;

    private List<Validator<IndividualBulkRequest, Individual>> validators;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private IndividualProperties properties;


    @BeforeEach
    void setUp() {
        validators = Arrays.asList(nullIdValidator, nonExistentEntityValidator);
        ReflectionTestUtils.setField(individualService, "validators", validators);
        ReflectionTestUtils.setField(individualService, "isApplicableForDelete",
                (Predicate<Validator<IndividualBulkRequest, Individual>>) validator ->
                validator.getClass().equals(NullIdValidator.class)
                        || validator.getClass().equals(NonExistentEntityValidator.class));
        when(properties.getDeleteIndividualTopic()).thenReturn("delete-topic");
    }

    @Test
    @DisplayName("should delete the individual and related entities")
    void shouldDeleteTheIndividualAndRelatedEntities() {
        Individual requestIndividual = IndividualTestBuilder.builder()
                .withId()
                .withClientReferenceId()
                .withRowVersion()
                .withIdentifiers()
                .withAddress()
                .withIsDeleted(true)
                .build();
        IndividualRequest request = IndividualRequestTestBuilder.builder()
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

        List<Individual> result = individualService.delete(request);

        assertEquals(requestIndividual.getRowVersion(),
                result.stream().findFirst().get().getRowVersion());
        assertNotNull(result.stream().findFirst().get().getAuditDetails());
        assertTrue(result.stream().findFirst().get().getAddress().stream().findFirst().get().getIsDeleted());
        assertTrue(result.stream().findFirst().get().getIdentifiers().stream().findFirst().get().getIsDeleted());
        assertEquals(2, result.stream().findFirst().get().getRowVersion());
        verify(individualRepository, times(1)).save(anyList(), anyString());
    }
}
