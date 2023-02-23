package org.egov.individual.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.individual.helper.IndividualBulkRequestTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.validators.RowVersionValidator;
import org.egov.individual.web.models.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class RowVersionValidatorTest {

    @InjectMocks
    private RowVersionValidator rowVersionValidator;

    @Mock
    private IndividualRepository individualRepository;

    @Test
    void shouldNotGiveError_WhenRowVersionMatches() {
        Address address = Address.builder()
                .id("some-Id")
                .city("some-city")
                .tenantId("some-tenant-id")
                .type(AddressType.PERMANENT)
                .isDeleted(false)
                .build();
        Identifier identifier = Identifier.builder()
                .identifierType("SYSTEM_GENERATED")
                .identifierId("some-identifier-id")
                .isDeleted(true)
                .build();
        Skill skill = Skill.builder().type("type").experience("exp").level("lvl").isDeleted(false).build();
        Individual individual = IndividualTestBuilder.builder().withId("some-Id").withAddress(address).withIdentifiers(identifier).withSkills(skill).withRowVersion(1).build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        List<Individual> existingIndividuals = new ArrayList<>();
        existingIndividuals.add(individual);
        lenient().when(individualRepository.findById(anyList(), anyString(), eq(false))).thenReturn(existingIndividuals);
        assertTrue(rowVersionValidator.validate(individualBulkRequest).isEmpty());

    }
}
