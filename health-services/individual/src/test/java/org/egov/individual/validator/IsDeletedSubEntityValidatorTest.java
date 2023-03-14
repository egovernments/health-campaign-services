package org.egov.individual.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.individual.helper.IndividualBulkRequestTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.validators.IsDeletedSubEntityValidator;
import org.egov.individual.web.models.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        errorDetailsMap = isDeletedSubEntityValidator.validate(individualBulkRequest);
        List<Error> errorList = errorDetailsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        assertEquals("IS_DELETED_TRUE_SUB_ENTITY", errorList.get(0).getErrorCode());


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
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        errorDetailsMap = isDeletedSubEntityValidator.validate(individualBulkRequest);
        List<Error> errorList = errorDetailsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        assertEquals("IS_DELETED_TRUE_SUB_ENTITY", errorList.get(0).getErrorCode());

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
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        errorDetailsMap = isDeletedSubEntityValidator.validate(individualBulkRequest);
        List<Error> errorList = errorDetailsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        assertEquals("IS_DELETED_TRUE_SUB_ENTITY", errorList.get(0).getErrorCode());

    }
}
