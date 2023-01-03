package org.egov.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.producer.Producer;
import org.egov.common.service.IdGenService;
import org.egov.helper.HouseholdRequestTestBuilder;
import org.egov.helper.HouseholdTestBuilder;
import org.egov.repository.HouseholdRepository;
import org.egov.web.models.Household;
import org.egov.web.models.HouseholdRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HouseholdServiceTest {

    @InjectMocks
    HouseholdService householdService;

    @Mock
    HouseholdRepository householdRepository;

    @Mock
    IdGenService idGenService;

    @Mock
    Producer producer;

    @BeforeEach
    void setUp() throws Exception {
        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                        any(String.class),
                        eq("household.id"), eq(""), anyInt()))
                .thenReturn(idList);
    }

    @Test
    @DisplayName("should not call validateId if clientReferenceId is null")
    void shouldNotCallValidateIdIfClientReferenceIdIsNull() throws Exception {
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationCreate().build();
        householdRequest.getHousehold().get(0).setClientReferenceId(null);

        householdService.create(householdRequest);

        verify(householdRepository, times(0)).validateIds(anyList(), anyString());
    }

    @Test
    @DisplayName("should generate and set ID using ID generation service")
    void shouldGenerateAndSetIdFromIdGenService() throws Exception {
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationCreate().build();

        List<Household> households = householdService.create(householdRequest);

        assertNotNull(households.get(0).getId());
        verify(idGenService, times(1))
                .getIdList(any(RequestInfo.class), anyString(), eq("household.id"), eq(""), anyInt());
    }

    @Test
    @DisplayName("should enrich household request with rowVersion and isDeleted")
    void shouldEnrichHouseholdWithRowVersionAndIsDeleted() throws Exception {
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationCreate().build();

        List<Household> households = householdService.create(householdRequest);

        assertEquals(households.stream().findAny().get().getRowVersion(), 1);
        assertEquals(households.stream().findAny().get().getIsDeleted(), false);
    }

    @Test
    @DisplayName("should enrich household request with audit details")
    void shouldEnrichHouseholdWithAuditDetails() throws Exception {
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationCreate().build();

        List<Household> households = householdService.create(householdRequest);

        assertNotNull(households.stream().findAny().get().getAuditDetails().getCreatedBy());
        assertNotNull(households.stream().findAny().get().getAuditDetails().getCreatedTime());
        assertNotNull(households.stream().findAny().get().getAuditDetails().getLastModifiedBy());
        assertNotNull(households.stream().findAny().get().getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should generate address Id where address is not null")
    void shouldEnrichAddressIdIfAddressNotNull() throws Exception {
        Household withAddress = HouseholdTestBuilder.builder().withHousehold().build();
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder()
                .withHousehold(Arrays.asList(withAddress)).withRequestInfo()
                .withApiOperationCreate().build();

        List<Household> households = householdService.create(householdRequest);

        assertNotNull(households.get(0).getAddress().getId());
    }

    @Test
    @DisplayName("should not generate address Id where address is null")
    void shouldNotEnrichAddressIdIfAddressNull() throws Exception {
        Household withNullAddress = HouseholdTestBuilder.builder().withHousehold().withAddress(null).build();
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder()
                .withHousehold(Arrays.asList(withNullAddress)).withRequestInfo()
                .withApiOperationCreate().build();

        List<Household> households = householdService.create(householdRequest);

        assertNull(households.get(0).getAddress());
        verify(idGenService, times(1))
                .getIdList(any(RequestInfo.class), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("should send data to kafka")
    void shouldSendDataToKafkaTopic() throws Exception {
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationCreate().build();

        householdService.create(householdRequest);

        verify(householdRepository, times(1)).save(anyList(), anyString());
    }
}
