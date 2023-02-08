package org.egov.stock.web.controllers;

import org.egov.stock.TestConfiguration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
* API tests for StockApiController
*/
@WebMvcTest(StockApiController.class)
@Import(TestConfiguration.class)
public class StockApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
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
    @Disabled
    public void stockV1CreatePostSuccess() throws Exception {
        mockMvc.perform(post("/stock/v1/_create").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
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
