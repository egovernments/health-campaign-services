package org.egov.product.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.product.TestConfiguration;
import org.egov.product.helper.ProductVariantRequestTestBuilder;
import org.egov.product.web.models.ProductVariantResponse;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API tests for ProductApiController
 */
@RunWith(SpringRunner.class)
@WebMvcTest(ProductApiController.class)
@Import(TestConfiguration.class)
public class ProductApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Ignore
    public void productV1CreatePostSuccess() throws Exception {
        mockMvc.perform(post("/product/v1/_create").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isOk());
    }

    @Test
    @Ignore
    public void productV1CreatePostFailure() throws Exception {
        mockMvc.perform(post("/product/v1/_create").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Ignore
    public void productV1SearchPostSuccess() throws Exception {
        mockMvc.perform(post("/product/v1/_search").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isOk());
    }

    @Test
    @Ignore
    public void productV1SearchPostFailure() throws Exception {
        mockMvc.perform(post("/product/v1/_search").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Ignore
    public void productV1UpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/product/v1/_update").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isOk());
    }

    @Test
    @Ignore
    public void productV1UpdatePostFailure() throws Exception {
        mockMvc.perform(post("/product/v1/_update").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should create product variant and return with 202 accepted")
    public void productVariantV1CreatePostSuccess() throws Exception {
        final MvcResult result = mockMvc.perform(post("/variant/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProductVariantRequestTestBuilder.builder()
                                .withOneProductVariantAndApiOperationNull()
                                .build())))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ProductVariantResponse response = objectMapper.readValue(responseStr,
                ProductVariantResponse.class);

        assertEquals(1, response.getProductVariant().size());
        assertNotNull(response.getProductVariant().get(0).getId());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    @Ignore
    public void productVariantV1CreatePostFailure() throws Exception {
        mockMvc.perform(post("/product/variant/v1/_create").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Ignore
    public void productVariantV1SearchPostSuccess() throws Exception {
        mockMvc.perform(post("/product/variant/v1/_search").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isOk());
    }

    @Test
    @Ignore
    public void productVariantV1SearchPostFailure() throws Exception {
        mockMvc.perform(post("/product/variant/v1/_search").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Ignore
    public void productVariantV1UpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/product/variant/v1/_update").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isOk());
    }

    @Test
    @Ignore
    public void productVariantV1UpdatePostFailure() throws Exception {
        mockMvc.perform(post("/product/variant/v1/_update").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isBadRequest());
    }

}
