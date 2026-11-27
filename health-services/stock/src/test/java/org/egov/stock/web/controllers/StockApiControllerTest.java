package org.egov.stock.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.models.stock.StockRequest;
import org.egov.common.models.stock.StockResponse;
import org.egov.common.producer.Producer;
import org.egov.stock.TestConfiguration;
import org.egov.stock.config.StockConfiguration;
import org.egov.stock.helper.StockBulkRequestTestBuilder;
import org.egov.stock.helper.StockRequestTestBuilder;
import org.egov.stock.helper.StockTestBuilder;
import org.egov.stock.service.StockReconciliationService;
import org.egov.stock.service.StockService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    @MockBean
    private Producer producer;

    @MockBean
    private StockConfiguration configuration;

    @BeforeEach
    void setUp() {
        when(configuration.getBulkCreateStockTopic()).thenReturn("stock-bulk-create-topic");
        when(configuration.getBulkUpdateStockTopic()).thenReturn("stock-bulk-update-topic");
        when(configuration.getBulkDeleteStockTopic()).thenReturn("stock-bulk-delete-topic");
    }

    @Test
    @DisplayName("should return stock response for create")
    void shouldReturnStockResponseForCreate() throws Exception {
        StockRequest request = StockRequestTestBuilder.builder().withStock().withRequestInfo().build();
        when(stockService.create(any(StockRequest.class))).thenReturn(StockTestBuilder.builder().withStock().build());

        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType
        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isAccepted()).andReturn();

        StockResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), StockResponse.class);
        assertNotNull(response.getStock());
        verify(stockService, times(1)).create(any(StockRequest.class));
    }

    @Test
    @DisplayName("should send stock bulk create request to kafka")
    void shouldSendStockToKafkaForBulkCreateRequest() throws Exception {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        mockMvc.perform(post("/v1/bulk/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        verify(producer, times(1)).push(eq("stock-bulk-create-topic"), any(StockBulkRequest.class));
        verify(configuration, times(1)).getBulkCreateStockTopic();
    }

    @Test
    @DisplayName("should return stock response for update")
    void shouldReturnStockResponseForUpdate() throws Exception {
        StockRequest request = StockRequestTestBuilder.builder().withStock().withRequestInfo().build();
        when(stockService.update(any(StockRequest.class))).thenReturn(StockTestBuilder.builder().withStock().build());

        MvcResult result = mockMvc.perform(post("/v1/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        StockResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), StockResponse.class);
        assertNotNull(response.getStock());
        verify(stockService, times(1)).update(any(StockRequest.class));
    }

    @Test
    @DisplayName("should send stock bulk update request to kafka")
    void shouldSendStockToKafkaForBulkUpdateRequest() throws Exception {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        mockMvc.perform(post("/v1/bulk/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        verify(producer, times(1)).push(eq("stock-bulk-update-topic"), any(StockBulkRequest.class));
        verify(configuration, times(1)).getBulkUpdateStockTopic();
    }

    @Test
    @DisplayName("should return stock response for delete")
    void shouldReturnStockResponseForDelete() throws Exception {
        StockRequest request = StockRequestTestBuilder.builder().withStock().withRequestInfo().build();
        when(stockService.delete(any(StockRequest.class))).thenReturn(StockTestBuilder.builder().withStock().build());

        MvcResult result = mockMvc.perform(post("/v1/_delete").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        StockResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), StockResponse.class);
        assertNotNull(response.getStock());
        verify(stockService, times(1)).delete(any(StockRequest.class));
    }

    @Test
    @DisplayName("should send stock bulk delete request to kafka")
    void shouldSendStockToKafkaForBulkDeleteRequest() throws Exception {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        mockMvc.perform(post("/v1/bulk/_delete").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        verify(producer, times(1)).push(eq("stock-bulk-delete-topic"), any(StockBulkRequest.class));
        verify(configuration, times(1)).getBulkDeleteStockTopic();
    }
}
