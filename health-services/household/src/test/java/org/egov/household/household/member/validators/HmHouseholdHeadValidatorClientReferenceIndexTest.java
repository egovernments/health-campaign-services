package org.egov.household.household.member.validators;

import org.egov.common.models.Error;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.household.config.HouseholdMemberConfiguration;
import org.egov.household.repository.HouseholdMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Flag ON: {@code household.member.duplicate.check.by.client.reference.enabled=true}.
 *
 * <p>The flag-OFF path deliberately does not widen a lone both-keys member's lookup to the client-reference
 * column (see {@link HmHouseholdHeadValidatorMixedShapeTest#memberCarryingBothKeysAloneStillIssuesOnlyTheHouseholdIdQuery}),
 * so a stored head written out-of-order with a NULL householdId is invisible to it. The flag builds an
 * {@link ExistingMemberIndex} over BOTH parent-key columns to close exactly that blind spot. This path shipped
 * without test coverage; these tests pin its behaviour before it can be enabled by default.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HmHouseholdHeadValidatorClientReferenceIndexTest {

    private static final String TENANT_ID = "default";

    private static final String HOUSEHOLD_ID_FIELD = "householdId";

    private static final String HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD = "householdClientReferenceId";

    private static final String HOUSEHOLD_ALREADY_HAS_HEAD = "HOUSEHOLD_ALREADY_HAS_HEAD";

    private static final String HOUSEHOLD_DOES_NOT_HAVE_A_HEAD = "HOUSEHOLD_DOES_NOT_HAVE_A_HEAD";

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Mock
    private HouseholdMemberConfiguration householdMemberConfiguration;

    @InjectMocks
    private HmHouseholdHeadValidator validator;

    @BeforeEach
    void setUp() {
        // The whole point of these tests: the widened, index-backed lookup path.
        lenient().when(householdMemberConfiguration.isDuplicateCheckByClientReferenceEnabled()).thenReturn(true);
    }

    private static HouseholdMember member(String id, String clientReferenceId, String householdId,
                                          String householdClientReferenceId, Boolean isHeadOfHousehold) {
        HouseholdMember member = HouseholdMember.builder()
                .id(id)
                .clientReferenceId(clientReferenceId)
                .householdId(householdId)
                .householdClientReferenceId(householdClientReferenceId)
                .isHeadOfHousehold(isHeadOfHousehold)
                .build();
        member.setTenantId(TENANT_ID);
        return member;
    }

    private static HouseholdMemberBulkRequest request(HouseholdMember... members) {
        return HouseholdMemberBulkRequest.builder()
                .householdMembers(Arrays.asList(members))
                .build();
    }

    private static SearchResponse<HouseholdMember> response(HouseholdMember... rows) {
        List<HouseholdMember> list = Arrays.asList(rows);
        return SearchResponse.<HouseholdMember>builder().totalCount((long) list.size()).response(list).build();
    }

    private static boolean hasError(Map<HouseholdMember, List<Error>> errors, String errorCode,
                                    Error.ErrorType type) {
        return errors.values().stream().flatMap(List::stream)
                .anyMatch(error -> errorCode.equals(error.getErrorCode()) && type.equals(error.getType()));
    }

    /**
     * The gap flag-OFF cannot see: incoming head carries the server householdId, an already-stored DIFFERENT
     * head carries only the client reference (its householdId is still NULL). The index resolves the stored
     * head by the shared householdClientReferenceId and the conflict is caught - RECOVERABLE, because it looks
     * identical to a reassignment whose clearing update has not drained off the persister queue yet.
     */
    @Test
    void crossKeyStoredHeadWithNullHouseholdIdIsCaughtAsRecoverable() throws Exception {
        HouseholdMember storedHead =
                member("stored-head-id", "crid-stored", null, "HCR", Boolean.TRUE);
        when(householdMemberRepository.findById(anyString(), anyList(), eq(HOUSEHOLD_ID_FIELD), any()))
                .thenReturn(response());
        when(householdMemberRepository.findById(anyString(), anyList(),
                eq(HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD), any()))
                .thenReturn(response(storedHead));

        // Lone both-keys incoming head, a different member from the stored one, no client-reference sibling.
        HouseholdMember incomingHead = member(null, "crid-M1", "H", "HCR", Boolean.TRUE);

        Map<HouseholdMember, List<Error>> errors = validator.validate(request(incomingHead));

        assertTrue(hasError(errors, HOUSEHOLD_ALREADY_HAS_HEAD, Error.ErrorType.RECOVERABLE),
                "the stored head reachable only by householdClientReferenceId must be seen, and the conflict "
                        + "must stay retryable");
        // The index is loaded by both columns, one batch query each.
        verify(householdMemberRepository).findById(eq(TENANT_ID), anyList(), eq(HOUSEHOLD_ID_FIELD), any());
        verify(householdMemberRepository)
                .findById(eq(TENANT_ID), anyList(), eq(HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD), any());
    }

    /**
     * The same head re-syncing: it now carries the server householdId but its stored row still has a NULL
     * householdId. The stored row IS this member (same clientReferenceId), so widening the lookup must NOT
     * turn a legitimate re-send into a false HOUSEHOLD_ALREADY_HAS_HEAD.
     */
    @Test
    void sameHeadReSyncIsNotFalselyFlagged() throws Exception {
        HouseholdMember storedSelf =
                member("stored-self-id", "crid-same", null, "HCR", Boolean.TRUE);
        when(householdMemberRepository.findById(anyString(), anyList(), eq(HOUSEHOLD_ID_FIELD), any()))
                .thenReturn(response());
        when(householdMemberRepository.findById(anyString(), anyList(),
                eq(HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD), any()))
                .thenReturn(response(storedSelf));

        // Same member (same clientReferenceId) re-syncing with the householdId now filled in.
        HouseholdMember reSyncedHead = member(null, "crid-same", "H", "HCR", Boolean.TRUE);

        Map<HouseholdMember, List<Error>> errors = validator.validate(request(reSyncedHead));

        assertFalse(hasError(errors, HOUSEHOLD_ALREADY_HAS_HEAD, Error.ErrorType.RECOVERABLE),
                "a head finding its own out-of-order stored row must not be reported as a second head");
        assertFalse(hasError(errors, HOUSEHOLD_ALREADY_HAS_HEAD, Error.ErrorType.NON_RECOVERABLE),
                "and certainly not terminally");
        assertTrue(errors.isEmpty(), "the household has exactly one head - its own - so no error at all");
    }

    /**
     * The index build is best-effort: if the batch load throws, validation must fall back to the shipped
     * per-group lookup rather than failing the whole batch behind a 202.
     */
    @Test
    void indexBuildFailureFallsBackToPerGroupLookupWithoutFailingBatch() throws Exception {
        when(householdMemberRepository.findById(anyString(), anyList(), anyString(), any()))
                .thenThrow(new RuntimeException("db unavailable"));
        // Fallback path issues the per-group query; a brand-new household has no stored rows.
        when(householdMemberRepository.findIndividualByHousehold(anyString(), any(), anyString()))
                .thenReturn(SearchResponse.<HouseholdMember>builder()
                        .totalCount(0L).response(Collections.emptyList()).build());

        HouseholdMember head = member(null, "crid-M1", "H", "HCR", Boolean.TRUE);

        Map<HouseholdMember, List<Error>> errors =
                assertDoesNotThrow(() -> validator.validate(request(head)));

        assertFalse(hasError(errors, HOUSEHOLD_DOES_NOT_HAVE_A_HEAD, Error.ErrorType.NON_RECOVERABLE),
                "the request carries its own head, so it is valid on the fallback path too");
        // Fallback actually engaged: the per-group query ran.
        verify(householdMemberRepository).findIndividualByHousehold(TENANT_ID, "H", HOUSEHOLD_ID_FIELD);
    }
}
