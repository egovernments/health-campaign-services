package org.egov.individual.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.individual.Address;
import org.egov.common.models.individual.AddressType;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.models.individual.Skill;
import org.egov.individual.helper.IndividualBulkRequestTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.validators.individual.NonExistentEntityValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class NonExistentEntityValidatorTest {

    @InjectMocks
    private NonExistentEntityValidator nonExistentEntityValidator;

    @Mock
    private IndividualRepository individualRepository;

    @Test
    void shouldNotGiveErrorWhenEntityExists() throws InvalidTenantIdException {
        Address address = Address.builder()
                .id("some-Id")
                .city("some-city")
                .tenantId("some-tenant-id")
                .type(AddressType.PERMANENT)
                .isDeleted(false)
                .build();
        Identifier identifier = Identifier.builder()
                .id("some-id")
                .identifierType("SYSTEM_GENERATED")
                .identifierId("some-identifier-id")
                .isDeleted(false)
                .build();
        Skill skill = Skill.builder().id("some-id").type("type").experience("exp").level("lvl").isDeleted(false).build();
        Individual individual = IndividualTestBuilder.builder().withId("some-id").withAddress(address).withIdentifiers(identifier).withSkills(skill).build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        List<Individual> existingIndividuals = new ArrayList<>();
        existingIndividuals.add(individual);
        lenient().when(individualRepository.find(any(), any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(
                    SearchResponse.<Individual>builder()
                            .totalCount(Long.valueOf(existingIndividuals.size()))
                            .response(existingIndividuals)
                            .build()
                );
        assertTrue(nonExistentEntityValidator.validate(individualBulkRequest).isEmpty());

    }

    @Test
    void shouldGiveErrorWhenEntityDoesNotExist() throws InvalidTenantIdException {
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder()
                .withIndividuals(IndividualTestBuilder.builder()
                        .withId("some-id")
                        .build())
                .build();
        when(individualRepository.find(any(), any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(SearchResponse.<Individual>builder().build());
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        errorDetailsMap = nonExistentEntityValidator.validate(individualBulkRequest);
        List<Error> errorList = errorDetailsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        assertEquals("NON_EXISTENT_ENTITY", errorList.get(0).getErrorCode());
    }
}
