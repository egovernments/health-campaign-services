package org.egov.household.validators.household;

import org.egov.common.models.Error;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.household.HouseHoldType;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.household.repository.HouseholdRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Offline-first regression cover for the householdType-change validator.
 *
 * <p>Two ways this validator used to take a whole batch down from inside {@code validate()}, where the bulk
 * consumer swallowed the throw as an empty result: a raw two-arg {@code Collectors.toMap} keyed on an id that
 * is routinely null (a household created offline carries only a clientReferenceId) or repeated on a replay,
 * and a {@code HOUSEHOLD_SEARCH_FAILED} rethrow on a transient DB failure.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HHouseholdTypeChangeValidatorTest {

    @Mock
    private HouseholdRepository householdRepository;

    @InjectMocks
    private HHouseholdTypeChangeValidator validator;

    private Household household(String id, String clientReferenceId, HouseHoldType householdType) {
        Household household = new Household();
        household.setId(id);
        household.setClientReferenceId(clientReferenceId);
        household.setHouseholdType(householdType);
        household.setTenantId("default");
        return household;
    }

    private HouseholdBulkRequest request(Household... households) {
        return HouseholdBulkRequest.builder()
                .households(Arrays.asList(households))
                .build();
    }

    private void stubSearch(List<Household> found) throws Exception {
        lenient().when(householdRepository.find(any(HouseholdSearch.class), anyInt(), anyInt(), anyString(),
                        eq(null), anyBoolean()))
                .thenReturn(SearchResponse.<Household>builder()
                        .totalCount((long) found.size())
                        .response(found)
                        .build());
    }

    @Test
    void nullIdIsSkippedRatherThanFailingTheBatch() throws Exception {
        // The normal unbundle-shaped input: no server id yet, only a client reference.
        Household unsynced = household(null, "crid-unsynced", HouseHoldType.FAMILY);
        Household synced = household("household-1", "crid-1", HouseHoldType.FAMILY);
        stubSearch(Collections.singletonList(household("household-1", "crid-1", HouseHoldType.FAMILY)));

        Map<Household, List<Error>> errors = assertDoesNotThrow(() -> validator.validate(request(unsynced, synced)));

        assertTrue(errors.isEmpty(), "no type changed, so nothing may be reported");
    }

    @Test
    void repeatedIdDoesNotThrowDuplicateKey() throws Exception {
        Household first = household("household-1", "crid-1", HouseHoldType.FAMILY);
        Household replayed = household("household-1", "crid-1", HouseHoldType.FAMILY);
        stubSearch(Collections.singletonList(household("household-1", "crid-1", HouseHoldType.FAMILY)));

        Map<Household, List<Error>> errors = assertDoesNotThrow(() -> validator.validate(request(first, replayed)));

        assertTrue(errors.isEmpty());
    }

    @Test
    void searchFailureFailsOpenInsteadOfLosingTheBatch() throws Exception {
        Household household = household("household-1", "crid-1", HouseHoldType.FAMILY);
        when(householdRepository.find(any(HouseholdSearch.class), anyInt(), anyInt(), anyString(), eq(null),
                anyBoolean())).thenThrow(new RuntimeException("connection reset"));

        Map<Household, List<Error>> errors = assertDoesNotThrow(() -> validator.validate(request(household)));

        // Failing open costs at most a missed householdType change; throwing cost the whole batch.
        assertTrue(errors.isEmpty());
    }
}
