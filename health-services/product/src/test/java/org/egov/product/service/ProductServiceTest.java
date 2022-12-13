package org.egov.product.service;

import org.egov.common.producer.Producer;
import org.egov.common.service.IdGenService;
import org.egov.product.enrichment.ProductEnrichment;
import org.egov.product.helper.ProductRequestTestBuilder;
import org.egov.product.repository.ProductRepository;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private IdGenService idGenService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductEnrichment productEnrichment;

    @Mock
    private Producer producer;

    private ProductRequest request;

    @BeforeEach
    void setUp() throws Exception {
        request = ProductRequestTestBuilder.builder()
                .addGoodProduct().withRequestInfo()
                .build();
    }

    @Test
    @DisplayName("should enrich the formatted id in products")
    void shouldEnrichTheFormattedIdInProduct() throws Exception {
        ProductRequest response = ProductRequestTestBuilder.builder().addGoodProduct().build();
        response.getProduct().get(0).setId("some-id");
        when(productEnrichment.enrichProduct(any(ProductRequest.class))).thenReturn(response);

        List<Product> products = productService.create(request);
        assertEquals("some-id", products.get(0).getId());
    }

    @Test
    @DisplayName("should throw error for already existing product")
    void shouldThrowErrorForAlreadyExistingProducts() throws Exception{
        ArrayList<String> ids = new ArrayList<>();
        ids.add("some-id1");

        when(productRepository.validateProductId(any(List.class))).thenReturn(ids);

        assertThrows(CustomException.class, () -> productService.create(request));
    }

    @Test
    @DisplayName("should send the enriched product to the kafka topic")
    void shouldSendTheEnrichedProductToTheKafkaTopic() throws Exception {
        // TODO: Follow AAA
        ProductRequest response = ProductRequestTestBuilder.builder().addGoodProduct().build();
        response.getProduct().get(0).setId("some-id");
        when(productEnrichment.enrichProduct(any(ProductRequest.class))).thenReturn(response);
        productService.create(request);
        verify(productEnrichment, times(1)).enrichProduct(any(ProductRequest.class));
        verify(productRepository, times(1)).save(any(List.class), any(String.class));
    }

    @Test
    @DisplayName("should validate and return valid productIds")
    void shouldValidateAndReturnValidProductIds() {
        List<String> productIds = new ArrayList<>();
        productIds.add("some-id");
        productIds.add("some-other-id");
        List<String> validProductIds = new ArrayList<>(productIds);
        when(productRepository.validateProductId(productIds)).thenReturn(validProductIds);

        List<String> result = productService.validateProductId(productIds);

        assertEquals(2, result.size());
    }
}