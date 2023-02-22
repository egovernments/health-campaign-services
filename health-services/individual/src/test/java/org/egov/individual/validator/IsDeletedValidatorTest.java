package org.egov.individual.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.individual.helper.IndividualBulkRequestTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.validators.IsDeletedValidator;
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
public class IsDeletedValidatorTest {

    @InjectMocks
    private IsDeletedValidator isDeletedValidator;

    @Test
    void shouldNotGiveError_WhenIndividualIsNotDeleted() {
        Individual individual = IndividualTestBuilder.builder().withIsDeleted(false).build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        assertTrue(isDeletedValidator.validate(individualBulkRequest).isEmpty());
    }

    @Test
    void shouldGiveError_WhenIndividualIsDeleted() {
        Individual individual = IndividualTestBuilder.builder().withIsDeleted(true).build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        assertFalse(isDeletedValidator.validate(individualBulkRequest).isEmpty());
    }
}
