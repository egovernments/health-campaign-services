package org.egov.product.repository;

import org.egov.product.helper.ProductVariantTestBuilder;
import org.egov.product.web.models.ProductVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantRepositoryFindTest {
    @InjectMocks
    private ProductVariantRepository productVariantRepository;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

//    @Mock
//    private RedisTemplate<String, Object> redisTemplate;
//
//    @Mock
//    private HashOperations hashOperations;

    private List<String> productVariantIds;

    private List<ProductVariant> productVariants;

    @BeforeEach
    void setUp() {
        productVariants = Collections.singletonList(ProductVariantTestBuilder
                .builder().withId().build());
        productVariantIds = productVariants.stream().map(ProductVariant::getId)
                .collect(Collectors.toList());
        //when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    @DisplayName("should find product variants by ids and return the results")
    void shouldFindProductVariantsByIdsAndReturnTheResults() {
        //when(hashOperations.multiGet(anyString(), anyList())).thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.queryForObject(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(productVariants);

        List<ProductVariant> result = productVariantRepository.findById(productVariantIds);

        assertEquals(productVariantIds.size(), result.size());
    }

    @Test
    @DisplayName("should find product variants by ids in cache first and return the results")
    @Disabled
    void shouldFindProductVariantsByIdsInCacheFirstAndReturnTheResults() {
//        when(hashOperations.multiGet(anyString(), anyList())).thenReturn(productVariants);
//
//        List<ProductVariant> result = productVariantRepository.findById(productVariantIds);
//
//        assertEquals(productVariantIds.size(), result.size());
//        verify(namedParameterJdbcTemplate, times(0))
//                .queryForObject(anyString(), anyMap(), any(RowMapper.class));
    }
}
