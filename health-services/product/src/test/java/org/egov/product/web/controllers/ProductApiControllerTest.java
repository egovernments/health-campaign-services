package org.egov.product.web.controllers;

import org.egov.product.TestConfiguration;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API tests for ProductApiController
 */
@Ignore
@RunWith(SpringRunner.class)
@WebMvcTest(ProductApiController.class)
@Import(TestConfiguration.class)
public class ProductApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void productV1CreatePostSuccess() throws Exception {
        mockMvc.perform(post("/product/v1/_create").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isOk());
    }

    @Test
    public void productV1CreatePostFailure() throws Exception {
        mockMvc.perform(post("/product/v1/_create").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void productV1SearchPostSuccess() throws Exception {
        mockMvc.perform(post("/product/v1/_search").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isOk());
    }

    @Test
    public void productV1SearchPostFailure() throws Exception {
        mockMvc.perform(post("/product/v1/_search").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void productV1UpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/product/v1/_update").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isOk());
    }

    @Test
    public void productV1UpdatePostFailure() throws Exception {
        mockMvc.perform(post("/product/v1/_update").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void productVariantV1CreatePostSuccess() throws Exception {
        mockMvc.perform(post("/product/variant/v1/_create").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isOk());
    }

    @Test
    public void productVariantV1CreatePostFailure() throws Exception {
        mockMvc.perform(post("/product/variant/v1/_create").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void productVariantV1SearchPostSuccess() throws Exception {
        mockMvc.perform(post("/product/variant/v1/_search").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isOk());
    }

    @Test
    public void productVariantV1SearchPostFailure() throws Exception {
        mockMvc.perform(post("/product/variant/v1/_search").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void productVariantV1UpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/product/variant/v1/_update").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isOk());
    }

    @Test
    public void productVariantV1UpdatePostFailure() throws Exception {
        mockMvc.perform(post("/product/variant/v1/_update").contentType(MediaType
                        .APPLICATION_JSON_UTF8))
                .andExpect(status().isBadRequest());
    }

}
