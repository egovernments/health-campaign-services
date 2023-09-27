package org.egov.referralmanagement.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.referralmanagement.TestConfiguration;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.helper.AdverseEventRequestTestBuilder;
import org.egov.referralmanagement.helper.AdverseEventTestBuilder;
import org.egov.referralmanagement.service.AdverseEventService;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.referralmanagement.adverseevent.AdverseEvent;
import org.egov.common.models.referralmanagement.adverseevent.AdverseEventBulkResponse;
import org.egov.common.models.referralmanagement.adverseevent.AdverseEventRequest;
import org.egov.common.models.referralmanagement.adverseevent.AdverseEventResponse;
import org.egov.common.models.referralmanagement.adverseevent.AdverseEventSearch;
import org.egov.common.models.referralmanagement.adverseevent.AdverseEventSearchRequest;
import org.egov.common.producer.Producer;
import org.egov.tracer.model.CustomException;
import org.egov.tracer.model.ErrorRes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@WebMvcTest(AdverseEventApiController.class)
@Import(TestConfiguration.class)
public class AdverseEventApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdverseEventService adverseEventService;

    @MockBean
    private Producer producer;

    @MockBean
    ReferralManagementConfiguration referralManagementConfiguration;

    @Test
    @DisplayName("should create adverse event and return with 202 accepted")
    void shouldCreateAdverseEventAndReturnWith202Accepted() throws Exception {
        AdverseEventRequest request = AdverseEventRequestTestBuilder.builder()
                .withOneAdverseEvent()
                .withApiOperationNotUpdate()
                .build();
        List<AdverseEvent> adverseEvents = getAdverseEvents();
        Mockito.when(adverseEventService.create(ArgumentMatchers.any(AdverseEventRequest.class))).thenReturn(adverseEvents.get(0));

        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/adverse_event/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        AdverseEventResponse response = objectMapper.readValue(responseStr, AdverseEventResponse.class);

        Assertions.assertNotNull(response.getAdverseEvent());
        Assertions.assertNotNull(response.getAdverseEvent().getId());
        Assertions.assertEquals("successful", response.getResponseInfo().getStatus());
    }

    private List<AdverseEvent> getAdverseEvents() {
        AdverseEvent adverseEvent = AdverseEventTestBuilder.builder().withId().build();
        List<AdverseEvent> adverseEvents = new ArrayList<>();
        adverseEvents.add(adverseEvent);
        return adverseEvents;
    }


    @Test
    @DisplayName("should send error response with error details with 400 bad request for create")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForCreate() throws Exception {
        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/adverse_event/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdverseEventRequestTestBuilder.builder()
                                .withOneAdverseEvent()
                                .withBadTenantIdInOneAdverseEvent()
                                .build())))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr, ErrorRes.class);

        Assertions.assertEquals(1, response.getErrors().size());
        Assertions.assertTrue(response.getErrors().get(0).getCode().contains("tenantId"));
    }

    @Test
    @DisplayName("should update adverse event and return with 202 accepted")
    void shouldUpdateAdverseEventAndReturnWith202Accepted() throws Exception {
        AdverseEventRequest request = AdverseEventRequestTestBuilder.builder()
                .withOneAdverseEventHavingId()
                .withApiOperationNotNullAndNotCreate()
                .build();
        AdverseEvent adverseEvent = AdverseEventTestBuilder.builder().withId().build();
        Mockito.when(adverseEventService.update(ArgumentMatchers.any(AdverseEventRequest.class))).thenReturn(adverseEvent);

        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/adverse_event/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        AdverseEventResponse response = objectMapper.readValue(responseStr, AdverseEventResponse.class);

        Assertions.assertNotNull(response.getAdverseEvent());
        Assertions.assertNotNull(response.getAdverseEvent().getId());
        Assertions.assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    @DisplayName("should send error response with error details with 400 bad request for update")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForUpdate() throws Exception {
        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/adverse_event/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdverseEventRequestTestBuilder.builder()
                                .withOneAdverseEventHavingId()
                                .withBadTenantIdInOneAdverseEvent()
                                .build())))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        Assertions.assertEquals(1, response.getErrors().size());
        Assertions.assertTrue(response.getErrors().get(0).getCode().contains("tenantId"));
    }
    
    @Test
    @DisplayName("Should accept search request and return response as accepted")
    void shouldAcceptSearchRequestAndReturnAdverseEvent() throws Exception {

        AdverseEventSearchRequest adverseEventSearchRequest = AdverseEventSearchRequest.builder().adverseEvent(
                AdverseEventSearch.builder().taskId("some-task-id").build()
        ).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        Mockito.when(adverseEventService.search(ArgumentMatchers.any(AdverseEventSearchRequest.class),
                ArgumentMatchers.any(Integer.class),
                ArgumentMatchers.any(Integer.class),
                ArgumentMatchers.any(String.class),
                ArgumentMatchers.any(Long.class),
                ArgumentMatchers.any(Boolean.class))).thenReturn(Arrays.asList(AdverseEventTestBuilder.builder().withId().withAuditDetails().build()));

        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post(
                        "/adverse_event/v1/_search?limit=10&offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adverseEventSearchRequest)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        AdverseEventBulkResponse response = objectMapper.readValue(responseStr,
                AdverseEventBulkResponse.class);

        Assertions.assertEquals(response.getAdverseEvents().size(), 1);
    }

    @Test
    @DisplayName("Should accept search request and return response as accepted")
    void shouldThrowExceptionIfNoResultFound() throws Exception {

        AdverseEventSearchRequest adverseEventSearchRequest = AdverseEventSearchRequest.builder().adverseEvent(
                AdverseEventSearch.builder().taskId("some-task-id").build()
        ).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        Mockito.when(adverseEventService.search(ArgumentMatchers.any(AdverseEventSearchRequest.class),
                ArgumentMatchers.any(Integer.class),
                ArgumentMatchers.any(Integer.class),
                ArgumentMatchers.any(String.class),
                ArgumentMatchers.any(Long.class),
                ArgumentMatchers.any(Boolean.class))).thenThrow(new CustomException("NO_RESULT_FOUND", "No Adverse Event found."));


        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/adverse_event/v1/_search?limit=10&offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adverseEventSearchRequest)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        Assertions.assertEquals(response.getErrors().size(), 1);
    }

}
