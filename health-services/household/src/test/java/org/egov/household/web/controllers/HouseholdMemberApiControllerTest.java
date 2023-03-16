package org.egov.household.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.household.HouseholdMemberRequest;
import org.egov.common.producer.Producer;
import org.egov.household.TestConfiguration;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.household.config.HouseholdMemberConfiguration;
import org.egov.household.helper.HouseholdMemberRequestTestBuilder;
import org.egov.household.service.HouseholdMemberService;
import org.egov.household.service.HouseholdService;
import org.egov.household.web.models.HouseholdMemberSearch;
import org.egov.household.web.models.HouseholdMemberSearchRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
* API tests for HouseholdApiController
*/
@WebMvcTest(HouseholdApiController.class)
@Import(TestConfiguration.class)
class HouseholdMemberApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HouseholdService householdService;

    @MockBean
    private HouseholdMemberService householdMemberService;

    @MockBean
    private Producer producer;

    @MockBean
    private HouseholdConfiguration householdConfiguration;

    @MockBean
    private HouseholdMemberConfiguration householdMemberConfiguration;

    @Test
    @DisplayName("should household member create request pass")
    void householdMemberV1CreatePostSuccess() throws Exception {
        HouseholdMemberRequest householdMemberRequest = HouseholdMemberRequestTestBuilder.builder().withHouseholdMember().withRequestInfo()
                .build();

        when(householdMemberService.create(any(HouseholdMemberRequest.class))).thenReturn(
                Collections.singletonList(householdMemberRequest.getHouseholdMember())
        );

        mockMvc.perform(post("/member/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdMemberRequest)))
                .andExpect(status().isAccepted());
    }


    @Test
    @DisplayName("should household member search request pass if all the required query parameters are present")
    void shouldSearchRequestPassIfQueryParamsArePresent() throws Exception {
        HouseholdMemberSearchRequest householdMemberSearchRequest = HouseholdMemberSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .householdMemberSearch(HouseholdMemberSearch.builder().build()).build();
        when(householdMemberService.search(any(HouseholdMemberSearch.class), anyInt(),
                anyInt(), anyString(), anyLong(), anyBoolean())).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/member/v1/_search?limit=10&offset=0&tenantId=default").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdMemberSearchRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should household member search request fail if the required query parameters are missing")
    void shouldSearchRequestPassIfQueryParamsAreMissing() throws Exception {
        HouseholdMemberSearchRequest householdMemberSearchRequest = HouseholdMemberSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .householdMemberSearch(HouseholdMemberSearch.builder().build()).build();
        when(householdMemberService.search(any(HouseholdMemberSearch.class), anyInt(),
                anyInt(), anyString(), anyLong(), anyBoolean())).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/member/v1/_search?limit=10&offset=0").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdMemberSearchRequest)))
                .andExpect(status().isBadRequest());
    }


    @Test
    @DisplayName("household member update request should pass")
    void shouldHouseholdMemberUpdateRequestPassForUpdateOperation() throws Exception {
        HouseholdMemberRequest householdMemberRequest = HouseholdMemberRequestTestBuilder.builder().withHouseholdMember().withRequestInfo()
                .build();
        when(householdMemberService.update(any(HouseholdMemberRequest.class))).thenReturn(
                Collections.singletonList(householdMemberRequest.getHouseholdMember())
        );
        mockMvc.perform(post("/member/v1/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdMemberRequest)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("household member delete request should pass")
    void shouldHouseholdMemberUpdateRequestPassForDeleteOperation() throws Exception {
        HouseholdMemberRequest householdMemberRequest = HouseholdMemberRequestTestBuilder.builder().withHouseholdMember().withRequestInfo()
                .build();

        when(householdMemberService.delete(any(HouseholdMemberRequest.class))).thenReturn(
                Collections.singletonList(householdMemberRequest.getHouseholdMember())
        );

        mockMvc.perform(post("/member/v1/_delete").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdMemberRequest)))
                .andExpect(status().isAccepted());
    }

}
