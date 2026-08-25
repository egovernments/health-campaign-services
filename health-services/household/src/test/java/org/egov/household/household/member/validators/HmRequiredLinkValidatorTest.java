package org.egov.household.household.member.validators;

import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.models.household.Relationship;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmRequiredLinkValidatorTest {

    private final HmRequiredLinkValidator validator = new HmRequiredLinkValidator();

    @BeforeEach
    void enableRelationshipCheck() {
        // Only the relationship check is flagged; the three original link checks are always on.
        ReflectionTestUtils.setField(validator, "relationshipLinkEnabled", true);
    }

    @Test
    void shouldReportButNotRejectRelationshipWhenDisabled() {
        ReflectionTestUtils.setField(validator, "relationshipLinkEnabled", false);
        HouseholdMember member = memberWithRequiredLinks();
        member.setMemberRelationships(Collections.singletonList(
                Relationship.builder().relationshipType("PARENT").build()));

        assertTrue(validator.validate(request(member)).isEmpty());
    }

    @Test
    void shouldRejectRelationshipWithoutEitherRelativeKey() {
        Relationship invalidRelationship = Relationship.builder()
                .relationshipType("PARENT")
                .build();
        HouseholdMember member = memberWithRequiredLinks();
        member.setMemberRelationships(Collections.singletonList(invalidRelationship));

        Map<HouseholdMember, List<Error>> errors = validator.validate(request(member));

        assertEquals(1, errors.size());
        assertTrue(errors.get(member).get(0).getErrorMessage()
                .contains("memberRelationships[0].relativeId/relativeClientReferenceId"));
    }

    @Test
    void shouldAllowRelationshipUsingOfflineClientReferenceKey() {
        Relationship relationship = Relationship.builder()
                .relationshipType("PARENT")
                .relativeClientReferenceId("device-relative-1")
                .build();
        HouseholdMember member = memberWithRequiredLinks();
        member.setMemberRelationships(Collections.singletonList(relationship));

        assertTrue(validator.validate(request(member)).isEmpty());
    }

    private static HouseholdMember memberWithRequiredLinks() {
        return HouseholdMember.builder()
                .clientReferenceId("device-member-1")
                .householdClientReferenceId("device-household-1")
                .individualClientReferenceId("device-individual-1")
                .build();
    }

    private static HouseholdMemberBulkRequest request(HouseholdMember member) {
        return HouseholdMemberBulkRequest.builder()
                .householdMembers(Collections.singletonList(member)).build();
    }
}
