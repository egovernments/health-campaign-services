package org.egov.product.repository;

import org.egov.common.producer.Producer;
import org.egov.product.helper.ProductVariantTestBuilder;
import org.egov.product.web.models.ProductVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductVariantRepositoryTest {

    @InjectMocks
    private ProductVariantRepository productVariantRepository;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private Producer producer;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("should save and return saved objects back")
    void shouldSaveAndReturnSavedObjectsBack() {
        List<ProductVariant> productVariants = Collections.singletonList(ProductVariantTestBuilder
                .builder().withId().build());

        List<ProductVariant> result = productVariantRepository.save(productVariants);

        assertEquals(result, productVariants);
        verify(producer, times(1)).push(any(String.class), any(Object.class));
    }
}