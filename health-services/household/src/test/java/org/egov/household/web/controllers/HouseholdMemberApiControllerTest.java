package org.egov.household.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.household.TestConfiguration;
import org.egov.household.helper.HouseholdMemberRequestTestBuilder;
import org.egov.household.helper.HouseholdRequestTestBuilder;
import org.egov.household.service.HouseholdMemberService;
import org.egov.household.service.HouseholdService;
import org.egov.household.web.models.HouseholdMemberRequest;
import org.egov.household.web.models.HouseholdMemberSearch;
import org.egov.household.web.models.HouseholdMemberSearchRequest;
import org.egov.tracer.model.ErrorRes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    @DisplayName("should household member create request pass if API Operation is create")
    void householdMemberV1CreatePostSuccess() throws Exception {
        HouseholdMemberRequest householdMemberRequest = HouseholdMemberRequestTestBuilder.builder().withHouseholdMember().withRequestInfo()
                .withApiOperationCreate().build();

        mockMvc.perform(post("/member/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdMemberRequest)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("should household member create request fail if API Operation is not create")
    void householdMemberV1CreatePostFailure() throws Exception {
        HouseholdMemberRequest householdMemberRequest = HouseholdMemberRequestTestBuilder.builder().withHouseholdMember().withRequestInfo()
                .withApiOperationDelete().build();

        MvcResult result = mockMvc.perform(post("/member/v1/_create").contentType(MediaType
        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdMemberRequest)))
        .andExpect(status().isBadRequest()).andReturn();

        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);
        assertEquals(1, response.getErrors().size());
        assertEquals("INVALID_API_OPERATION" ,response.getErrors().get(0).getCode());
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
    @DisplayName("household member update request should pass if API Operation is update")
    void shouldHouseholdMemberUpdateRequestPassForUpdateOperation() throws Exception {
        HouseholdMemberRequest householdMemberRequest = HouseholdMemberRequestTestBuilder.builder().withHouseholdMember().withRequestInfo()
                .withApiOperationUpdate().build();

        mockMvc.perform(post("/member/v1/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdMemberRequest)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("household member update request should pass if API Operation is delete")
    void shouldHouseholdMemberUpdateRequestPassForDeleteOperation() throws Exception {
        HouseholdMemberRequest householdMemberRequest = HouseholdMemberRequestTestBuilder.builder().withHouseholdMember().withRequestInfo()
                .withApiOperationDelete().build();

        mockMvc.perform(post("/member/v1/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdMemberRequest)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("household member update request should pass if API Operation is create")
    void shouldFailHouseholdMemberUpdateRequestPassForCreateOperation() throws Exception {
        HouseholdMemberRequest householdMemberRequest = HouseholdMemberRequestTestBuilder.builder().withHouseholdMember().withRequestInfo()
                .withApiOperationCreate().build();

        mockMvc.perform(post("/member/v1/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdMemberRequest)))
                .andExpect(status().isBadRequest());
    }
}
