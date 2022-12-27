package org.egov.product.repository;

import org.egov.product.helper.ProductTestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRepositoryTest {

    @InjectMocks
    private ProductRepository productRepository;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations hashOperations;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        ReflectionTestUtils.setField(productRepository, "timeToLive", "60");
    }

    @Test
    @DisplayName("should validate and return valid productIds")
    void shouldValidateAndReturnValidProductIds() {
        List<String> productIds = new ArrayList<>();
        productIds.add("some-id");
        productIds.add("some-other-id");
        List<String> validProductIds = new ArrayList<>(productIds);
        when(namedParameterJdbcTemplate.queryForList(any(String.class), any(Map.class), eq(String.class)))
                .thenReturn(validProductIds);

        List<String> result = productRepository.validateProductId(productIds);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("should return ids from cache for productIds")
    void shouldValidateAndReturnValidProductIdsFromCache() {
        List<String> productIds = new ArrayList<>();
        productIds.add("some-id");
        productIds.add("some-other-id");
        HashMap<Object, Object> hashMap = new HashMap<>();
        hashMap.put("some-id", ProductTestBuilder.builder().goodProduct().build());
        when(hashOperations.entries(any(Object.class))).thenReturn(hashMap);

        List<String> result = productRepository.validateProductId(productIds);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("should validate and return empty list if no ids are valid")
    void shouldValidateAndReturnEmptyListIfNoIdsAreValid() {
        List<String> productIds = new ArrayList<>();
        productIds.add("some-id");
        productIds.add("some-other-id");
        when(namedParameterJdbcTemplate.queryForList(any(String.class), any(Map.class), eq(String.class)))
                .thenReturn(Collections.emptyList());

        List<String> result = productRepository.validateProductId(productIds);

        assertEquals(0, result.size());
    }
}