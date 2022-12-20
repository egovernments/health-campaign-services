package org.egov.product.service;

import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.product.helper.ProductTestBuilder;
import org.egov.product.repository.ProductRepository;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductSearch;
import org.egov.product.web.models.ProductSearchRequest;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceSearchTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    private ArrayList<Product> products;

    @BeforeEach
    void setUp() throws QueryBuilderException {
        products = new ArrayList<>();
        lenient().when(productRepository.find(any(ProductSearch.class), any(Integer.class),
               any(Integer.class), any(String.class), eq(null), any(Boolean.class)))
               .thenReturn(products);
    }

    @Test
    @DisplayName("Should raise exception if no search results are found")
    void shouldRaiseExceptionIfNoProductsFound() throws Exception {
        ProductSearch productSearch = ProductSearch.builder().id("ID101").name("Product").build();
        ProductSearchRequest productSearchRequest = ProductSearchRequest.builder().product(productSearch)
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        assertThrows(Exception.class, () -> productService.search(productSearchRequest, 10, 0, "default", null, false));
    }

    @Test
    @DisplayName("Should return products if search criteria is matched")
    void shouldReturnProductsIfSearchCriteriaIsMatched() throws Exception {
        products.add(ProductTestBuilder.builder().goodProduct().withId("ID101").build());
        ProductSearch productSearch = ProductSearch.builder().id("ID101").name("Product").build();
        ProductSearchRequest productSearchRequest = ProductSearchRequest.builder().product(productSearch)
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        List<Product> products = productService.search(productSearchRequest, 10, 0, "default", null, false);

        assertEquals(1, products.size());
    }

    @Test
    @DisplayName("Should return product from cache if search criteria has id only")
    void shouldReturnProductFromCacheIfSearchCriteriaHasIdOnly() throws Exception {
        products.add(ProductTestBuilder.builder().goodProduct().withId("ID101").build());
        ProductSearch productSearch = ProductSearch.builder().id("ID101").build();
        ProductSearchRequest productSearchRequest = ProductSearchRequest.builder().product(productSearch)
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();
        when(productRepository.findById(anyList())).thenReturn(products);

        List<Product> products = productService.search(productSearchRequest, 10, 0, "default", null, false);

        assertEquals(1, products.size());
    }
}
