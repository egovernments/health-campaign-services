package org.egov.product.service;

import org.egov.common.producer.Producer;
import org.egov.product.helper.ProductRequestTestBuilder;
import org.egov.product.helper.ProductTestBuilder;
import org.egov.product.repository.ProductRepository;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceUpdateTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private Producer producer;

    @Test
    @DisplayName("Should throw exception if product ids are null or empty")
    void shouldThrowExceptionIfProductIdNullOrEmpty() throws Exception {
        ProductRequest productRequest = ProductRequestTestBuilder.builder().addGoodProduct().withRequestInfo().build();
        assertThrows(Exception.class, () -> productService.update(productRequest));
    }

    @Test
    @DisplayName("Should throw exception if product ids not found in cache or DB")
    void shouldThrowExceptionIfProductIdNotFound() throws Exception {
        Product product1 = ProductTestBuilder.builder().goodProduct().withId("ID101").build();
        ProductRequest productRequest = ProductRequestTestBuilder.builder().add(product1).withRequestInfo().build();
        when(productRepository.validateAllProductId(any(List.class))).thenReturn(new ArrayList<String>());
        assertThrows(Exception.class, () -> productService.update(productRequest));
    }
}
