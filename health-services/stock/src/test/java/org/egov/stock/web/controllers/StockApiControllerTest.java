package org.egov.stock.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.stock.TestConfiguration;
import org.egov.stock.helper.StockRequestTestBuilder;
import org.egov.stock.helper.StockTestBuilder;
import org.egov.stock.service.StockReconciliationService;
import org.egov.stock.service.StockService;
import org.egov.stock.web.models.StockRequest;
import org.egov.stock.web.models.StockResponse;
import org.junit.jupiter.api.Disabled;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
* API tests for StockApiController
*/
@WebMvcTest(StockApiController.class)
@Import(TestConfiguration.class)
class StockApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StockService stockService;

    @MockBean
    private StockReconciliationService stockReconciliationService;

    @Test
    @DisplayName("should return stock response")
    @Disabled
    public void stockReconciliationV1CreatePostSuccess() throws Exception {
        mockMvc.perform(post("/stock/reconciliation/v1/_create").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void stockReconciliationV1CreatePostFailure() throws Exception {
        mockMvc.perform(post("/stock/reconciliation/v1/_create").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void stockReconciliationV1SearchPostSuccess() throws Exception {
        mockMvc.perform(post("/stock/reconciliation/v1/_search").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void stockReconciliationV1SearchPostFailure() throws Exception {
        mockMvc.perform(post("/stock/reconciliation/v1/_search").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void stockReconciliationV1UpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/stock/reconciliation/v1/_update").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void stockReconciliationV1UpdatePostFailure() throws Exception {
        mockMvc.perform(post("/stock/reconciliation/v1/_update").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return stock response")
    void stockV1CreatePostSuccess() throws Exception {
        StockRequest request = StockRequestTestBuilder.builder().withStock().withRequestInfo().build();
        when(stockService.create(any(StockRequest.class))).thenReturn(StockTestBuilder.builder().withStock().build());

        MvcResult result = mockMvc.perform(post("/stock/v1/_create").contentType(MediaType
        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isAccepted()).andReturn();

        StockResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), StockResponse.class);
        assertNotNull(response.getStock());
    }

    @Test
    @Disabled
    public void stockV1CreatePostFailure() throws Exception {
        mockMvc.perform(post("/stock/v1/_create").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void stockV1SearchPostSuccess() throws Exception {
        mockMvc.perform(post("/stock/v1/_search").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void stockV1SearchPostFailure() throws Exception {
        mockMvc.perform(post("/stock/v1/_search").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void stockV1UpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/stock/v1/_update").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void stockV1UpdatePostFailure() throws Exception {
        mockMvc.perform(post("/stock/v1/_update").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

}
