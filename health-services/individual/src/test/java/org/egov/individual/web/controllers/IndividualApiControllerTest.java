package org.egov.individual.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.individual.TestConfiguration;
import org.egov.individual.helper.IndividualRequestTestBuilder;
import org.egov.individual.helper.IndividualSearchRequestTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.service.AddressService;
import org.egov.individual.service.IndividualService;
import org.egov.individual.web.models.ApiOperation;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.egov.individual.web.models.IndividualResponse;
import org.egov.individual.web.models.IndividualSearch;
import org.egov.individual.web.models.IndividualSearchRequest;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    @MockBean
    private AddressService addressService;

    @Test
    @DisplayName("should create an individual and return 202 accepted when api operation is create")
    void shouldCreateAnIndividualAndReturn202AcceptedWhenApiOperationIsCreate() throws Exception {
        Individual individual = IndividualTestBuilder.builder()
                .withName()
                .withTenantId()
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
        assertEquals(2, response.getErrors().size());
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

    @Test
    @DisplayName("should send 200 OK if search is successful and results are not empty")
    void shouldSend200OkIfSearchIsSuccessfulAndResultsAreNotEmpty() throws Exception {
        IndividualSearchRequest request = IndividualSearchRequestTestBuilder.builder()
                .withIndividualSearch()
                .withRequestInfo()
                .build();
        Individual responseIndividual = IndividualTestBuilder.builder()
                .withId()
                .build();
        when(individualService.search(any(IndividualSearch.class),
                any(Integer.class),
                any(Integer.class),
                any(String.class),
                any(Long.class),
                any(Boolean.class))).thenReturn(Collections.singletonList(responseIndividual));

        MvcResult result = mockMvc.perform(post("/v1/_search?limit=10&offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
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
    @DisplayName("should send 400 Bad Request in case validation error in search request")
    void shouldSend400BadRequestInCaseOfValidationErrorInSearchRequest() throws Exception {
        IndividualSearchRequest request = IndividualSearchRequestTestBuilder.builder()
                .withIndividualSearch()
                .withRequestInfo()
                .build();

        MvcResult result = mockMvc.perform(post("/v1/_search?offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType
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
    @DisplayName("should update an individual and return 202 accepted when api operation is update")
    void shouldUpdateAnIndividualAndReturn202AcceptedWhenApiOperationIsUpdate() throws Exception {
        Individual individual = IndividualTestBuilder.builder()
                .withName()
                .withTenantId()
                .build();
        Individual responseIndividual = IndividualTestBuilder.builder()
                .withId()
                .withName("some-new-family-name", "some-new-given-name")
                .build();
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withIndividuals(individual)
                .withRequestInfo()
                .withApiOperation(ApiOperation.UPDATE)
                .build();
        when(individualService.update(request)).thenReturn(Collections.singletonList(responseIndividual));

        MvcResult result = mockMvc.perform(post("/v1/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String responseStr = result.getResponse().getContentAsString();
        IndividualResponse response = objectMapper.readValue(responseStr,
                IndividualResponse.class);
        assertEquals(1, response.getIndividual().size());
        assertEquals("some-new-family-name", response.getIndividual().stream().findAny().get().getName().getFamilyName());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    @DisplayName("should delete an individual and return 202 accepted when api operation is delete")
    void shouldDeleteAnIndividualAndReturn202AcceptedWhenApiOperationIsDelete() throws Exception {
        Individual individual = IndividualTestBuilder.builder()
                .withName()
                .withTenantId()
                .build();
        Individual responseIndividual = IndividualTestBuilder.builder()
                .withId()
                .withName()
                .withIsDeleted(true)
                .build();
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withIndividuals(individual)
                .withRequestInfo()
                .withApiOperation(ApiOperation.UPDATE)
                .build();
        when(individualService.update(request)).thenReturn(Collections.singletonList(responseIndividual));

        MvcResult result = mockMvc.perform(post("/v1/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String responseStr = result.getResponse().getContentAsString();
        IndividualResponse response = objectMapper.readValue(responseStr,
                IndividualResponse.class);
        assertEquals(1, response.getIndividual().size());
        assertTrue(response.getIndividual().stream().findAny().get().getIsDeleted());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    @DisplayName("should send 400 bad request in case api operation is create for update")
    void shouldSend400BadRequestInCaseApiOperationIsCreateForUpdate() throws Exception {
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withIndividuals(IndividualTestBuilder.builder()
                        .withName()
                        .build())
                .withRequestInfo()
                .withApiOperation(ApiOperation.CREATE)
                .build();

        MvcResult result = mockMvc.perform(post("/v1/_update").contentType(MediaType
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
