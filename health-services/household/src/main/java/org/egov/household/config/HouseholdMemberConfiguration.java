package org.egov.household.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Component
public class HouseholdMemberConfiguration {

    @Value("${household.member.kafka.create.topic}")
    private String createTopic;

    @Value("${household.member.kafka.update.topic}")
    private String updateTopic;

    @Value("${household.member.kafka.delete.topic}")
    private String deleteTopic;

    @Value("${household.member.consumer.bulk.create.topic}")
    private String bulkCreateTopic;

    @Value("${household.member.consumer.bulk.update.topic}")
    private String bulkUpdateTopic;

    @Value("${household.member.consumer.bulk.delete.topic}")
    private String bulkDeleteTopic;

    @Value("${egov.individual.host}")
    private String individualServiceHost;

    @Value("${egov.individual.search.url}")
    private String individualServiceSearchUrl;

    // When false, the cross-entity existence checks (household / individual / relative) are skipped so a
    // member is not rejected while a referenced parent is still on the persister queue.
    // Default FALSE = disabled (validators off) — chosen for the unified-dev rollout test; set true to enforce.
    @Value("${household.member.relationship.validation:false}")
    private boolean householdMemberRelationshipValidation;

    // BACKWARD COMPATIBILITY GATES. All default FALSE = pre-existing (old-client) behaviour.
    // Set true only once every field build in scope is known to satisfy the rule.

    // Gates the head-of-household rules ADDED after the old baseline: HOUSEHOLD_DOES_NOT_HAVE_A_HEAD,
    // HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD and HOUSEHOLD_HEAD_CANNOT_BE_UNASSIGNED. The original
    // HOUSEHOLD_ALREADY_HAS_HEAD protection is NOT gated - it existed before and stays always on.
    @Value("${household.member.head.strict.validation:false}")
    private boolean householdMemberHeadStrictValidation;

    // Gates HmRelationshipTypeValidator at the predicate. Predicate-level (rather than in-validator) gating is
    // deliberate: it also removes the unconditional MDMS round-trip and the whole-batch throw when the
    // HOUSEHOLD_MEMBER_RELATIONSHIP_TYPES master is absent.
    @Value("${household.member.relationship.type.validation:false}")
    private boolean householdMemberRelationshipTypeValidation;

    // Gates HmRequiredLinkValidator (REQUIRED_LINK_MISSING on create). Old service accepted link-less members.
    @Value("${household.member.required.link.validation:false}")
    private boolean householdMemberRequiredLinkValidation;
}
