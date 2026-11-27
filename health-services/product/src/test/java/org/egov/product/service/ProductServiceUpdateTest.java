package org.egov.product.service;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.product.Product;
import org.egov.common.models.product.ProductRequest;
import org.egov.common.producer.Producer;
import org.egov.product.config.ProductConfiguration;
import org.egov.product.helper.ProductRequestTestBuilder;
import org.egov.product.helper.ProductTestBuilder;
import org.egov.product.repository.ProductRepository;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceUpdateTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private Producer producer;

    @Mock
    private ProductConfiguration productConfiguration;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(productConfiguration.getUpdateProductTopic()).thenReturn("update-topic");
    }

    @Test
    @DisplayName("should throw exception if product ids are null or empty")
    void shouldThrowExceptionIfProductIdNotFound() throws InvalidTenantIdException {
        Product product = ProductTestBuilder.builder().goodProduct().withId("123").build();
        product.setRowVersion(123);
        ProductRequest productRequest = ProductRequestTestBuilder.builder().add(product).withRequestInfo().build();
        when(productRepository.findById(anyString(), any(List.class))).thenReturn(Arrays.asList());

        assertThrows(Exception.class, () -> productService.update(productRequest));
    }

    @Test
    @DisplayName("should throw exception for row versions mismatch")
    void shouldThrowExceptionIfRowVersionIsNotSimilar() throws InvalidTenantIdException {
        Product product = ProductTestBuilder.builder().goodProduct().withId("123").build();
        product.setRowVersion(123);
        ProductRequest productRequest = ProductRequestTestBuilder.builder().add(product).withRequestInfo().build();
        when(productRepository.findById(anyString(), any(List.class))).thenReturn(Arrays.asList(ProductTestBuilder.builder().goodProduct().withId("213").build()));

        assertThrows(Exception.class, () -> productService.update(productRequest));
    }

    @Test
    @DisplayName("should throw exception if Ids are null")
    void shouldThrowExceptionIfIdsAreNull() {
        Product product = ProductTestBuilder.builder().goodProduct().withId(null).build();
        ProductRequest productRequest = ProductRequestTestBuilder.builder().add(product).withRequestInfo().build();

        assertThrows(CustomException.class, () -> productService.update(productRequest));
    }
}
