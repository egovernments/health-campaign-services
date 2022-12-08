package org.egov.product.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.product.helper.ProductRequestTestBuilder;
import org.egov.product.helper.ProductTestBuilder;
import org.egov.product.service.ProductService;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;

import org.egov.tracer.KafkaConsumerErrorHandler;
import org.egov.tracer.config.TracerConfiguration;
import org.egov.tracer.kafka.ErrorQueueProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * API tests for ProductApiController
 */
//@Ignore
@WebMvcTest(ProductApiController.class)
@Import({ TracerConfiguration.class })
public class ProductApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @MockBean
    private ProductService productService;

    @MockBean
    private KafkaConsumerErrorHandler kafkaConsumerErrorHandler;

    @MockBean
    private ErrorQueueProducer errorQueueProducer;
    @Test
    public void productRequestShouldFailForInvalidApiOperation() throws Exception {
        ProductRequest productRequest = ProductRequestTestBuilder.builder().addGoodProduct().withApiOperationDelete().withRequestInfo().build();
        String request = objectMapper.writeValueAsString(
          productRequest
        );
        mockMvc.perform(post("/product/v1/_create").contentType(MediaType
                        .APPLICATION_JSON_UTF8).content(request))
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
