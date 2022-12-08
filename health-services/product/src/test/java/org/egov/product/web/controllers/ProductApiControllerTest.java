package org.egov.product.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.service.IdGenService;
import org.egov.product.TestConfiguration;
import org.egov.product.helper.ProductRequestTestBuilder;
import org.egov.product.helper.ProductTestBuilder;
import org.egov.product.service.ProductService;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;
import org.egov.product.web.models.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API tests for ProductApiController
 */
@WebMvcTest(ProductApiController.class)
@Import(TestConfiguration.class)
public class ProductApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @Test
    @DisplayName("Product Request should fail for incorrect API operation")
    public void productRequestForCreateShouldFailForIncorrectApiOperation() throws Exception {
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addGoodProduct().withApiOperationDelete().build();

        String expectedResponse = "{\"ResponseInfo\":null,\"Errors\":[{\"code\":\"INVALID_API_OPERATION\",\"message\":\"API Operation DELETE not valid for create request\",\"description\":null,\"params\":null}]}";

        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType
                        .APPLICATION_JSON_UTF8).content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isBadRequest()).andReturn();

        String actualResponse = result.getResponse().getContentAsString();
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    @DisplayName("Product request should pass with API Operation NULL")
    public void productRequestForCreateShouldPassForNullApiOperation() throws Exception{
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addGoodProduct().build();

        ArrayList<Product> products = new ArrayList<>();
        products.add(ProductTestBuilder.builder().goodProduct().build());

        when(productService.create(any(ProductRequest.class))).thenReturn(products);

        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType
                        .APPLICATION_JSON_UTF8).content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isAccepted()).andReturn();

        String responseStr = result.getResponse().getContentAsString();
        ProductResponse response = objectMapper.readValue(responseStr,
                ProductResponse.class);

        assertEquals(1, response.getProduct().size());
        assertNotNull(response.getProduct().get(0).getId());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    @DisplayName("Product request should pass with API Operation CREATE")
    public void productRequestForCreateShouldPassForCreateApiOperation() throws Exception{
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addGoodProduct().withApiOperationCreate().build();

        ArrayList<Product> products = new ArrayList<>();
        products.add(ProductTestBuilder.builder().goodProduct().build());

        when(productService.create(any(ProductRequest.class))).thenReturn(products);

        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType
                        .APPLICATION_JSON_UTF8).content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isAccepted()).andReturn();

        String responseStr = result.getResponse().getContentAsString();
        ProductResponse response = objectMapper.readValue(responseStr,
                ProductResponse.class);

        assertEquals(1, response.getProduct().size());
        assertNotNull(response.getProduct().get(0).getId());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    @DisplayName("Product request should fail if products are invalid")
    public void productRequestForCreateShouldFailForBadProducts() throws Exception{
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addBadProduct().withApiOperationCreate().build();
        String expectedResponse = "{\"ResponseInfo\":null,\"Errors\":[{\"code\":\"NotNull.productRequest.product[0].tenantId\",\"message\":\"must not be null\",\"description\":null,\"params\":null},{\"code\":\"NotNull.productRequest.product[0].type\",\"message\":\"must not be null\",\"description\":null,\"params\":null}]}";
        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType
                        .APPLICATION_JSON_UTF8).content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isBadRequest()).andReturn();

        String actualResponse = result.getResponse().getContentAsString();
        assertEquals(expectedResponse, actualResponse);
    }
}