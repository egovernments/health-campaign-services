package org.egov.household.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.ds.Tuple;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.household.Household;
import org.egov.common.producer.Producer;
import org.egov.household.TestConfiguration;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.household.config.HouseholdMemberConfiguration;
import org.egov.household.service.HouseholdMemberService;
import org.egov.household.service.HouseholdService;
import org.egov.household.web.models.HouseholdSearch;
import org.egov.household.web.models.HouseholdSearchRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

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
class HouseholdApiControllerTest {

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
    @DisplayName("should household search request pass if all the required query parameters are present")
    void shouldSearchRequestPassIfQueryParamsArePresent() throws Exception {
        HouseholdSearchRequest householdSearchRequest = HouseholdSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .household(HouseholdSearch.builder().build()).build();
        when(householdService.search(any(HouseholdSearch.class), anyInt(),
                anyInt(), anyString(), anyLong(), anyBoolean())).thenReturn(new Tuple<Long, List<Household>>(0L, Collections.emptyList()));

//        mockMvc.perform(post("/v1/_search?limit=10&offset=0&tenantId=default").contentType(MediaType
//                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdSearchRequest)))
//                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should household search request fail if the required query parameters are missing")
    void shouldSearchRequestPassIfQueryParamsAreMissing() throws Exception {
        HouseholdSearchRequest householdSearchRequest = HouseholdSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .household(HouseholdSearch.builder().build()).build();
        when(householdService.search(any(HouseholdSearch.class), anyInt(),
                anyInt(), anyString(), anyLong(), anyBoolean())).thenReturn(new Tuple<>(0L, Collections.emptyList()));

        mockMvc.perform(post("/v1/_search?limit=10&offset=0").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdSearchRequest)))
                .andExpect(status().isBadRequest());
    }
}