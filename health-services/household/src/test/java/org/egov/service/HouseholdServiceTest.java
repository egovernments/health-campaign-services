package org.egov.service;

import org.egov.helper.HouseholdRequestTestBuilder;
import org.egov.repository.HouseholdRepository;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.HouseholdRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseholdServiceTest {

    @InjectMocks
    HouseholdService householdService;

    @Mock
    HouseholdRepository householdRepository;

    @Test
    @DisplayName("should call validateId once")
    void shouldCallValidateIdOnce() {
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationCreate().build();
        when(householdRepository.validateIds(anyList(), anyString())).thenReturn(Arrays.asList());

        householdService.create(householdRequest);

        verify(householdRepository, times(1)).validateIds(anyList(), anyString());
    }

    @Test
    @DisplayName("should fail if client reference Id is already present in DB")
    void shouldFailIfClientReferenceIdIsAlreadyPresentInDB() {
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationCreate().build();
        when(householdRepository.validateIds(anyList(), anyString())).thenReturn(Arrays.asList("id101"));

        assertThrows(CustomException.class, () -> householdService.create(householdRequest));
    }

    @Test
    @DisplayName("should not call validateId if clientReferenceId is null")
    void shouldNotCallValidateIdIfClientReferenceIdIsNull() {
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationCreate().build();
        householdRequest.getHousehold().get(0).setClientReferenceId(null);

        householdService.create(householdRequest);

        verify(householdRepository, times(0)).validateIds(anyList(), anyString());
    }
}
