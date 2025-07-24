package org.egov.individual.service;

import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.household.Household;
import org.egov.individual.repository.HouseholdRepository;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.common.models.household.HouseholdSearchRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseholdFindTest {
    @InjectMocks
    HouseholdService householdService;

    @Mock
    HouseholdRepository householdRepository;

    @Test
    @DisplayName("should search only by id if only id is present")
    void shouldOnlySearchByIdIfOnlyIdIsPresent() throws QueryBuilderException, InvalidTenantIdException {
        HouseholdSearchRequest householdSearchRequest = HouseholdSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .household(HouseholdSearch.builder().id(Collections.singletonList("some-id")).build()).build();
        when(householdRepository.findById( anyString(),  anyList(), eq("id"), anyBoolean()))
                .thenReturn(SearchResponse.<Household>builder().build());

        householdService.search(householdSearchRequest.getHousehold(), 10, 0, "default",
                null, false);

        verify(householdRepository, times(1))
                .findById( anyString(),anyList(), eq("id"), anyBoolean());
    }

    @Test
    @DisplayName("should search only by clientReferenceId if only clientReferenceId is present")
    void shouldOnlySearchByClientReferenceIdIfOnlyClientReferenceIdIsPresent() throws QueryBuilderException, InvalidTenantIdException {
        HouseholdSearchRequest householdSearchRequest = HouseholdSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .household(HouseholdSearch.builder().clientReferenceId(Collections.singletonList("some-id")).build()).build();
        when(householdRepository.findById(anyString(), anyList(), eq("clientReferenceId"), anyBoolean()))
                .thenReturn(SearchResponse.<Household>builder().build());

        householdService.search(householdSearchRequest.getHousehold(), 10, 0, "default",
                null, false);

        verify(householdRepository, times(1)).findById(anyString(), anyList(),
                eq("clientReferenceId"), anyBoolean());
    }

    @Test
    @DisplayName("should not call findById if more search parameters are available")
    void shouldNotCallFindByIdIfMoreParametersAreAvailable() throws QueryBuilderException, InvalidTenantIdException {
        HouseholdSearchRequest householdSearchRequest = HouseholdSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .household(HouseholdSearch.builder().id(Collections.singletonList("someid"))
                        .clientReferenceId(Collections.singletonList("some-id")).build()).build();
        when(householdRepository.find(any(HouseholdSearch.class), anyInt(),
                anyInt(), anyString(), anyLong(), anyBoolean())).thenReturn(SearchResponse.<Household>builder().build());

        householdService.search(householdSearchRequest.getHousehold(), 10, 0,
                "default", 0L, false);

        verify(householdRepository, times(0))
                .findById( anyString(),anyList(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("should call find if more parameters are available")
    void shouldCallFindIfMoreParametersAreAvailable() throws QueryBuilderException, InvalidTenantIdException {
        HouseholdSearchRequest householdSearchRequest = HouseholdSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .household(HouseholdSearch.builder().id(Collections.singletonList("someid"))
                        .clientReferenceId(Collections.singletonList("some-id")).build()).build();
        when(householdRepository.find(any(HouseholdSearch.class), anyInt(),
                anyInt(), anyString(), anyLong(), anyBoolean())).thenReturn(SearchResponse.<Household>builder().build());

        householdService.search(householdSearchRequest.getHousehold(), 10, 0,
                "default", 0L, false);

        verify(householdRepository, times(1))
                .find(any(HouseholdSearch.class), anyInt(),
                        anyInt(), anyString(), anyLong(), anyBoolean());
    }
}
