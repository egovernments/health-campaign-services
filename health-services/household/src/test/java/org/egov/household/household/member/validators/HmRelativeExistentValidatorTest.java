package org.egov.household.household.member.validators;

import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.Relationship;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class HmRelativeExistentValidatorTest {

    @InjectMocks
    private HmRelativeExistentValidator hmRelativeExistentValidator;

    @Test
    void isInvalidRelativeAndSelf_relativeIdMissing() {
        HouseholdMember householdMember = HouseholdMember.builder()
                .build();
        Relationship relationship = Relationship.builder()
                .build();
        assertTrue(hmRelativeExistentValidator.isRelativeIdMissing(relationship));
        assertTrue(hmRelativeExistentValidator.isInvalidRelativeAndSelf(householdMember, relationship));

        relationship.setRelativeId("relative-id");
        assertFalse(hmRelativeExistentValidator.isInvalidRelativeAndSelf(householdMember, relationship));

        relationship.setRelativeClientReferenceId("relative-client-reference-id");
        relationship.setRelativeId(null);
        assertFalse(hmRelativeExistentValidator.isInvalidRelativeAndSelf(householdMember, relationship));
    }

    @Test
    void isInvalidRelativeAndSelf_selfAndHouseholdMemberIdShouldBeSame() {
        HouseholdMember householdMember = HouseholdMember.builder()
                .id("household-member-id")
                .build();
        Relationship relationship = Relationship.builder()
                .relativeId("relative-id")
                .build();
        assertFalse(hmRelativeExistentValidator.isInvalidRelativeAndSelf(householdMember, relationship));

        relationship.setSelfId("self-household-member-id");
        assertTrue(hmRelativeExistentValidator.isInvalidRelativeAndSelf(householdMember, relationship));

        relationship.setSelfId(householdMember.getId());
        assertFalse(hmRelativeExistentValidator.isInvalidRelativeAndSelf(householdMember, relationship));
    }

    @Test
    void isInvalidRelativeAndSelf_selfAndHouseholdMemberClientReferenceIdShouldBeSame() {
        HouseholdMember householdMember = HouseholdMember.builder()
                .clientReferenceId("household-member-id")
                .build();
        Relationship relationship = Relationship.builder()
                .relativeId("relative-id")
                .build();
        assertFalse(hmRelativeExistentValidator.isInvalidRelativeAndSelf(householdMember, relationship));

        relationship.setSelfClientReferenceId("self-household-member-id");
        assertTrue(hmRelativeExistentValidator.isInvalidRelativeAndSelf(householdMember, relationship));

        relationship.setSelfClientReferenceId(householdMember.getClientReferenceId());
        assertFalse(hmRelativeExistentValidator.isInvalidRelativeAndSelf(householdMember, relationship));
    }

    @Test
    void isInvalidRelativeAndSelf_relativeIdAndHouseholdMemberIdShouldNotSame() {
        HouseholdMember householdMember = HouseholdMember.builder()
                .id("household-member-id")
                .build();
        Relationship relationship = Relationship.builder()
                .relativeId("self-household-member-id")
                .build();
        assertFalse(hmRelativeExistentValidator.isInvalidRelativeAndSelf(householdMember, relationship));

        relationship.setRelativeId(householdMember.getId());
        assertTrue(hmRelativeExistentValidator.isInvalidRelativeAndSelf(householdMember, relationship));
    }

    @Test
    void isInvalidRelativeAndSelf_relativeAndHouseholdMemberClientReferenceIdShouldNotSame() {
        HouseholdMember householdMember = HouseholdMember.builder()
                .clientReferenceId("household-member-id")
                .build();
        Relationship relationship = Relationship.builder()
                .relativeClientReferenceId("self-household-member-id")
                .relativeId("relative-id")
                .build();
        assertFalse(hmRelativeExistentValidator.isInvalidRelativeAndSelf(householdMember, relationship));

        relationship.setRelativeClientReferenceId(householdMember.getClientReferenceId());
        assertTrue(hmRelativeExistentValidator.isInvalidRelativeAndSelf(householdMember, relationship));
    }
}