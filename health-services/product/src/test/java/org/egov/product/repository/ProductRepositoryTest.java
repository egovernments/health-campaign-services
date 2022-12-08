package org.egov.product.repository;

import org.egov.common.producer.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRepositoryTest {

    @InjectMocks
    private ProductRepository productRepository;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private Producer producer;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("should validate and return valid productIds")
    void shouldValidateAndReturnValidProductIds() {
        List<String> productIds = new ArrayList<>();
        productIds.add("some-id");
        productIds.add("some-other-id");
        List<String> validProductIds = new ArrayList<>(productIds);
        when(namedParameterJdbcTemplate.queryForObject(any(String.class), any(Map.class), any(RowMapper.class)))
                .thenReturn(validProductIds);

        List<String> result = productRepository.validateProductId(productIds);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("should validate and return empty list if no ids are valid")
    void shouldValidateAndReturnEmptyListIfNoIdsAreValid() {
        List<String> productIds = new ArrayList<>();
        productIds.add("some-id");
        productIds.add("some-other-id");
        when(namedParameterJdbcTemplate.queryForObject(any(String.class), any(Map.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        List<String> result = productRepository.validateProductId(productIds);

        assertEquals(0, result.size());
    }
}