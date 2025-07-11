package org.egov.product.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.product.Product;
import org.egov.common.models.product.ProductRequest;
import org.egov.common.models.product.ProductResponse;
import org.egov.product.TestConfiguration;
import org.egov.product.helper.ProductRequestTestBuilder;
import org.egov.product.helper.ProductTestBuilder;
import org.egov.product.service.ProductService;
import org.egov.product.service.ProductVariantService;
import org.egov.product.web.models.ProductSearch;
import org.egov.product.web.models.ProductSearchRequest;
import org.egov.tracer.model.CustomException;
import org.egov.tracer.model.ErrorRes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API tests for ProductApiController
 */
@WebMvcTest(ProductApiController.class)
@Import({TestConfiguration.class})
class ProductApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductVariantService productVariantService;

    @MockBean
    private ProductService productService;

    @Test
    @DisplayName("product Request should fail for incorrect API operation")
    void productRequestForCreateShouldFailForIncorrectApiOperation() throws Exception {
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addGoodProduct().withApiOperationDelete().build();
        String expectedResponse = "{\"ResponseInfo\":null,\"Errors\":[{\"code\":\"INVALID_API_OPERATION\",\"message\":\"API Operation DELETE not valid for create request\",\"description\":null,\"params\":null}]}";
        ErrorRes expectErrorResponse = objectMapper.readValue(expectedResponse, ErrorRes.class);
        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isBadRequest()).andReturn();
        ErrorRes actualErrorRes = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorRes.class);

        assertEquals(expectErrorResponse.getErrors().get(0).getCode(), actualErrorRes.getErrors().get(0).getCode());
    }

    @Test
    @DisplayName("product request should not pass with API Operation NULL")
    void productRequestForCreateShouldNotPassForNullApiOperation() throws Exception{
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addGoodProduct().build();
        ArrayList<Product> products = new ArrayList<>();
        products.add(ProductTestBuilder.builder().goodProduct().withId("ID-101").build());
        when(productService.create(any(ProductRequest.class))).thenReturn(products);

        mockMvc.perform(post("/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("product request should pass with API Operation CREATE")
    void productRequestForCreateShouldPassForCreateApiOperation() throws Exception{
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addGoodProduct().withApiOperationCreate().build();
        ArrayList<Product> products = new ArrayList<>();
        products.add(ProductTestBuilder.builder().goodProduct().withId("ID-101").build());
        when(productService.create(any(ProductRequest.class))).thenReturn(products);

        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isAccepted()).andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ProductResponse response = objectMapper.readValue(responseStr,
                ProductResponse.class);

        assertEquals(1, response.getProduct().size());
        assertNotNull(response.getProduct().get(0).getId());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    @DisplayName("product request should fail if products are invalid")
    void productRequestForCreateShouldFailForBadProducts() throws Exception {
        ProductRequest productRequest = ProductRequestTestBuilder.builder()
                .withRequestInfo()
                .addBadProduct()
                .withApiOperationCreate()
                .build();

        // Mock the service to throw CustomException on create
        when(productService.create(any(ProductRequest.class)))
                .thenThrow(new CustomException("INVALID_PRODUCT", "Product is invalid"));

        MvcResult result = mockMvc.perform(post("/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isBadRequest())
                .andReturn();
    }

    @Test
    @DisplayName("should send 400 bad request in case of incorrect api operation for update")
    void shouldSend400BadRequestInCaseOfIncorrectApiOperationForUpdateProduct() throws Exception{
        final MvcResult result = mockMvc.perform(post("/v1/_update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ProductRequestTestBuilder.builder().addGoodProduct().build())))
                .andExpect(status().isBadRequest())
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr, ErrorRes.class);

        assertEquals(1, response.getErrors().size());
    }

    @Test
    @DisplayName("should send error response with error details with 400 bad request for product update")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForUpdateProduct() throws Exception {
        final MvcResult result = mockMvc.perform(post("/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProductRequestTestBuilder.builder()
                                .withRequestInfo()
                                .addGoodProductWithNullTenant().build())))
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr, ErrorRes.class);

        assertEquals(1, response.getErrors().size());
        assertEquals("NotNull.productRequest.product[0].tenantId", response.getErrors().get(0).getCode());
    }



    @Test
    @DisplayName("should update product and return with 202 accepted")
    void shouldUpdateProductAndReturnWith202Accepted() throws Exception {
        ProductRequest request = ProductRequestTestBuilder.builder()
                .withRequestInfo()
                .addGoodProductWithId("ID101")
                .withApiOperationUpdate()
                .build();

        List<Product> products = new ArrayList<>();
        products.addAll(request.getProduct());
        when(productService.update(any(ProductRequest.class))).thenReturn(products);

        final MvcResult result = mockMvc.perform(post("/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ProductResponse response = objectMapper.readValue(responseStr,
                ProductResponse.class);

        assertEquals(1, response.getProduct().size());
        assertNotNull(response.getProduct().get(0).getId());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    @DisplayName("should throw exception if product ids are null or empty")
    void shouldThrowExceptionIfProductIdsNullOrEmpty() throws Exception {
        ProductRequest request = ProductRequestTestBuilder.builder()
                .withRequestInfo()
                .addGoodProduct()
                .withApiOperationUpdate()
                .build();

        List<Product> products = new ArrayList<>();
        products.addAll(request.getProduct());
        when(productService.update(any(ProductRequest.class))).thenThrow(new CustomException("PRODUCT_EMPTY", "Product IDs can be null or empty"));

        final MvcResult result = mockMvc.perform(post("/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals(response.getErrors().size(), 1);
        assertEquals(response.getErrors().get(0).getCode(), "PRODUCT_EMPTY");
    }

    @Test
    @DisplayName("should accept search request and return response as accepted")
    void shouldAcceptSearchRequestAndReturnProducts() throws Exception {

        ProductSearchRequest productSearchRequest = ProductSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .product(ProductSearch.builder().id(Collections.singletonList("ID101")).type("DRUG").build())
                .build();
        when(productService.search(any(ProductSearchRequest.class),
                any(Integer.class),
                any(Integer.class),
                any(String.class),
                any(Long.class),
                any(Boolean.class))).thenReturn(Arrays.asList(ProductTestBuilder.builder().goodProduct().withId("ID101").build()));

        final MvcResult result = mockMvc.perform(post("/v1/_search?limit=10&offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productSearchRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ProductResponse response = objectMapper.readValue(responseStr,
                ProductResponse.class);

        assertEquals(response.getProduct().size(), 1);
    }

    @Test
    @DisplayName("should accept search request and return response as accepted")
    void shouldThrowExceptionIfNoResultFound() throws Exception {

        ProductSearchRequest productSearchRequest = ProductSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .product(ProductSearch.builder().id(Collections.singletonList("ID101")).type("DRUG").build())
                .build();
        when(productService.search(any(ProductSearchRequest.class),
                any(Integer.class),
                any(Integer.class),
                any(String.class),
                any(Long.class),
                any(Boolean.class))).thenThrow(new CustomException("NO_RESULT_FOUND", "No products found."));

        final MvcResult result = mockMvc.perform(post("/v1/_search?limit=10&offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productSearchRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals(response.getErrors().size(), 1);
    }

}
