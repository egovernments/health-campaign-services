package org.egov.household.validators.household;

import org.egov.common.models.Error;
import org.egov.common.models.household.Address;
import org.egov.common.models.household.AddressType;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.household.helper.AddressTestBuilder;
import org.egov.household.helper.HouseholdBulkRequestTestBuilder;
import org.egov.household.helper.HouseholdTestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HAddressTypeValidatorTest {

    private final HAddressTypeValidator validator = new HAddressTypeValidator();

    @BeforeEach
    void enableValidator() {
        // Household never had this check, so the flag ships false. These cases cover the
        // enforcing behaviour and opt in explicitly.
        ReflectionTestUtils.setField(validator, "enabled", true);
    }

    @Test
    void shouldReportButNotRejectWhenDisabled() {
        ReflectionTestUtils.setField(validator, "enabled", false);
        Address nullTypeAddress = AddressTestBuilder.builder().withAddress().build();
        nullTypeAddress.setType(null);
        Household bad = HouseholdTestBuilder.builder().withHousehold()
                .withAddress(nullTypeAddress).build();

        assertTrue(validator.validate(HouseholdBulkRequestTestBuilder.builder()
                .withHouseholds(List.of(bad)).build()).isEmpty());
    }

    @Test
    void shouldFlagOnlyTheHouseholdWithNullAddressType() {
        Address nullTypeAddress = AddressTestBuilder.builder().withAddress().build();
        nullTypeAddress.setType(null);
        Household bad = HouseholdTestBuilder.builder().withHousehold()
                .withAddress(nullTypeAddress).build();

        Address validAddress = AddressTestBuilder.builder().withAddress().build();
        validAddress.setType(AddressType.PERMANENT);
        Household good = HouseholdTestBuilder.builder().withHousehold()
                .withAddress(validAddress).build();

        HouseholdBulkRequest request = HouseholdBulkRequestTestBuilder.builder()
                .withHouseholds(Arrays.asList(bad, good))
                .build();

        Map<Household, List<Error>> errors = validator.validate(request);

        assertEquals(1, errors.size());
        assertTrue(errors.containsKey(bad));
        assertFalse(errors.containsKey(good));
        assertEquals("INVALID_ADDRESS", errors.get(bad).get(0).getErrorCode());
    }

    @Test
    void shouldAcceptHouseholdWithValidAddressType() {
        Address validAddress = AddressTestBuilder.builder().withAddress().build();
        validAddress.setType(AddressType.PERMANENT);
        Household good = HouseholdTestBuilder.builder().withHousehold()
                .withAddress(validAddress).build();

        HouseholdBulkRequest request = HouseholdBulkRequestTestBuilder.builder()
                .withHouseholds(List.of(good))
                .build();

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void shouldAcceptHouseholdWithNoAddress() {
        Household noAddress = HouseholdTestBuilder.builder().withHousehold()
                .withAddress(null).build();

        HouseholdBulkRequest request = HouseholdBulkRequestTestBuilder.builder()
                .withHouseholds(List.of(noAddress))
                .build();

        assertTrue(validator.validate(request).isEmpty());
    }

    /**
     * HouseholdService injects {@code List<Validator<HouseholdBulkRequest, Household>>} and then
     * filters it by class. A validator without {@code @Component} is never in that list, so it is
     * silently inert no matter how many times the service names it — which is exactly the state
     * HCommunityTypeValidator is in today. This test fails if that happens to this validator.
     */
    @Test
    void shouldBeASpringComponentOtherwiseItIsNeverWiredIntoTheValidatorChain() {
        assertNotNull(HAddressTypeValidator.class.getAnnotation(Component.class),
                "HAddressTypeValidator must be annotated @Component or HouseholdService will never receive it");
    }
}
