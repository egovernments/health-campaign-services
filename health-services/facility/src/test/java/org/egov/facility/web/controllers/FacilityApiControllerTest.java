package org.egov.facility.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.models.facility.FacilityBulkRequest;
import org.egov.common.models.facility.FacilityRequest;
import org.egov.common.models.facility.FacilityResponse;
import org.egov.common.producer.Producer;
import org.egov.facility.TestConfiguration;
import org.egov.facility.config.FacilityConfiguration;
import org.egov.facility.helper.FacilityBulkRequestTestBuilder;
import org.egov.facility.helper.FacilityRequestTestBuilder;
import org.egov.facility.helper.FacilityTestBuilder;
import org.egov.facility.service.FacilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
* API tests for FacilityApiController
*/
@WebMvcTest(FacilityApiController.class)
@Import(TestConfiguration.class)
public class FacilityApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FacilityService facilityService;

    @MockBean
    private Producer producer;

    @MockBean
    private FacilityConfiguration configuration;

    @BeforeEach
    void setUp() {
        when(configuration.getBulkCreateFacilityTopic()).thenReturn("facility-bulk-create-topic");
        when(configuration.getBulkUpdateFacilityTopic()).thenReturn("facility-bulk-update-topic");
        when(configuration.getBulkDeleteFacilityTopic()).thenReturn("facility-bulk-delete-topic");
    }

    @Test
    @DisplayName("should return facility response for create")
    void shouldReturnFacilityResponseForCreate() throws Exception {
        FacilityRequest request = FacilityRequestTestBuilder.builder().withFacility().withRequestInfo().build();
        when(facilityService.create(any(FacilityRequest.class))).thenReturn(FacilityTestBuilder.builder().withFacility().build());

        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        FacilityResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), FacilityResponse.class);
        assertNotNull(response.getFacility());
        verify(facilityService, times(1)).create(any(FacilityRequest.class));
    }

    @Test
    @DisplayName("should send facility bulk create request to kafka")
    void shouldSendFacilityToKafkaForBulkCreateRequest() throws Exception {
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder().withFacility().withRequestInfo().build();

        mockMvc.perform(post("/v1/bulk/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        verify(producer, times(1)).push(eq("default"), eq("facility-bulk-create-topic"), any(FacilityBulkRequest.class));
        verify(configuration, times(1)).getBulkCreateFacilityTopic();
    }

    @Test
    @DisplayName("should return facility response for update")
    void shouldReturnFacilityResponseForUpdate() throws Exception {
        FacilityRequest request = FacilityRequestTestBuilder.builder().withFacility().withRequestInfo().build();
        when(facilityService.update(any(FacilityRequest.class))).thenReturn(FacilityTestBuilder.builder().withFacility().build());

        MvcResult result = mockMvc.perform(post("/v1/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        FacilityResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), FacilityResponse.class);
        assertNotNull(response.getFacility());
        verify(facilityService, times(1)).update(any(FacilityRequest.class));
    }

    @Test
    @DisplayName("should send facility bulk update request to kafka")
    void shouldSendFacilityToKafkaForBulkUpdateRequest() throws Exception {
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder().withFacility().withRequestInfo().build();

        mockMvc.perform(post("/v1/bulk/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        verify(producer, times(1)).push(eq("default"), eq("facility-bulk-update-topic"), any(FacilityBulkRequest.class));
        verify(configuration, times(1)).getBulkUpdateFacilityTopic();
    }

    @Test
    @DisplayName("should return facility response for delete")
    void shouldReturnFacilityResponseForDelete() throws Exception {
        FacilityRequest request = FacilityRequestTestBuilder.builder().withFacility().withRequestInfo().build();
        when(facilityService.delete(any(FacilityRequest.class))).thenReturn(FacilityTestBuilder.builder().withFacility().build());

        MvcResult result = mockMvc.perform(post("/v1/_delete").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        FacilityResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), FacilityResponse.class);
        assertNotNull(response.getFacility());
        verify(facilityService, times(1)).delete(any(FacilityRequest.class));
    }

    @Test
    @DisplayName("should send facility bulk delete request to kafka")
    void shouldSendFacilityToKafkaForBulkDeleteRequest() throws Exception {
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder().withFacility().withRequestInfo().build();

        mockMvc.perform(post("/v1/bulk/_delete").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        verify(producer, times(1)).push(eq("default"), eq("facility-bulk-delete-topic"), any(FacilityBulkRequest.class));
        verify(configuration, times(1)).getBulkDeleteFacilityTopic();
    }

}
