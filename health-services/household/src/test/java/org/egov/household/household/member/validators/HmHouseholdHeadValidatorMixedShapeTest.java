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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * One household, two parent-key shapes, flag OFF.
 *
 * <p>A newly registered household's head is the member that gets the server {@code householdId} back first,
 * so it carries BOTH keys while its siblings still carry only the {@code householdClientReferenceId}. If the
 * validator keys each member by whichever shape it happens to carry, that one household is split across two
 * groups: the head lands alone in the householdId group and the siblings land in a group that contains no
 * head at all. The siblings are then stamped HOUSEHOLD_DOES_NOT_HAVE_A_HEAD / NON_RECOVERABLE, which is
 * final for the field device - the records are lost, not delayed.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HmHouseholdHeadValidatorMixedShapeTest {

    private static final String TENANT_ID = "default";

    private static final String HOUSEHOLD_DOES_NOT_HAVE_A_HEAD = "HOUSEHOLD_DOES_NOT_HAVE_A_HEAD";

    private static final String HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD = "HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD";

    private static final String HOUSEHOLD_ALREADY_HAS_HEAD = "HOUSEHOLD_ALREADY_HAS_HEAD";

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    /** Default mock answer is false: every test here runs the shipped, non-widened lookup path. */
    @Mock
    private HouseholdMemberConfiguration householdMemberConfiguration;

    @InjectMocks
    private HmHouseholdHeadValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        // Brand new household: nothing stored yet, so every group is judged on the request alone.
        lenient().when(householdMemberRepository.findIndividualByHousehold(anyString(), any(), anyString()))
                .thenReturn(SearchResponse.<HouseholdMember>builder()
                        .totalCount(0L)
                        .response(Collections.emptyList())
                        .build());
    }

    private static HouseholdMember member(String clientReferenceId, String householdId,
                                          String householdClientReferenceId, Boolean isHeadOfHousehold) {
        HouseholdMember member = HouseholdMember.builder()
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

    private static boolean hasError(Map<HouseholdMember, List<Error>> errors, String errorCode) {
        return errors.values().stream().flatMap(List::stream)
                .anyMatch(error -> errorCode.equals(error.getErrorCode()));
    }

    private static boolean hasError(Map<HouseholdMember, List<Error>> errors, String errorCode,
                                    Error.ErrorType type) {
        return errors.values().stream().flatMap(List::stream)
                .anyMatch(error -> errorCode.equals(error.getErrorCode()) && type.equals(error.getType()));
    }

    private static long countErrors(Map<HouseholdMember, List<Error>> errors, String errorCode) {
        return errors.values().stream().flatMap(List::stream)
                .filter(error -> errorCode.equals(error.getErrorCode())).count();
    }

    @Test
    void oneHouseholdSplitAcrossBothParentKeyShapesIsAccepted() {
        HouseholdMember head = member("crid-M1", "H", "HCR", Boolean.TRUE);
        HouseholdMember sibling2 = member("crid-M2", null, "HCR", Boolean.FALSE);
        HouseholdMember sibling3 = member("crid-M3", null, "HCR", Boolean.FALSE);

        Map<HouseholdMember, List<Error>> errors = validator.validate(request(head, sibling2, sibling3));

        assertFalse(hasError(errors, HOUSEHOLD_DOES_NOT_HAVE_A_HEAD),
                "M2/M3 belong to the same household as M1, which IS the head - they must not be reported "
                        + "headless");
        assertFalse(Boolean.TRUE.equals(sibling2.getHasErrors()), "M2 must be accepted");
        assertFalse(Boolean.TRUE.equals(sibling3.getHasErrors()), "M3 must be accepted");
        assertFalse(Boolean.TRUE.equals(head.getHasErrors()), "M1 must be accepted");
        assertTrue(errors.isEmpty(), "a single valid household must produce no errors at all");
        assertEquals(0, errors.size());
    }

    /** The merged group closes the detection blind spot: both heads are now in the same group. */
    @Test
    void twoHeadsSpanningBothParentKeyShapesAreCaught() {
        HouseholdMember headWithBothKeys = member("crid-M1", "H", "HCR", Boolean.TRUE);
        HouseholdMember headWithClientReferenceOnly = member("crid-M2", null, "HCR", Boolean.TRUE);

        Map<HouseholdMember, List<Error>> errors =
                validator.validate(request(headWithBothKeys, headWithClientReferenceOnly));

        assertTrue(hasError(errors, HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD, Error.ErrorType.RECOVERABLE),
                "the conflict must be reported, but RECOVERABLE: the two heads are only in one group "
                        + "because the alias inferred from the bridging member put them there, so a "
                        + "re-send without that bridge is accepted and the device must keep retrying");
        assertEquals(Boolean.TRUE, headWithBothKeys.getHasErrors());
        assertEquals(Boolean.TRUE, headWithClientReferenceOnly.getHasErrors());
        // Exactly one error per member: neither may be validated twice.
        assertEquals(2, countErrors(errors, HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD));
    }

    /**
     * Blast radius of that conflict. The merge folds the client-reference-only siblings into the same group
     * as the two heads, so the whole-group stamp reaches members that state no conflict at all - and the
     * identical records WITHOUT the bridging member are accepted in full. Whatever the batch partition, a
     * record that only differs by which other records travelled with it must stay retryable.
     */
    @Test
    void innocentSiblingsInAMergedTwoHeadGroupStayRetryable() {
        HouseholdMember headWithBothKeys = member("crid-M1", "H", "HCR", Boolean.TRUE);
        HouseholdMember secondHead = member("crid-M9", null, "HCR", Boolean.TRUE);
        HouseholdMember sibling1 = member("crid-S1", null, "HCR", Boolean.FALSE);
        HouseholdMember sibling2 = member("crid-S2", null, "HCR", Boolean.FALSE);

        Map<HouseholdMember, List<Error>> errors = validator.validate(
                request(headWithBothKeys, secondHead, sibling1, sibling2));

        assertFalse(hasError(errors, HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD, Error.ErrorType.NON_RECOVERABLE),
                "no member of a merged group may be stamped final on a conflict the merge itself created");
        assertEquals(4, countErrors(errors, HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD),
                "every member of the group is told, exactly once");
        assertTrue(errors.values().stream().flatMap(List::stream)
                        .allMatch(error -> Error.ErrorType.RECOVERABLE.equals(error.getType())),
                "the siblings carry no conflict of their own - they must remain retryable");
    }

    /**
     * A self-contradictory batch - two members claiming different householdIds for one
     * householdClientReferenceId. The first claim in request order wins, deterministically.
     */
    @Test
    void conflictingAliasKeepsTheFirstClaimAndValidatesEveryMemberOnce() {
        HouseholdMember firstClaim = member("crid-M1", "H1", "HCR", Boolean.TRUE);
        HouseholdMember losingClaim = member("crid-M2", "H2", "HCR", Boolean.FALSE);
        HouseholdMember clientReferenceOnly = member("crid-M3", null, "HCR", Boolean.FALSE);

        Map<HouseholdMember, List<Error>> errors =
                validator.validate(request(firstClaim, losingClaim, clientReferenceOnly));

        // HCR resolves to H1, so M3 joins M1's group, which has a head. If the LAST claim had won, M3
        // would have landed in H2's headless group and been rejected alongside M2.
        assertFalse(Boolean.TRUE.equals(clientReferenceOnly.getHasErrors()),
                "the client-reference-only member follows the first householdId claimed for its household");
        assertFalse(Boolean.TRUE.equals(firstClaim.getHasErrors()));
        // M2 carries its own householdId H2, whose group holds no head - exactly as it is rejected today.
        assertEquals(Boolean.TRUE, losingClaim.getHasErrors());
        assertEquals(1, countErrors(errors, HOUSEHOLD_DOES_NOT_HAVE_A_HEAD),
                "only the contradicting member is rejected, and only once");
    }

    /**
     * No member carries both keys, so nothing bridges the two shapes and grouping is exactly what ships:
     * one query per group, each on the column its group is keyed by.
     */
    @Test
    void batchThatDoesNotMixShapesIsGroupedAndQueriedExactlyAsBefore() throws Exception {
        HouseholdMember serverKeyed = member("crid-A", "H1", null, Boolean.TRUE);
        HouseholdMember clientReferenceKeyed = member("crid-B", null, "HCR2", Boolean.TRUE);

        Map<HouseholdMember, List<Error>> errors =
                validator.validate(request(serverKeyed, clientReferenceKeyed));

        assertTrue(errors.isEmpty(), "two distinct single-head households are both valid");
        verify(householdMemberRepository).findIndividualByHousehold(TENANT_ID, "H1", "householdId");
        verify(householdMemberRepository)
                .findIndividualByHousehold(TENANT_ID, "HCR2", "householdClientReferenceId");
        verifyNoMoreInteractions(householdMemberRepository);
    }

    /**
     * A member carrying both keys with no client-reference-only sibling merges nothing, so it must still
     * issue the single householdId query and no other.
     */
    @Test
    void memberCarryingBothKeysAloneStillIssuesOnlyTheHouseholdIdQuery() throws Exception {
        Map<HouseholdMember, List<Error>> errors =
                validator.validate(request(member("crid-M1", "H", "HCR", Boolean.TRUE)));

        assertTrue(errors.isEmpty());
        verify(householdMemberRepository).findIndividualByHousehold(TENANT_ID, "H", "householdId");
        verifyNoMoreInteractions(householdMemberRepository);
    }

    /**
     * The merged group must not lose sight of stored rows its client-reference members could see before
     * the merge: the stored head written while the household had not synced yet carries a NULL householdId.
     */
    @Test
    void mergedGroupStillSeesTheStoredHeadReachableOnlyByClientReference() throws Exception {
        HouseholdMember storedHead = member("crid-stored-head", null, "HCR", Boolean.TRUE);
        lenient().when(householdMemberRepository
                        .findIndividualByHousehold(TENANT_ID, "HCR", "householdClientReferenceId"))
                .thenReturn(SearchResponse.<HouseholdMember>builder().totalCount(1L)
                        .response(Collections.singletonList(storedHead)).build());

        HouseholdMember memberWithBothKeys = member("crid-M1", "H", "HCR", Boolean.FALSE);
        HouseholdMember sibling = member("crid-M2", null, "HCR", Boolean.FALSE);

        Map<HouseholdMember, List<Error>> errors =
                validator.validate(request(memberWithBothKeys, sibling));

        assertTrue(errors.isEmpty(), "the household has a stored head, so no member of it is headless");
        verify(householdMemberRepository).findIndividualByHousehold(TENANT_ID, "H", "householdId");
        verify(householdMemberRepository)
                .findIndividualByHousehold(TENANT_ID, "HCR", "householdClientReferenceId");
    }

    /**
     * A conflict that only the merged lookup can see is RECOVERABLE: it looks identical to a legitimate
     * head reassignment whose clearing update has not drained off the persister queue yet, and a
     * NON_RECOVERABLE error would stop the device retrying.
     */
    @Test
    void conflictOnlyTheMergedLookupCanSeeIsRecoverable() throws Exception {
        HouseholdMember storedHead = member("crid-stored-head", null, "HCR", Boolean.TRUE);
        lenient().when(householdMemberRepository
                        .findIndividualByHousehold(TENANT_ID, "HCR", "householdClientReferenceId"))
                .thenReturn(SearchResponse.<HouseholdMember>builder().totalCount(1L)
                        .response(Collections.singletonList(storedHead)).build());

        HouseholdMember incomingHead = member("crid-M1", "H", "HCR", Boolean.TRUE);
        HouseholdMember sibling = member("crid-M2", null, "HCR", Boolean.FALSE);

        Map<HouseholdMember, List<Error>> errors = validator.validate(request(incomingHead, sibling));

        assertTrue(hasError(errors, HOUSEHOLD_ALREADY_HAS_HEAD, Error.ErrorType.RECOVERABLE),
                "the newly visible conflict must leave the device able to retry");
        assertFalse(Boolean.TRUE.equals(sibling.getHasErrors()),
                "only the incoming head is rejected, not its siblings");
    }

    /** Trap: a device omitting isHeadOfHousehold must not unbox to an NPE that takes the batch down. */
    @Test
    void nullIsHeadOfHouseholdDoesNotThrow() {
        Map<HouseholdMember, List<Error>> errors = assertDoesNotThrow(() ->
                validator.validate(request(member("crid-A", null, "household-clientref-1", null))));

        // No head anywhere for that household -> the household-has-no-head rule fires per record.
        assertEquals(1, errors.size());
    }

    /**
     * Trap: a member carrying neither parent key must be skipped, not fail the batch. Missing-link
     * enforcement belongs to HmRequiredLinkValidator.
     */
    @Test
    void memberWithNoParentLinkIsSkippedRatherThanFailingTheBatch() {
        Map<HouseholdMember, List<Error>> errors = assertDoesNotThrow(() -> validator.validate(request(
                member("crid-unlinked", null, null, Boolean.TRUE),
                member("crid-ok", null, "household-clientref-1", Boolean.TRUE))));

        assertTrue(errors.isEmpty(), "an unlinked member must not produce a head-validation error here");
    }
}
