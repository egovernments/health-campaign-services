package org.egov.product.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.product.ApiOperation;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.product.ProductVariantRequest;
import org.egov.common.models.product.ProductVariantResponse;
import org.egov.product.TestConfiguration;
import org.egov.product.helper.ProductVariantRequestTestBuilder;
import org.egov.product.helper.ProductVariantTestBuilder;
import org.egov.product.service.ProductService;
import org.egov.product.service.ProductVariantService;
import org.egov.product.web.models.ProductVariantSearch;
import org.egov.product.web.models.ProductVariantSearchRequest;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductApiController.class)
@Import({TestConfiguration.class})
public class ProductVariantApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductVariantService productVariantService;

    @MockBean
    private ProductService productService;

    @Test
    @DisplayName("should create product variant and return with 202 accepted")
    void shouldCreateProductVariantAndReturnWith202Accepted() throws Exception {
        ProductVariantRequest request = ProductVariantRequestTestBuilder.builder()
                .withOneProductVariant()
                .withApiOperationNotUpdate()
                .build();
        ProductVariant productVariant = ProductVariantTestBuilder.builder()
                .withId().withVariation().build();
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

    @Test
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

    @Test
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

    @Test
    @DisplayName("should update product variant and return with 202 accepted")
    void shouldUpdateProductVariantAndReturnWith202Accepted() throws Exception {
        ProductVariantRequest request = ProductVariantRequestTestBuilder.builder()
                .withOneProductVariantHavingId()
                .withApiOperationNotNullAndNotCreate()
                .build();
        ProductVariant productVariant = ProductVariantTestBuilder.builder()
                .withId().withVariation().build();
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

    @Test
    @DisplayName("should send error response with error details with 400 bad request for update")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForUpdate() throws Exception {
        ProductVariantRequest request = ProductVariantRequestTestBuilder.builder()
                .withOneProductVariantHavingId()
                .withBadTenantIdInOneProductVariant()
                .build();
        request.setApiOperation(ApiOperation.UPDATE);

        // Mock the service to simulate validation failure
        when(productVariantService.update(any(ProductVariantRequest.class)))
                .thenThrow(new CustomException("tenantId", "tenantId is invalid"));

        final MvcResult result = mockMvc.perform(post("/variant/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())  // expect 400
                .andReturn();

        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr, ErrorRes.class);

        assertEquals(1, response.getErrors().size());
        assertTrue(response.getErrors().get(0).getCode().contains("tenantId"));
    }

    @Test
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

    @Test
    @DisplayName("should accept search request and return response as accepted")
    void shouldAcceptSearchRequestAndReturnProductsVariants() throws Exception {

        ProductVariantSearchRequest productVariantSearchRequest = ProductVariantSearchRequest.builder().productVariant(
                ProductVariantSearch.builder().productId("101").build()
        ).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();
        when(productVariantService.search(any(ProductVariantSearchRequest.class),
                any(Integer.class),
                any(Integer.class),
                any(String.class),
                any(Long.class),
                any(Boolean.class))).thenReturn(Arrays.asList(ProductVariantTestBuilder.builder().withId().withVariation().withAuditDetails().build()));

        final MvcResult result = mockMvc.perform(post("/variant/v1/_search?limit=10&offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productVariantSearchRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ProductVariantResponse response = objectMapper.readValue(responseStr,
                ProductVariantResponse.class);

        assertEquals(response.getProductVariant().size(), 1);
    }

    @Test
    @DisplayName("should accept search request and return response as accepted")
    void shouldThrowExceptionIfNoResultFound() throws Exception {

        ProductVariantSearchRequest productVariantSearchRequest = ProductVariantSearchRequest.builder().productVariant(
                ProductVariantSearch.builder().productId("101").build()
        ).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();
        when(productVariantService.search(any(ProductVariantSearchRequest.class),
                any(Integer.class),
                any(Integer.class),
                any(String.class),
                any(Long.class),
                any(Boolean.class))).thenThrow(new CustomException("NO_RESULT_FOUND", "No products found."));


        final MvcResult result = mockMvc.perform(post("/variant/v1/_search?limit=10&offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productVariantSearchRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals(response.getErrors().size(), 1);
    }
}
