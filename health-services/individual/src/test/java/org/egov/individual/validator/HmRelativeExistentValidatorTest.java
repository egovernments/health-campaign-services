package org.egov.individual.validator;

import java.util.stream.Stream;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.Relationship;
import org.egov.individual.validators.member.HmRelativeExistentValidator;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class HmRelativeExistentValidatorTest {

    @InjectMocks
    private HmRelativeExistentValidator hmRelativeExistentValidator;

    private static Stream<Arguments> invalidRelativeAndSelfScenarios() {
        return Stream.of(
                // same household ID and selfId → invalid = false
                Arguments.of("household-member-id", null, "relative-id", null, "household-member-id", null, false, false),

                // different selfId → invalid = true
                Arguments.of("household-member-id", null, "relative-id", null, "self-household-member-id", null, false, true),

                // same clientReferenceId and selfClientReferenceId → invalid = false
                Arguments.of(null, "household-member-id", "relative-id", null, null, "household-member-id", false, false),

                // different selfClientReferenceId → invalid = true
                Arguments.of(null, "household-member-id", "relative-id", null, null, "self-household-member-id", false, true),

                // relativeId same as household id → invalid = true
                Arguments.of("household-member-id", null, "household-member-id", null, null, null, false, true),

                // relativeClientReferenceId same as household member client ref → invalid = true
                Arguments.of(null, "household-member-id", "relative-id", "household-member-id", null, null, false, true),

                // relativeClientReferenceId different → valid
                Arguments.of(null, "household-member-id", "relative-id", "self-household-member-id", null, null, false, false)
        );
    }


    @ParameterizedTest
    @MethodSource("invalidRelativeAndSelfScenarios")
    void testIsInvalidRelativeAndSelf(
            String householdMemberId,
            String householdMemberClientRefId,
            String relativeId,
            String relativeClientRefId,
            String selfId,
            String selfClientRefId,
            boolean isRelativeIdMissing,
            boolean expectedInvalid
    ) {
        HouseholdMember householdMember = HouseholdMember.builder()
                .id(householdMemberId)
                .clientReferenceId(householdMemberClientRefId)
                .build();

        Relationship relationship = Relationship.builder()
                .relativeId(relativeId)
                .relativeClientReferenceId(relativeClientRefId)
                .selfId(selfId)
                .selfClientReferenceId(selfClientRefId)
                .build();

        if (isRelativeIdMissing) {
            assertTrue(hmRelativeExistentValidator.isRelativeIdMissing(relationship));
        }

        boolean actualInvalid = hmRelativeExistentValidator.isInvalidRelativeAndSelf(householdMember, relationship);
        assertEquals(expectedInvalid, actualInvalid);
    }
}