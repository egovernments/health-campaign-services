package org.egov.individual.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.individual.helper.IndividualBulkRequestTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.validators.AddressTypeValidator;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.AddressType;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualBulkRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class AddressTypeValidatorTest {

    @InjectMocks
    private AddressTypeValidator addressTypeValidator;

    @Test
    void shouldNotGiveError_WhenAddressTypeIsValid() {
        Address address = Address.builder()
                .id("some-Id")
                .city("some-city")
                .tenantId("some-tenant-id")
                .type(AddressType.PERMANENT)
                .isDeleted(false)
                .build();
        Individual individual = IndividualTestBuilder.builder().withAddress(address).build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        assertTrue(addressTypeValidator.validate(individualBulkRequest).isEmpty());
    }

    @Test
    void shouldGiveError_WhenAddressIsInvalid() {
        Address address = Address.builder().id("some-Id").tenantId("some-tenant-Id").type(AddressType.PERMANENT).build();
        Address address1 = Address.builder().id("some-Id").tenantId("some-tenant-Id").type(AddressType.PERMANENT).build();
        Individual individual = IndividualTestBuilder.builder().withAddress(address, address1).build();
        IndividualBulkRequest individualBulkRequest = IndividualBulkRequestTestBuilder.builder().withIndividuals(individual).build();
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        errorDetailsMap = addressTypeValidator.validate(individualBulkRequest);
        List<Error> errorList = errorDetailsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        assertEquals("INVALID_ADDRESS", errorList.get(0).getErrorCode());
    }


}
