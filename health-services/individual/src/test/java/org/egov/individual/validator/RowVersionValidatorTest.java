package org.egov.individual.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.individual.helper.IndividualBulkRequestTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.validators.individual.RowVersionValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class RowVersionValidatorTest {

    @InjectMocks
    private RowVersionValidator rowVersionValidator;

    @Mock
    private IndividualRepository individualRepository;

    @Test
    void shouldNotGiveErrorWhenRowVersionMatches() throws InvalidTenantIdException {
        // Arrange
        Individual individual = IndividualTestBuilder.builder()
                .withId("some-id")
                .withRowVersion(2) // same as request
                .build();

        IndividualBulkRequest request = IndividualBulkRequestTestBuilder.builder()
                .withIndividuals(IndividualTestBuilder.builder()
                        .withId("some-id")
                        .withRowVersion(2) // match row version
                        .build())
                .build();

        // Stub repository with non-null return value
        when(individualRepository.findById(any(), any(), any(), anyBoolean()))
                .thenReturn(SearchResponse.<Individual>builder()
                        .totalCount(1L)
                        .response(Collections.singletonList(individual))
                        .build());

        // Act
        Map<Individual, List<Error>> errorDetailsMap = rowVersionValidator.validate(request);
        List<Error> errors = errorDetailsMap.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        // Assert
        assertTrue(errors.isEmpty(), "Expected no errors when row version matches");
    }


    @Test
    void shouldGiveErrorWhenRowVersionDoesNotMatch() throws InvalidTenantIdException {
        // Arrange
        Individual individual = IndividualTestBuilder.builder()
                .withId("some-id")
                .withRowVersion(1) // actual row version in DB
                .build();

        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder()
                .withIndividuals(IndividualTestBuilder.builder()
                        .withId("some-id")
                        .withRowVersion(2) // mismatched row version in request
                        .build())
                .build();

        // Stub with any() to avoid argument mismatch issues
        when(individualRepository.findById(any(), any(), any(), anyBoolean()))
                .thenReturn(SearchResponse.<Individual>builder()
                        .totalCount(1L)
                        .response(Collections.singletonList(individual))
                        .build());

        // Act
        Map<Individual, List<Error>> errorDetailsMap = rowVersionValidator.validate(individualBulkRequest);
        List<Error> errorList = errorDetailsMap.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        // Assert
        assertEquals(1, errorList.size());
        assertEquals("MISMATCHED_ROW_VERSION", errorList.get(0).getErrorCode());
    }

}
