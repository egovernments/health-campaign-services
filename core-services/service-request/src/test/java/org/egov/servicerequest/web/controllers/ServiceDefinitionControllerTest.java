package org.egov.servicerequest.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.servicerequest.TestConfiguration;
import org.egov.servicerequest.config.Configuration;
import org.egov.servicerequest.helper.ServiceDefinitionRequestTestBuilder;
import org.egov.servicerequest.helper.ServiceDefinitionTestBuilder;
import org.egov.servicerequest.kafka.Producer;
import org.egov.servicerequest.service.ServiceDefinitionRequestService;
import org.egov.servicerequest.util.ResponseInfoFactory;
import org.egov.servicerequest.web.models.ServiceDefinitionRequest;
import org.egov.servicerequest.web.models.ServiceDefinitionResponse;
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

@WebMvcTest(ServiceDefinitionController.class)
@Import(TestConfiguration.class)
public class ServiceDefinitionControllerTest {
    
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private Configuration configuration;

    @MockBean
    private ServiceDefinitionRequestService serviceDefinitionRequestService;

    @MockBean
    private ResponseInfoFactory responseInfoFactory;

    @MockBean
    private Producer producer;


    @BeforeEach
    void setUp(){
        when(configuration.getServiceDefinitionCreateTopic()).thenReturn("save-service-definition");
    }


    @Test
    @DisplayName("should return service definition response for create")
    void shouldReturnServiceDefinitionResponseForCreate() throws Exception {
        ServiceDefinitionRequest request= ServiceDefinitionRequestTestBuilder.builder().withServiceDefinition().withRequestInfo().build();
        when(serviceDefinitionRequestService.createServiceDefinition(any(ServiceDefinitionRequest.class))).
                thenReturn(ServiceDefinitionTestBuilder.builder().withServiceDefinition().build());

        MvcResult result=mockMvc.perform(post("/service/definition/v1/_create").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn();
        ServiceDefinitionResponse response=objectMapper.readValue(result.getResponse().getContentAsString(),ServiceDefinitionResponse.class);

        assertNotNull(response.getServiceDefinition());
        verify(serviceDefinitionRequestService,times(1)).createServiceDefinition(any(ServiceDefinitionRequest.class));
    }


}
