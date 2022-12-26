package org.egov.service;

import org.egov.helper.HouseholdRequestTestBuilder;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.Household;
import org.egov.web.models.HouseholdRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class HouseholdServiceTest {

    @InjectMocks
    HouseholdService householdService;

    @Test
    @DisplayName("Should return household")
    void shouldReturnHousehold(){
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationCreate().build();

        List<Household> households = householdService.create(householdRequest);

        assertEquals(households.size(), householdRequest.getHousehold().size());
    }

    @Test
    @DisplayName("Should fail if client reference Id is already present in DB")
    void shouldFailIfClientReferenceIdIsAlreadyPresentInDB(){
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationCreate().build();

        assertThrows(CustomException.class, () -> householdService.create(householdRequest));
    }
}
