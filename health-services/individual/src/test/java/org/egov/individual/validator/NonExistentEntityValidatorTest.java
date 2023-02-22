package org.egov.individual.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.individual.Constants;
import org.egov.individual.helper.IndividualBulkRequestTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.validators.NonExistentEntityValidator;
import org.egov.individual.web.models.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class NonExistentEntityValidatorTest {

    @InjectMocks
    private NonExistentEntityValidator nonExistentEntityValidator;

    @Mock
    private IndividualRepository individualRepository;

    @Test
    void name() {
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
        Individual individual= IndividualTestBuilder.builder().withId("some-id").withAddress(address).withIdentifiers(identifier).withSkills(skill).build();
        IndividualBulkRequest individualBulkRequest=IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        List<Individual> existingIndividuals=new ArrayList<>();
        existingIndividuals.add(individual);
        lenient().when(individualRepository.findById(anyList(), anyString(), eq(false))).thenReturn(existingIndividuals);
        Constants constants=mock(Constants.class);
        lenient().when(constants.get()).thenReturn("getMethodName");
        assertTrue(nonExistentEntityValidator.validate(individualBulkRequest).isEmpty());

    }
}
