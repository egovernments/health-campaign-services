package org.egov.product.service;

import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.product.helper.ProductVariantTestBuilder;
import org.egov.product.repository.ProductVariantRepository;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
public class ProductVariantServiceSearchTest {

    @InjectMocks
    private ProductVariantService productVariantService;

    @Mock
    private ProductVariantRepository productVariantRepository;

    ArrayList<ProductVariant> productVariants = new ArrayList<>();

    @BeforeEach
    void setUp() throws QueryBuilderException {
        lenient().when(productVariantRepository.find(any(ProductVariantSearch.class), any(Integer.class), any(Integer.class), any(String.class), eq(null), any(Boolean.class))).thenReturn(productVariants);
    }

    @Test
    @DisplayName("Should raise exception if no search results are found")
    void shouldRaiseExceptionIfNoProductsFound() throws Exception {
        productVariants.clear();
        ProductVariantSearch productVariantSearch = ProductVariantSearch.builder().id("ID101").build();
        ProductVariantSearchRequest productVariantSearchRequest = ProductVariantSearchRequest.builder().productVariant(productVariantSearch).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        assertThrows(Exception.class, () -> productVariantService.search(productVariantSearchRequest, 10, 0, "default", null, false));
    }

    @Test
    @DisplayName("Should return products if search criteria is matched")
    void shouldReturnProductsIfSearchCriteriaIsMatched() throws Exception {
        productVariants.add(ProductVariantTestBuilder.builder().withId().withAuditDetails().build());
        productVariants.add(ProductVariantTestBuilder.builder().withId().withAuditDetails().build());
        ProductVariantSearch productVariantSearch = ProductVariantSearch.builder().id("ID101").build();
        ProductVariantSearchRequest productVariantSearchRequest = ProductVariantSearchRequest.builder().productVariant(productVariantSearch).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        List<ProductVariant> products = productVariantService.search(productVariantSearchRequest, 10, 0, "default", null, false);

        assertEquals(2, products.size());
    }
}
