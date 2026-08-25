package org.egov.household.validators.household;

import org.egov.common.models.Error;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HRequiredLinkValidatorTest {

    private final HRequiredLinkValidator validator = new HRequiredLinkValidator();

    @BeforeEach
    void enableValidator() {
        // The flag ships false so the image cannot start rejecting payloads that persisted before.
        // These cases cover the enforcing behaviour, so they opt in explicitly.
        ReflectionTestUtils.setField(validator, "enabled", true);
    }

    @Test
    void shouldReportButNotRejectWhenDisabled() {
        ReflectionTestUtils.setField(validator, "enabled", false);
        Household invalid = Household.builder().clientReferenceId("  ").hasErrors(false).build();

        assertTrue(validator.validate(HouseholdBulkRequest.builder()
                .households(List.of(invalid)).build()).isEmpty());
    }

    @Test
    void shouldRejectOnlyHouseholdWithBlankClientReferenceId() {
        Household invalid = Household.builder().clientReferenceId("  ").hasErrors(false).build();
        Household valid = Household.builder().clientReferenceId("device-household-1")
                .hasErrors(false).build();
        HouseholdBulkRequest request = HouseholdBulkRequest.builder()
                .households(Arrays.asList(invalid, valid)).build();

        Map<Household, List<Error>> errors = validator.validate(request);

        assertEquals(1, errors.size());
        assertTrue(errors.containsKey(invalid));
        assertFalse(errors.containsKey(valid));
        assertEquals("REQUIRED_LINK_MISSING", errors.get(invalid).get(0).getErrorCode());
    }

    @Test
    void shouldAcceptHouseholdWithClientReferenceId() {
        Household valid = Household.builder().clientReferenceId("device-household-1").build();

        assertTrue(validator.validate(HouseholdBulkRequest.builder()
                .households(List.of(valid)).build()).isEmpty());
    }
}
