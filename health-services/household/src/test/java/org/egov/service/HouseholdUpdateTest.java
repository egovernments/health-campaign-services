package org.egov.service;

import org.egov.helper.HouseholdRequestTestBuilder;
import org.egov.helper.HouseholdTestBuilder;
import org.egov.repository.HouseholdRepository;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.Household;
import org.egov.web.models.HouseholdRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HouseholdUpdateTest {

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
                .thenReturn(Arrays.asList(HouseholdTestBuilder.builder().withHousehold().withRowVersion(5).build()));

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

        assertEquals(households.get(0).getRowVersion(), 2);
    }

    @Test
    @DisplayName("should set isDeleted true if API operation is delete")
    void shouldSetIsDeletedIfApiOperationIsTrue() {
        HouseholdRequest request = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationDelete().build();
        when(householdRepository.findById(anyList(), eq("id"), eq(false)))
                .thenReturn(request.getHousehold());

        List<Household> households = householdService.update(request);

        assertEquals(households.get(0).getIsDeleted(), true);
    }

    @Test
    @DisplayName("should send data to kafka topic")
    void shouldSendDataToKafkaTopic() {
        HouseholdRequest request = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationDelete().build();
        when(householdRepository.findById(anyList(), eq("id"), eq(false)))
                .thenReturn(request.getHousehold());

        List<Household> households = householdService.update(request);

        verify(householdRepository, times(1)).save(anyList(), anyString());
    }
}
