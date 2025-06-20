package org.egov.servicerequest.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.servicerequest.TestConfiguration;
import org.egov.servicerequest.config.Configuration;
import org.egov.servicerequest.helper.ServiceRequestTestBuilder;
import org.egov.servicerequest.helper.ServiceTestBuilder;
import org.egov.servicerequest.kafka.Producer;
import org.egov.servicerequest.service.ServiceRequestService;
import org.egov.servicerequest.util.ResponseInfoFactory;
import org.egov.servicerequest.web.models.ServiceRequest;
import org.egov.servicerequest.web.models.ServiceResponse;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * API tests for ServiceController
 */
@WebMvcTest(ServiceController.class)
@Import(TestConfiguration.class)
public class ServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private Configuration configuration;

    @MockBean
    private ServiceRequestService serviceRequestService;

    @MockBean
    private ResponseInfoFactory responseInfoFactory;

    @MockBean
    private Producer producer;


    @BeforeEach
    void setUp(){
        when(configuration.getServiceCreateTopic()).thenReturn("save-service");
    }


    @Test
    @DisplayName("should return service response for create")
    void shouldReturnServiceResponseForCreate() throws Exception {
        ServiceRequest request= ServiceRequestTestBuilder.builder().withServices().withRequestInfo().build();
        when(serviceRequestService.createService(any(ServiceRequest.class))).
                thenReturn(ServiceTestBuilder.builder().withService().build());

        MvcResult result=mockMvc.perform(post("/service/v1/_create").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn();
        ServiceResponse response=objectMapper.readValue(result.getResponse().getContentAsString(),ServiceResponse.class);

        assertNotNull(response.getService());
        verify(serviceRequestService,times(1)).createService(any(ServiceRequest.class));
    }



}
