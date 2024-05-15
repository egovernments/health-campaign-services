package org.egov.household.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.producer.Producer;
import org.egov.common.service.IdGenService;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.household.helper.HouseholdBulkRequestTestBuilder;
import org.egov.household.helper.HouseholdTestBuilder;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseholdEnrichmentServiceTest {

    @InjectMocks
    HouseholdEnrichmentService householdService;

    @Mock
    IdGenService idGenService;

    @Mock
    HouseholdConfiguration householdConfiguration;

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
        lenient().when(householdConfiguration.getCreateTopic()).thenReturn("create-topic");
        lenient().when(householdConfiguration.getUpdateTopic()).thenReturn("update-topic");
        when(householdConfiguration.getIdgenFormat()).thenReturn("household.id");
    }

    @Test
    @DisplayName("should generate and set ID using ID generation service")
    void shouldGenerateAndSetIdFromIdGenService() throws Exception {
        HouseholdBulkRequest householdBulkRequest = HouseholdBulkRequestTestBuilder.builder().withHouseholds().withRequestInfo()
                .build();
        List<Household> households = householdBulkRequest.getHouseholds();
        householdService.create(households, householdBulkRequest);

        assertNotNull(households.get(0).getId());
        verify(idGenService, times(1))
                .getIdList(any(RequestInfo.class), anyString(), eq("household.id"), eq(""), anyInt());
    }

    @Test
    @DisplayName("should enrich household request with rowVersion and isDeleted")
    void shouldEnrichHouseholdWithRowVersionAndIsDeleted() throws Exception {
        HouseholdBulkRequest householdBulkRequest = HouseholdBulkRequestTestBuilder.builder().withHouseholds().withRequestInfo()
                .build();

        List<Household> households = householdBulkRequest.getHouseholds();
        householdService.create(households, householdBulkRequest);

        assertEquals(1, households.stream().findAny().get().getRowVersion());
        assertFalse(households.stream().findAny().get().getIsDeleted());
    }

    @Test
    @DisplayName("should enrich household request with audit details")
    void shouldEnrichHouseholdWithAuditDetails() throws Exception {
        HouseholdBulkRequest householdBulkRequest = HouseholdBulkRequestTestBuilder.builder().withHouseholds().withRequestInfo()
                .build();

        List<Household> households = householdBulkRequest.getHouseholds();
        householdService.create(households, householdBulkRequest);

        assertNotNull(households.stream().findAny().get().getAuditDetails().getCreatedBy());
        assertNotNull(households.stream().findAny().get().getAuditDetails().getCreatedTime());
        assertNotNull(households.stream().findAny().get().getAuditDetails().getLastModifiedBy());
        assertNotNull(households.stream().findAny().get().getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should generate address Id where address is not null")
    void shouldEnrichAddressIdIfAddressNotNull() throws Exception {
        Household withAddress = HouseholdTestBuilder.builder().withHousehold().build();
        HouseholdBulkRequest householdBulkRequest = HouseholdBulkRequestTestBuilder.builder()
                .withHouseholds(Arrays.asList(withAddress)).withRequestInfo()
                .build();

        List<Household> households = householdBulkRequest.getHouseholds();
        householdService.create(households, householdBulkRequest);

        assertNotNull(households.get(0).getAddress().getId());
    }

    @Test
    @DisplayName("should not generate address Id where address is null")
    void shouldNotEnrichAddressIdIfAddressNull() throws Exception {
        Household withNullAddress = HouseholdTestBuilder.builder().withHousehold().withAddress(null).build();
        HouseholdBulkRequest householdBulkRequest = HouseholdBulkRequestTestBuilder.builder()
                .withHouseholds(Arrays.asList(withNullAddress)).withRequestInfo()
                .build();
        List<Household> households = householdBulkRequest.getHouseholds();
         householdService.create(households, householdBulkRequest);

        assertNull(households.get(0).getAddress());
        verify(idGenService, times(1))
                .getIdList(any(RequestInfo.class), anyString(), anyString(), anyString(), anyInt());
    }
}
