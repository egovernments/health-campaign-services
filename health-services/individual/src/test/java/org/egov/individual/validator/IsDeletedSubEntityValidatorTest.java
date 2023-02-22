package org.egov.individual.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.individual.helper.IndividualBulkRequestTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.validators.IsDeletedSubEntityValidator;
import org.egov.individual.web.models.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class IsDeletedSubEntityValidatorTest {

    @InjectMocks
    IsDeletedSubEntityValidator isDeletedSubEntityValidator;

    @Test
    void shouldNotGiveError_WhenSubEntityIdentifierIsNotDeleted() {
        Individual individual = IndividualTestBuilder.builder().withIdentifiers().build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        assertTrue(isDeletedSubEntityValidator.validate(individualBulkRequest).isEmpty());
    }

    @Test
    void shouldGiveError_WhenSubEntityIdentifierIsDeleted() {
        Identifier identifier = Identifier.builder()
                .identifierType("SYSTEM_GENERATED")
                .identifierId("some-identifier-id")
                .isDeleted(true)
                .build();
        Individual individual = IndividualTestBuilder.builder().withIdentifiers(identifier).build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        assertFalse(isDeletedSubEntityValidator.validate(individualBulkRequest).isEmpty());

    }

    @Test
    void shouldNotGiveError_WhenSubEntityAddressIsNotDeleted() {
        Individual individual = IndividualTestBuilder.builder().withAddress().build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        assertTrue(isDeletedSubEntityValidator.validate(individualBulkRequest).isEmpty());
    }

    @Test
    void shouldGiveError_WhenSubEntityAddressIsDeleted() {
        Address address = Address.builder()
                .city("some-city")
                .tenantId("some-tenant-id")
                .type(AddressType.PERMANENT)
                .isDeleted(true)
                .build();
        Individual individual = IndividualTestBuilder.builder().withAddress(address).build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        assertFalse(isDeletedSubEntityValidator.validate(individualBulkRequest).isEmpty());

    }

    @Test
    void shouldNotGiveError_WhenSubEntitySkillsIsNotDeleted() {
        Skill skill = Skill.builder().type("type").experience("exp").level("lvl").isDeleted(false).build();
        Individual individual = IndividualTestBuilder.builder().withSkills(skill).build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        assertTrue(isDeletedSubEntityValidator.validate(individualBulkRequest).isEmpty());
    }

    @Test
    void shouldGiveError_WhenSubEntitySkillsIsDeleted() {
        Skill skill = Skill.builder().type("type").experience("exp").level("lvl").isDeleted(true).build();
        Individual individual = IndividualTestBuilder.builder().withSkills(skill).build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        assertFalse(isDeletedSubEntityValidator.validate(individualBulkRequest).isEmpty());
    }
}
