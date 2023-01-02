package org.egov.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.TestConfiguration;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.helper.HouseholdRequestTestBuilder;
import org.egov.service.HouseholdService;
import org.egov.tracer.model.ErrorRes;
import org.egov.web.models.HouseholdRequest;
import org.egov.web.models.HouseholdSearch;
import org.egov.web.models.HouseholdSearchRequest;
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
class HouseholdApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HouseholdService householdService;

    @Test
    @DisplayName("should household create request pass if API Operation is create")
    void householdV1CreatePostSuccess() throws Exception {
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationCreate().build();

        mockMvc.perform(post("/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdRequest)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("should household create request fail if API Operation is not create")
    void householdV1CreatePostFailure() throws Exception {
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationDelete().build();

        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType
        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdRequest)))
        .andExpect(status().isBadRequest()).andReturn();

        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);
        assertEquals(1, response.getErrors().size());
        assertEquals("INVALID_API_OPERATION" ,response.getErrors().get(0).getCode());
    }

    @Test
    @DisplayName("should household search request pass if all the required query parameters are present")
    void shouldSearchRequestPassIfQueryParamsArePresent() throws Exception {
        HouseholdSearchRequest householdSearchRequest = HouseholdSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .household(HouseholdSearch.builder().build()).build();
        when(householdService.search(any(HouseholdSearchRequest.class), anyInt(),
                anyInt(), anyString(), anyLong(), anyBoolean())).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/v1/_search?limit=10&offset=0&tenantId=default").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdSearchRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should household search request fail if the required query parameters are missing")
    void shouldSearchRequestPassIfQueryParamsAreMissing() throws Exception {
        HouseholdSearchRequest householdSearchRequest = HouseholdSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .household(HouseholdSearch.builder().build()).build();
        when(householdService.search(any(HouseholdSearchRequest.class), anyInt(),
                anyInt(), anyString(), anyLong(), anyBoolean())).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/v1/_search?limit=10&offset=0").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdSearchRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("household update request should pass if API Operation is update")
    void shouldHouseholdUpdateRequestPassForUpdateOperation() throws Exception {
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationUpdate().build();

        mockMvc.perform(post("/v1/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdRequest)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("household update request should pass if API Operation is delete")
    void shouldHouseholdUpdateRequestPassForDeleteOperation() throws Exception {
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationDelete().build();

        mockMvc.perform(post("/v1/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdRequest)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("household update request should pass if API Operation is create")
    void shouldFailHouseholdUpdateRequestPassForCreateOperation() throws Exception {
        HouseholdRequest householdRequest = HouseholdRequestTestBuilder.builder().withHousehold().withRequestInfo()
                .withApiOperationCreate().build();

        mockMvc.perform(post("/v1/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(householdRequest)))
                .andExpect(status().isBadRequest());
    }
}
