package org.egov.individual.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.individual.TestConfiguration;
import org.egov.individual.helper.IndividualRequestTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.service.IndividualService;
import org.egov.individual.web.models.ApiOperation;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.egov.individual.web.models.IndividualResponse;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
* API tests for IndividualApiController
*/
@WebMvcTest(IndividualApiController.class)
@Import({TestConfiguration.class})
class IndividualApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IndividualService individualService;

    @Test
    @DisplayName("should create an individual and return 202 accepted when api operation is create")
    void shouldCreateAnIndividualAndReturn202AcceptedWhenApiOperationIsCreate() throws Exception {
        Individual individual = IndividualTestBuilder.builder()
                .withName()
                .build();
        Individual responseIndividual = IndividualTestBuilder.builder()
                .withId()
                .build();
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withIndividuals(individual)
                .withRequestInfo()
                .withApiOperation(ApiOperation.CREATE)
                .build();
        when(individualService.create(request)).thenReturn(Collections.singletonList(responseIndividual));

        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType
        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String responseStr = result.getResponse().getContentAsString();
        IndividualResponse response = objectMapper.readValue(responseStr,
                IndividualResponse.class);
        assertEquals(1, response.getIndividual().size());
        assertEquals("some-id", response.getIndividual().stream().findAny().get().getId());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    @DisplayName("should send 400 bad request in case of validation errors for create")
    void shouldSend400BadRequestInCaseOfValidationErrorsForCreate() throws Exception {
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withIndividuals(IndividualTestBuilder.builder()
                        .withNoPropertiesSet()
                        .build())
                .withRequestInfo()
                .withApiOperation(ApiOperation.CREATE)
                .build();

        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);
        assertEquals(1, response.getErrors().size());
    }

    @Test
    @DisplayName("should send 400 bad request in case api operation is other than create for create")
    void shouldSend400BadRequestInCaseApiOperationIsOtherThanCreateForCreate() throws Exception {
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withIndividuals(IndividualTestBuilder.builder()
                        .withName()
                        .build())
                .withRequestInfo()
                .withApiOperation(ApiOperation.UPDATE)
                .build();

        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);
        assertEquals(1, response.getErrors().size());
    }
}
