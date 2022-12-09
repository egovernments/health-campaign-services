package org.egov.product.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.product.TestConfiguration;
import org.egov.product.helper.ProductRequestTestBuilder;
import org.egov.product.helper.ProductTestBuilder;
import org.egov.product.helper.ProductVariantRequestTestBuilder;
import org.egov.product.helper.ProductVariantTestBuilder;
import org.egov.product.service.ProductService;
import org.egov.product.service.ProductVariantService;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;
import org.egov.product.web.models.ProductResponse;
import org.egov.product.web.models.ProductVariant;
import org.egov.product.web.models.ProductVariantRequest;
import org.egov.product.web.models.ProductVariantResponse;
import org.egov.tracer.model.ErrorRes;
import org.junit.Ignore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
public class ProductApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductVariantService productVariantService;

    @MockBean
    private ProductService productService;

    @org.junit.jupiter.api.Test
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

    @org.junit.jupiter.api.Test
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

    @org.junit.jupiter.api.Test
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
        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType
                        .APPLICATION_JSON_UTF8).content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isBadRequest()).andReturn();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("should create product variant and return with 202 accepted")
    void shouldCreateProductVariantAndReturnWith202Accepted() throws Exception {
        ProductVariantRequest request = ProductVariantRequestTestBuilder.builder()
                .withOneProductVariant()
                .build();
        ProductVariant productVariant = ProductVariantTestBuilder.builder().withId().build();
        List<ProductVariant> productVariants = new ArrayList<>();
        productVariants.add(productVariant);
        when(productVariantService.create(any(ProductVariantRequest.class))).thenReturn(productVariants);

        final MvcResult result = mockMvc.perform(post("/variant/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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

    @org.junit.jupiter.api.Test
    @DisplayName("should send error response with error details with 400 bad request for create")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForCreate() throws Exception {
        final MvcResult result = mockMvc.perform(post("/variant/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProductVariantRequestTestBuilder.builder()
                                .withOneProductVariant()
                                .withBadTenantIdInOneProductVariant()
                                .build())))
                .andExpect(status().isBadRequest())
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals(1, response.getErrors().size());
        assertTrue(response.getErrors().get(0).getCode().contains("tenantId"));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("should send 400 bad request in case of incorrect api operation for create")
    void shouldSend400BadRequestInCaseOfIncorrectApiOperationForCreate() throws Exception {
        final MvcResult result = mockMvc.perform(post("/variant/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProductVariantRequestTestBuilder.builder()
                                .withOneProductVariant()
                                .withApiOperationNotNullAndNotCreate()
                                .build())))
                .andExpect(status().isBadRequest())
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals(1, response.getErrors().size());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("should update product variant and return with 202 accepted")
    void shouldUpdateProductVariantAndReturnWith202Accepted() throws Exception {
        ProductVariantRequest request = ProductVariantRequestTestBuilder.builder()
                .withOneProductVariantHavingId()
                .withApiOperationNotNullAndNotCreate()
                .build();
        ProductVariant productVariant = ProductVariantTestBuilder.builder().withId().build();
        List<ProductVariant> productVariants = new ArrayList<>();
        productVariants.add(productVariant);
        when(productVariantService.update(any(ProductVariantRequest.class))).thenReturn(productVariants);

        final MvcResult result = mockMvc.perform(post("/variant/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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

    @org.junit.jupiter.api.Test
    @DisplayName("should send error response with error details with 400 bad request for update")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForUpdate() throws Exception {
        final MvcResult result = mockMvc.perform(post("/variant/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProductVariantRequestTestBuilder.builder()
                                .withOneProductVariantHavingId()
                                .withBadTenantIdInOneProductVariant()
                                .build())))
                .andExpect(status().isBadRequest())
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals(1, response.getErrors().size());
        assertTrue(response.getErrors().get(0).getCode().contains("tenantId"));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("should send 400 bad request in case of incorrect api operation for update")
    void shouldSend400BadRequestInCaseOfIncorrectApiOperationForUpdate() throws Exception {
        final MvcResult result = mockMvc.perform(post("/variant/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProductVariantRequestTestBuilder.builder()
                                .withOneProductVariantHavingId()
                                .withApiOperationNotUpdate()
                                .build())))
                .andExpect(status().isBadRequest())
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals(1, response.getErrors().size());
    }

}
