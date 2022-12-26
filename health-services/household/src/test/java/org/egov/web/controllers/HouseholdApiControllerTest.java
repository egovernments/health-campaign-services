package org.egov.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.TestConfiguration;
import org.egov.helper.HouseholdRequestTestBuilder;
import org.egov.tracer.model.ErrorRes;
import org.egov.web.models.HouseholdRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    @DisplayName("household create request should pass if API Operation is create")
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
}
