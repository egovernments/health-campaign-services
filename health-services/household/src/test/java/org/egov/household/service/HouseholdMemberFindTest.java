package org.egov.household.service;

import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.household.HouseholdMember;
import org.egov.household.repository.HouseholdMemberRepository;
import org.egov.common.models.household.HouseholdMemberSearch;
import org.egov.common.models.household.HouseholdMemberSearchRequest;
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
class HouseholdMemberFindTest {
    @InjectMocks
    HouseholdMemberService householdMemberService;

    @Mock
    HouseholdMemberRepository householdMemberRepository;

    @Test
    @DisplayName("should search only by id if only id is present")
    void shouldOnlySearchByIdIfOnlyIdIsPresent() throws InvalidTenantIdException {
        HouseholdMemberSearchRequest householdSearchRequest = HouseholdMemberSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .householdMemberSearch(HouseholdMemberSearch.builder()
                        .id(Collections.singletonList("some-id")).build()).build();
        when(householdMemberRepository.findById( anyString(), anyList(), eq("id"), anyBoolean()))
                .thenReturn(SearchResponse.<HouseholdMember>builder().build());

        householdMemberService.search(householdSearchRequest.getHouseholdMemberSearch(), 10, 0, "default",
                null, false);

        verify(householdMemberRepository, times(1))
                .findById( anyString(), anyList(), eq("id"), anyBoolean());
    }


    @Test
    @DisplayName("should not call findById if more search parameters are available")
    void shouldNotCallFindByIfIfMoreParametersAreAvailable() throws QueryBuilderException, InvalidTenantIdException {
        HouseholdMemberSearchRequest householdMemberSearchRequest = HouseholdMemberSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .householdMemberSearch(HouseholdMemberSearch.builder()
                        .id(Collections.singletonList("some-id")).householdId(Collections.singletonList("household-id")).build()).build();
        when(householdMemberRepository.find(any(HouseholdMemberSearch.class), anyInt(),
                anyInt(), anyString(), anyLong(), anyBoolean())).thenReturn(SearchResponse.<HouseholdMember>builder().build());

        householdMemberService.search(householdMemberSearchRequest.getHouseholdMemberSearch(), 10, 0,
                "", 0L, false);

        verify(householdMemberRepository, times(0))
                .findById(anyString() ,anyList(), anyString(), anyBoolean());
    }


    @Test
    @DisplayName("should call find if more parameters are available")
    void shouldCallFindIfMoreParametersAreAvailable() throws QueryBuilderException, InvalidTenantIdException {
        HouseholdMemberSearchRequest householdMemberSearchRequest = HouseholdMemberSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .householdMemberSearch(HouseholdMemberSearch.builder()
                        .id(Collections.singletonList("some-id")).householdId(Collections.singletonList("household-id")).build()).build();
        when(householdMemberRepository.find(any(HouseholdMemberSearch.class), anyInt(),
                anyInt(), anyString(), anyLong(), anyBoolean())).thenReturn(SearchResponse.<HouseholdMember>builder().build());

        householdMemberService.search(householdMemberSearchRequest.getHouseholdMemberSearch(), 10, 0,
                "default", 0L, false);

        verify(householdMemberRepository, times(1))
                .find(any(HouseholdMemberSearch.class), anyInt(),
                        anyInt(), anyString(), anyLong(), anyBoolean());
    }

}
