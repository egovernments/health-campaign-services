package org.egov.individual.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.individual.helper.IndividualBulkRequestTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.validators.UniqueSubEntityValidator;
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
public class UniqueSubEntityValidatorTest {

    @InjectMocks
    private UniqueSubEntityValidator uniqueSubEntityValidator;

    @Test
    void shouldGiveError_WhenAddressIsDuplicate() {
        Address firstAddress = Address.builder()
                .id("some-Id")
                .city("some-city")
                .tenantId("some-tenant-id")
                .type(AddressType.PERMANENT)
                .isDeleted(false)
                .build();
        Address secondAddress = Address.builder()
                .id("some-Id")
                .city("some-city")
                .tenantId("some-tenant-id")
                .type(AddressType.PERMANENT)
                .isDeleted(false)
                .build();
        Individual individual = IndividualTestBuilder.builder().withAddress(firstAddress, secondAddress).build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        errorDetailsMap = uniqueSubEntityValidator.validate(individualBulkRequest);
        List<Error> errorList = errorDetailsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        assertEquals("DUPLICATE_SUB_ENTITY", errorList.get(0).getErrorCode());
    }

    @Test
    void shouldNotGiveError_WhenAddressIsUnique() {
        Address firstAddress = Address.builder().id("some-Id").city("some-city").tenantId("some-tenant-id").type(AddressType.PERMANENT).isDeleted(false).build();
        Address secondAddress = Address.builder().id("some-other-Id").city("some-city").tenantId("some-tenant-id").type(AddressType.PERMANENT).isDeleted(false).build();
        Individual individual = IndividualTestBuilder.builder().withAddress(firstAddress, secondAddress).build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        assertTrue(uniqueSubEntityValidator.validate(individualBulkRequest).isEmpty());
    }

    @Test
    void shouldGiveError_IfIdentifierTypeIsDuplicate() {
        Identifier firstIdentifier = Identifier.builder()
                .identifierType("SYSTEM_GENERATED")
                .identifierId("some-identifier-id")
                .isDeleted(false)
                .build();
        Identifier anotherIdentifier = Identifier.builder()
                .identifierType("SYSTEM_GENERATED")
                .identifierId("some-identifier-id")
                .isDeleted(false)
                .build();
        Individual individual = IndividualTestBuilder.builder().withIdentifiers(firstIdentifier, anotherIdentifier).build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        errorDetailsMap = uniqueSubEntityValidator.validate(individualBulkRequest);
        List<Error> errorList = errorDetailsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        assertEquals("DUPLICATE_SUB_ENTITY", errorList.get(0).getErrorCode());

    }

    @Test
    void shouldNotGiveError_IfIdentifierTypeIsUnique() {
        Identifier firstIdentifier = Identifier.builder()
                .identifierType("SYSTEM_GENERATED")
                .identifierId("some-identifier-id")
                .isDeleted(false)
                .build();
        Identifier anotherIdentifier = Identifier.builder()
                .identifierType("MANUALLY_GENERATED")
                .identifierId("some-identifier-id")
                .isDeleted(false)
                .build();
        Individual individual = IndividualTestBuilder.builder().withIdentifiers(firstIdentifier, anotherIdentifier).build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        assertTrue(uniqueSubEntityValidator.validate(individualBulkRequest).isEmpty());
    }

    @Test
    void shouldGiveError_WhenSkillIsDuplicate() {
        Skill skill = Skill.builder().id("some-id").type("type").experience("exp").level("lvl").build();
        Skill anotherSkill = Skill.builder().id("some-id").type("type").experience("exp").level("lvl").build();
        Individual individual = IndividualTestBuilder.builder().withSkills(skill, anotherSkill).build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        errorDetailsMap = uniqueSubEntityValidator.validate(individualBulkRequest);
        List<Error> errorList = errorDetailsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        assertEquals("DUPLICATE_SUB_ENTITY", errorList.get(0).getErrorCode());
    }

    @Test
    void shouldNotGiveError_WhenSkillIsUnique() {
        Skill skill = Skill.builder().id("some-id").type("type-one").experience("exp").level("lvl").build();
        Skill anotherSkill = Skill.builder().id("some-other-id").type("type-two").experience("exp").level("lvl").build();
        Individual individual = IndividualTestBuilder.builder().withSkills(skill, anotherSkill).build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        assertTrue(uniqueSubEntityValidator.validate(individualBulkRequest).isEmpty());
    }
}
