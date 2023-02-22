package org.egov.individual.validator;

import lombok.extern.slf4j.Slf4j;
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

}
