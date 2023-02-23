package org.egov.individual.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.individual.helper.IndividualBulkRequestTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.validators.UniqueEntityValidator;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class UniqueEntityValidatorTest {

    @InjectMocks
    private UniqueEntityValidator uniqueEntityValidator;

    @Test
    void shouldGiveError_WhenIndividualsWithDuplicateIdsPresent() {
        Individual firstIndividual = IndividualTestBuilder.builder().withId("some-ID").build();
        Individual secondIndividual = IndividualTestBuilder.builder().withId("some-ID").build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(firstIndividual, secondIndividual).build();
        assertFalse(uniqueEntityValidator.validate(individualBulkRequest).isEmpty());
    }

    @Test
    void shouldNotGiveError_WhenIndividualsWithUniqueIdsPresent() {
        Individual firstIndividual = IndividualTestBuilder.builder().withId("some-ID").build();
        Individual secondIndividual = IndividualTestBuilder.builder().withId("some-other-ID").build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(firstIndividual, secondIndividual).build();
        assertTrue(uniqueEntityValidator.validate(individualBulkRequest).isEmpty());
    }

}
