package org.egov.product.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.producer.Producer;
import org.egov.product.helper.ProductRequestTestBuilder;
import org.egov.product.helper.ProductTestBuilder;
import org.egov.product.repository.rowmapper.ProductRowMapper;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;
import org.egov.product.web.models.ProductSearch;
import org.egov.product.web.models.ProductSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRepositoryTest {

    @InjectMocks
    private ProductRepository productRepository;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private SelectQueryBuilder selectQueryBuilder;

    @Mock
    Producer producer;

    @Mock
    RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations hashOperations;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
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
    @DisplayName("should validate by id and return valid product")
    void shouldValidateByIdAndReturnValidProduct() {
        List<Product> products = new ArrayList<>();
        products.add(ProductTestBuilder.builder().goodProduct().withId("123").build());
        when(namedParameterJdbcTemplate.query(any(String.class), any(Map.class), any(RowMapper.class)))
                .thenReturn(products);

        List<Product> result = productRepository.findById(Arrays.asList("123"));

        assertEquals(1, result.size());
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

    @Test
    @DisplayName("should check if product is sent to kafka topic or not")
    void shouldSendProductToKafkaTopic() throws Exception{
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addGoodProduct()
                .withApiOperationCreate().build();

        productRepository.save(productRequest.getProduct(), "save-product-topic");

        verify(producer, times(1)).push(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("data should be cached after sending data to kafka topic")
    void shouldCacheDataAfterSendingToKafkaTopic() throws Exception{
        ProductRequest productRequest = ProductRequestTestBuilder.builder().withRequestInfo().addGoodProduct()
                .withApiOperationCreate().build();
        productRepository.save(productRequest.getProduct(), "save-product-topic");

        InOrder inOrder = inOrder(producer, hashOperations);

        inOrder.verify(producer, times(1)).push(any(String.class), any(Object.class));
        inOrder.verify(hashOperations, times(1))
                .putAll(any(String.class), any(Map.class));
    }

    @Test
    @DisplayName("data validate all product Ids from DB or cache")
    void shouldValidateAllProductIdsFromDbOrCache() throws Exception{
        HashMap<String, Product> productMap = new HashMap<>();
        productMap.put("ID101", ProductTestBuilder.builder().goodProduct().withId("ID101").build());
        productMap.put("ID102", ProductTestBuilder.builder().goodProduct().withId("ID102").build());
        when(namedParameterJdbcTemplate.query(any(String.class), any(Map.class), any(RowMapper.class))).thenReturn(Arrays.asList(
                ProductTestBuilder.builder().goodProduct().withId("ID103"),
                ProductTestBuilder.builder().goodProduct().withId("ID104")));
        when(hashOperations.entries(any(Object.class))).thenReturn(productMap);

        List<Product> validProductIds = productRepository.findById(new ArrayList(Arrays.asList("ID101", "ID102", "ID103", "ID104")));

        assertEquals(4, validProductIds.size());
    }

    @Test
    @DisplayName("get products from db for the search request")
    void shouldReturnProductsFromDBForSearchRequest() throws QueryBuilderException {
        List<Product> products = new ArrayList<>();
        products.add(ProductTestBuilder.builder().goodProduct().withId("ID101").build());
        products.add(ProductTestBuilder.builder().goodProduct().withId("ID101").build());
        ProductSearch productSearch = ProductSearch.builder().id("ID101").name("Product").build();
        ProductSearchRequest productSearchRequest = ProductSearchRequest.builder().product(productSearch)
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();
        when(selectQueryBuilder.build(any(Object.class))).thenReturn("Select * from product where id='ID101' and name=`product' and isdeleted=false");
        when(namedParameterJdbcTemplate.query(any(String.class), any(Map.class), any(ProductRowMapper.class)))
                .thenReturn(products);

        List<Product> productsResponse = productRepository.find(productSearchRequest.getProduct(), 2,
                0, "default", null, false);

        assertEquals(2, productsResponse.size());
    }

    @Test
    @DisplayName("get products from db which are deleted")
    void shouldReturnProductsFromDBForSearchRequestWithDeletedIncluded() throws QueryBuilderException {
        List<Product> products = new ArrayList<>();
        products.add(ProductTestBuilder.builder().goodProduct().withId("ID101").build());
        products.add(ProductTestBuilder.builder().goodProduct().withId("ID101").withIsDeleted().build());
        ProductSearch productSearch = ProductSearch.builder().id("ID101").name("Product").build();
        ProductSearchRequest productSearchRequest = ProductSearchRequest.builder().product(productSearch)
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();
        when(selectQueryBuilder.build(any(Object.class))).thenReturn("Select * from product where id='ID101' and name=`product'");
        when(namedParameterJdbcTemplate.query(any(String.class), any(Map.class), any(ProductRowMapper.class)))
                .thenReturn(products);

        List<Product> productsResponse = productRepository.find(productSearchRequest.getProduct(), 2,
                0, "default", null, true);

        assertEquals(2, productsResponse.size());
    }
}