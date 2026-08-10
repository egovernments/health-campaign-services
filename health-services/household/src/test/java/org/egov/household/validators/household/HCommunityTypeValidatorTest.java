package org.egov.household.validators.household;

import org.egov.common.models.Error;
import org.egov.common.models.household.HouseHoldType;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.household.helper.HouseholdBulkRequestTestBuilder;
import org.egov.household.helper.HouseholdTestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HCommunityTypeValidatorTest {

    @Mock
    private HouseholdConfiguration configuration;

    private HouseholdBulkRequest requestOf(HouseHoldType... types) {
        Household[] households = new Household[types.length];
        for (int i = 0; i < types.length; i++) {
            Household household = HouseholdTestBuilder.builder().withHousehold().build();
            household.setHouseholdType(types[i]);
            households[i] = household;
        }
        return HouseholdBulkRequestTestBuilder.builder().withHouseholds(List.of(households)).build();
    }

    private Map<Household, List<Error>> validate(boolean flagOn, HouseHoldType... types) {
        lenient().when(configuration.isHouseholdTypeSameValidation()).thenReturn(flagOn);
        return new HCommunityTypeValidator(configuration).validate(requestOf(types));
    }

    @Test
    void shouldFlagCommunityHouseholdsWhenMixedWithFamilyInOneRequest() {
        Map<Household, List<Error>> errors =
                validate(true, HouseHoldType.COMMUNITY, HouseHoldType.FAMILY);
        assertEquals(1, errors.size());
        assertEquals("COMMUNITY_AND_FAMILY_HOUSEHOLD_IN_SAME_REQUEST",
                errors.values().iterator().next().get(0).getErrorCode());
    }

    @Test
    void shouldAcceptARequestThatIsAllCommunity() {
        assertTrue(validate(true, HouseHoldType.COMMUNITY, HouseHoldType.COMMUNITY).isEmpty());
    }

    @Test
    void shouldAcceptARequestThatIsAllFamily() {
        assertTrue(validate(true, HouseHoldType.FAMILY, HouseHoldType.FAMILY).isEmpty());
    }

    @Test
    void shouldDoNothingWhenTheFlagIsOff() {
        assertTrue(validate(false, HouseHoldType.COMMUNITY, HouseHoldType.FAMILY).isEmpty());
    }

    /**
     * HouseholdService injects {@code List<Validator<HouseholdBulkRequest, Household>>} and then
     * filters it by class. Without {@code @Component} this validator is never in that list, so the
     * rule is silently inert however many times the service names it - which is the state it was in
     * until this fix. This test exists so that cannot recur unnoticed.
     */
    @Test
    void shouldBeASpringComponentOtherwiseItIsNeverWiredIntoTheValidatorChain() {
        assertNotNull(HCommunityTypeValidator.class.getAnnotation(Component.class),
                "HCommunityTypeValidator must be annotated @Component or HouseholdService will never receive it");
    }
}
