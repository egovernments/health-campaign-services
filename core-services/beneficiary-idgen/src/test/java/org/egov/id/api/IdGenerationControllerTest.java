package org.egov.id.api;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;

import jakarta.servlet.http.HttpServletRequest;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.idgen.*;
import org.egov.id.config.PropertiesManager;
import org.egov.id.producer.IdGenProducer;
import org.egov.id.service.IdDispatchService;
import org.egov.id.service.IdGenerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ContextConfiguration(classes = {IdGenerationController.class})
@ExtendWith(SpringExtension.class)
class IdGenerationControllerTest {

    @Autowired
    private IdGenerationController idGenerationController;

    @MockBean
    private IdGenerationService idGenerationService;

    @MockBean
    private IdDispatchService idDispatchService;  

    @MockBean
    private HttpServletRequest servletRequest;

    @MockBean
    PropertiesManager propertiesManager;

    @MockBean
    IdGenProducer producer;

    @Test
    void testGenerateIdResponse() throws Exception {
        IdGenerationRequest idGenerationRequest = new IdGenerationRequest();
        idGenerationRequest.setIdRequests(new ArrayList<>());
        idGenerationRequest.setRequestInfo(new RequestInfo());

        String content = (new ObjectMapper()).writeValueAsString(idGenerationRequest);
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.post("/_generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);

        ResultActions actualPerformResult = MockMvcBuilders.standaloneSetup(this.idGenerationController)
                .build()
                .perform(requestBuilder);

        actualPerformResult.andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void testIdPoolGenerateIDs() throws Exception {
        IDPoolGenerationRequest idPoolGenerationRequest = new IDPoolGenerationRequest();
        idPoolGenerationRequest.setRequestInfo(new RequestInfo());
        idPoolGenerationRequest.setBatchRequestList(new ArrayList<>());

        String content = (new ObjectMapper()).writeValueAsString(idPoolGenerationRequest);
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.post("/id_pool/_generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);

        ResultActions actualPerformResult = MockMvcBuilders.standaloneSetup(this.idGenerationController)
                .build()
                .perform(requestBuilder);

        actualPerformResult.andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void testIdPoolDispatchIDs() throws Exception {
        IdDispatchRequest idDispatchRequest = new IdDispatchRequest();
        idDispatchRequest.setRequestInfo(new RequestInfo());
        idDispatchRequest.setClientInfo(new ClientInfo());

        String content = (new ObjectMapper()).writeValueAsString(idDispatchRequest);
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.post("/id_pool/_dispatch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);

        ResultActions actualPerformResult = MockMvcBuilders.standaloneSetup(this.idGenerationController)
                .build()
                .perform(requestBuilder);

        actualPerformResult.andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void testIdPoolSearchGeneratedIDs() throws Exception {
        IdPoolSearchRequest idPoolSearchRequest = new IdPoolSearchRequest();
        idPoolSearchRequest.setRequestInfo(new RequestInfo());
        idPoolSearchRequest.setIdPoolSearch(new IdPoolSearch());

        String content = (new ObjectMapper()).writeValueAsString(idPoolSearchRequest);
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.post("/id_pool/_search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);

        ResultActions actualPerformResult = MockMvcBuilders.standaloneSetup(this.idGenerationController)
                .build()
                .perform(requestBuilder);

        actualPerformResult.andExpect(MockMvcResultMatchers.status().isNotFound());
    }


    @Test
    void testIdRecordUpdate() throws Exception {
        IdRecordBulkRequest idRecordBulkRequest = new IdRecordBulkRequest();
        idRecordBulkRequest.setRequestInfo(new RequestInfo());
        idRecordBulkRequest.setIdRecords(new ArrayList<>());

        String content = (new ObjectMapper()).writeValueAsString(idRecordBulkRequest);
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.post("/id_pool/_update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);

        ResultActions actualPerformResult = MockMvcBuilders.standaloneSetup(this.idGenerationController)
                .build()
                .perform(requestBuilder);

        actualPerformResult.andExpect(MockMvcResultMatchers.status().isNotFound());
    }

}
