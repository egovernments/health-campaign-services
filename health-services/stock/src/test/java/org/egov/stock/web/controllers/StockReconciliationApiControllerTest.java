package org.egov.stock.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.common.models.stock.StockReconciliationRequest;
import org.egov.common.models.stock.StockReconciliationResponse;
import org.egov.common.producer.Producer;
import org.egov.stock.TestConfiguration;
import org.egov.stock.config.StockReconciliationConfiguration;
import org.egov.stock.helper.StockReconciliationBulkRequestTestBuilder;
import org.egov.stock.helper.StockReconciliationRequestTestBuilder;
import org.egov.stock.helper.StockReconciliationTestBuilder;
import org.egov.stock.service.StockReconciliationService;
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
@WebMvcTest(StockReconciliationApiController.class)
@Import(TestConfiguration.class)
class StockReconciliationApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StockReconciliationService stockReconciliationService;

    @MockBean
    private Producer producer;

    @MockBean
    private StockReconciliationConfiguration configuration;

    @BeforeEach
    void setUp() {
        when(configuration.getBulkCreateStockReconciliationTopic()).thenReturn("stock-bulk-create-topic");
        when(configuration.getBulkUpdateStockReconciliationTopic()).thenReturn("stock-bulk-update-topic");
        when(configuration.getBulkDeleteStockReconciliationTopic()).thenReturn("stock-bulk-delete-topic");
    }

    @Test
    @DisplayName("should return stock reconciliation response for create")
    void shouldReturnStockResponseForCreate() throws Exception {
        StockReconciliationRequest request = StockReconciliationRequestTestBuilder.builder()
                .withReconciliation().withRequestInfo().build();
        when(stockReconciliationService.create(any(StockReconciliationRequest.class)))
                .thenReturn(StockReconciliationTestBuilder.builder().withStock().build());

        MvcResult result = mockMvc.perform(post("/reconciliation/v1/_create").contentType(MediaType
        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isAccepted()).andReturn();

        StockReconciliationResponse response = objectMapper.readValue(result.getResponse().getContentAsString(),
                StockReconciliationResponse.class);
        assertNotNull(response.getStockReconciliation());
        verify(stockReconciliationService, times(1)).create(any(StockReconciliationRequest.class));
    }

    @Test
    @DisplayName("should send stock reconciliation bulk create request to kafka")
    void shouldSendStockToKafkaForBulkCreateRequest() throws Exception {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStock().withRequestInfo().build();

        mockMvc.perform(post("/reconciliation/v1/bulk/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        verify(producer, times(1))
                .push(eq("stock-bulk-create-topic"), any(StockReconciliationBulkRequest.class));
        verify(configuration, times(1)).getBulkCreateStockReconciliationTopic();
    }

    @Test
    @DisplayName("should return stock reconciliation response for update")
    void shouldReturnStockResponseForUpdate() throws Exception {
        StockReconciliationRequest request = StockReconciliationRequestTestBuilder.builder()
                .withReconciliation().withRequestInfo().build();
        when(stockReconciliationService.update(any(StockReconciliationRequest.class)))
                .thenReturn(StockReconciliationTestBuilder.builder().withStock().build());


        MvcResult result = mockMvc.perform(post("/reconciliation/v1/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        StockReconciliationResponse response = objectMapper.readValue(result.getResponse().getContentAsString(),
                StockReconciliationResponse.class);
        assertNotNull(response.getStockReconciliation());
        verify(stockReconciliationService, times(1)).update(any(StockReconciliationRequest.class));
    }

    @Test
    @DisplayName("should send stock bulk update request to kafka")
    void shouldSendStockToKafkaForBulkUpdateRequest() throws Exception {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStock().withRequestInfo().build();

        mockMvc.perform(post("/reconciliation/v1/bulk/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        verify(producer, times(1)).push(eq("stock-bulk-update-topic"),
                any(StockReconciliationBulkRequest.class));
        verify(configuration, times(1)).getBulkUpdateStockReconciliationTopic();
    }

    @Test
    @DisplayName("should return stock response for delete")
    void shouldReturnStockResponseForDelete() throws Exception {
        StockReconciliationRequest request = StockReconciliationRequestTestBuilder.builder()
                .withReconciliation().withRequestInfo().build();
        when(stockReconciliationService.delete(any(StockReconciliationRequest.class)))
                .thenReturn(StockReconciliationTestBuilder.builder().withStock().build());

        MvcResult result = mockMvc.perform(post("/reconciliation/v1/_delete").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        StockReconciliationResponse response = objectMapper.readValue(result.getResponse().getContentAsString(),
                StockReconciliationResponse.class);
        assertNotNull(response.getStockReconciliation());
        verify(stockReconciliationService, times(1)).delete(any(StockReconciliationRequest.class));
    }

    @Test
    @DisplayName("should send stock bulk delete request to kafka")
    void shouldSendStockToKafkaForBulkDeleteRequest() throws Exception {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStock().withRequestInfo().build();

        mockMvc.perform(post("/reconciliation/v1/bulk/_delete").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        verify(producer, times(1)).push(eq("stock-bulk-delete-topic"), any(StockReconciliationBulkRequest.class));
        verify(configuration, times(1)).getBulkDeleteStockReconciliationTopic();
    }
}
