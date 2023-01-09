package org.egov.household.service;

import org.egov.household.helper.HouseholdRequestTestBuilder;
import org.egov.household.helper.HouseholdTestBuilder;
import org.egov.household.repository.HouseholdRepository;
import org.egov.household.web.models.Household;
import org.egov.household.web.models.HouseholdRequest;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseholdUpdateTest {

    @InjectMocks
    HouseholdService householdService;

    @Mock
    HouseholdRepository householdRepository;

    @Test
    @DisplayName("should throw exception if household not found")
    void shouldThrowExceptionIfHouseholdNotFound() {
        HouseholdRequest request = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo().build();
        when(householdRepository.findById(anyList(), eq("id"), eq(false)))
                .thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> householdService.update(request));
    }

    @Test
    @DisplayName("should throw exception if row version is not correct")
    void shouldThrowExceptionIfRowVersionNotCorrect() {
        HouseholdRequest request = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo().build();
        request.getHousehold().get(0).setRowVersion(10);
        when(householdRepository.findById(anyList(), eq("id"), eq(false)))
                .thenReturn(Collections.singletonList(HouseholdTestBuilder.builder().withHousehold().withRowVersion(5).build()));

        assertThrows(CustomException.class, () -> householdService.update(request));
    }

    @Test
    @DisplayName("should increment row version by 1")
    void shouldIncrementRowVersionByOne() {
        HouseholdRequest request = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationUpdate().build();
        when(householdRepository.findById(anyList(), eq("id"), eq(false)))
                .thenReturn(request.getHousehold());

        List<Household> households = householdService.update(request);

        assertEquals(2, households.get(0).getRowVersion());
    }

    @Test
    @DisplayName("should set isDeleted true if API operation is delete")
    void shouldSetIsDeletedIfApiOperationIsTrue() {
        HouseholdRequest request = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationDelete().build();
        when(householdRepository.findById(anyList(), eq("id"), eq(false)))
                .thenReturn(request.getHousehold());

        List<Household> households = householdService.update(request);

        assertTrue(households.get(0).getIsDeleted());
    }

    @Test
    @DisplayName("should send data to kafka topic")
    void shouldSendDataToKafkaTopic() {
        HouseholdRequest request = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationDelete().build();
        when(householdRepository.findById(anyList(), eq("id"), eq(false)))
                .thenReturn(request.getHousehold());

        householdService.update(request);

        verify(householdRepository, times(1)).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should update data based on clientReferenceId if ID is null")
    void shouldUpdateDataBasedOnClientReferenceIdIfIDNull() {
        Household household = HouseholdTestBuilder.builder().withHousehold().withId(null).withClientReferenceId("crf_101").build();
        HouseholdRequest request = HouseholdRequestTestBuilder.builder()
                .withHousehold(Collections.singletonList(household)).withRequestInfo()
                .withApiOperationDelete().build();
        when(householdRepository.findById(anyList(), eq("clientReferenceId"), eq(false)))
                .thenReturn(request.getHousehold());

        householdService.update(request);

        verify(householdRepository, times(1)).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should update data based on ID if clientReferenceID is Null")
    void shouldUpdateDataBasedOnIDIfClientReferenceIdNull() {
        Household household = HouseholdTestBuilder.builder().withHousehold().withId("ID101").withClientReferenceId(null).build();
        HouseholdRequest request = HouseholdRequestTestBuilder.builder()
                .withHousehold(Collections.singletonList(household)).withRequestInfo()
                .withApiOperationDelete().build();
        when(householdRepository.findById(anyList(), eq("id"), eq(false)))
                .thenReturn(request.getHousehold());

        householdService.update(request);

        verify(householdRepository, times(1)).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should update data based on ID if clientReferenceID is also present")
    void shouldUpdateDataBasedOnIDIfClientReferenceIdPresent() {
        Household household = HouseholdTestBuilder.builder().withHousehold().withId("ID101").withClientReferenceId("crf_101").build();
        HouseholdRequest request = HouseholdRequestTestBuilder.builder()
                .withHousehold(Collections.singletonList(household)).withRequestInfo()
                .withApiOperationDelete().build();
        when(householdRepository.findById(anyList(), eq("id"), eq(false)))
                .thenReturn(request.getHousehold());

        householdService.update(request);

        verify(householdRepository, times(1)).save(anyList(), anyString());
    }
}
