package org.egov.product.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.product.helper.ProductVariantTestBuilder;
import org.egov.product.repository.rowmapper.ProductVariantRowMapper;
import org.egov.product.web.models.ProductVariant;
import org.egov.product.web.models.ProductVariantSearch;
import org.egov.product.web.models.ProductVariantSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantRepositoryFindTest {
    @InjectMocks
    private ProductVariantRepository productVariantRepository;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private SelectQueryBuilder selectQueryBuilder;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations hashOperations;

    private List<String> productVariantIds;

    private List<ProductVariant> productVariants;

    @BeforeEach
    void setUp() {
        productVariants = Collections.singletonList(ProductVariantTestBuilder
                .builder().withId().build());
        productVariantIds = productVariants.stream().map(ProductVariant::getId)
                .collect(Collectors.toList());
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    @DisplayName("should find product variants by ids and return the results")
    void shouldFindProductVariantsByIdsAndReturnTheResults() {
        when(hashOperations.multiGet(anyString(), anyList())).thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(productVariants);

        List<ProductVariant> result = productVariantRepository.findById(productVariantIds);

        assertEquals(productVariantIds.size(), result.size());
    }

    @Test
    @DisplayName("get products from db for the search request")
    void shouldReturnProductsFromDBForSearchRequest() throws QueryBuilderException {
        ProductVariantSearch productVariantSearch = ProductVariantSearch.builder().id("ID101").build();
        ProductVariantSearchRequest productVariantSearchRequest = ProductVariantSearchRequest.builder().productVariant(productVariantSearch).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();
        when(selectQueryBuilder.build(any(Object.class))).thenReturn("Select * from product where id='ID101' and name=`product' and isdeleted=false");
        when(namedParameterJdbcTemplate.query(any(String.class), any(Map.class), any(ProductVariantRowMapper.class)))
                .thenReturn(productVariants);

        List<ProductVariant> productVariantResponse = productVariantRepository.find(productVariantSearchRequest.getProductVariant(), 2, 0, "default", null, false);

        assertEquals(1, productVariantResponse.size());
    }

    @Test
    @DisplayName("get products from db which are deleted")
    void shouldReturnProductsFromDBForSearchRequestWithDeletedIncluded() throws QueryBuilderException {
        productVariants = new ArrayList<>();
        productVariants.add(ProductVariantTestBuilder.builder().withId().withAuditDetails().withDeleted().build());
        productVariants.add(ProductVariantTestBuilder.builder().withId().withAuditDetails().build());
        ProductVariantSearch productVariantSearch = ProductVariantSearch.builder().id("ID101").build();
        ProductVariantSearchRequest productVariantSearchRequest = ProductVariantSearchRequest.builder().productVariant(productVariantSearch).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();
        when(selectQueryBuilder.build(any(Object.class))).thenReturn("Select * from product where id='ID101' and name=`product'");
        when(namedParameterJdbcTemplate.query(any(String.class), any(Map.class), any(ProductVariantRowMapper.class)))
                .thenReturn(productVariants);

        List<ProductVariant> productVariantResponse = productVariantRepository.find(productVariantSearchRequest.getProductVariant(), 2, 0, "default", null, true);

        assertEquals(2, productVariantResponse.size());
    }
}
