package org.egov.household.validators.household;

import org.egov.common.models.Error;
import org.egov.common.models.core.Boundary;
import org.egov.common.models.household.Address;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.household.helper.AddressTestBuilder;
import org.egov.household.helper.HouseholdBulkRequestTestBuilder;
import org.egov.household.helper.HouseholdTestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A locality object present with a null code used to reach Collectors.groupingBy(null) and throw
 * NullPointerException, failing the entire batch instead of the one bad record. The filter's
 * comment claimed to exclude null locality codes but only checked that locality itself was
 * non-null. This test pins that behaviour.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HBoundaryValidatorNullLocalityCodeTest {

    @Mock
    private ServiceRequestClient serviceRequestClient;

    @Mock
    private HouseholdConfiguration householdConfiguration;

    @InjectMocks
    private HBoundaryValidator validator;

    private Household householdWithLocalityCode(String clientReferenceId, String code) {
        Address address = AddressTestBuilder.builder().withAddress().build();
        Boundary locality = new Boundary();
        locality.setCode(code);
        address.setLocality(locality);
        return HouseholdTestBuilder.builder().withHousehold()
                .withAddress(address)
                .build();
    }

    @Test
    void shouldNotThrowWhenLocalityCodeIsNull() {
        Household nullCode = householdWithLocalityCode("null-code", null);

        HouseholdBulkRequest request = HouseholdBulkRequestTestBuilder.builder()
                .withHouseholds(List.of(nullCode))
                .build();

        // Before the fix this threw NullPointerException out of Collectors.groupingBy,
        // which propagated past the validator and failed every record in the batch.
        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void shouldSkipNullLocalityCodeWithoutAffectingSiblings() {
        Household nullCode = householdWithLocalityCode("null-code", null);
        Household noLocality = HouseholdTestBuilder.builder().withHousehold()
                .withAddress(AddressTestBuilder.builder().withAddress().build())
                .build();

        HouseholdBulkRequest request = HouseholdBulkRequestTestBuilder.builder()
                .withHouseholds(Arrays.asList(nullCode, noLocality))
                .build();

        Map<Household, List<Error>> errors = assertDoesNotThrow(() -> validator.validate(request));

        // Neither record reaches the boundary lookup, so neither is flagged here — the point is
        // that the null code is skipped rather than blowing up the whole validation pass.
        assertTrue(errors.isEmpty());
    }
}
