//package org.egov.product.service;
//
//import org.egov.common.data.query.exception.QueryBuilderException;
//import org.egov.common.helper.RequestInfoTestBuilder;
//import org.egov.common.models.product.ProductVariant;
//import org.egov.product.helper.ProductVariantTestBuilder;
//import org.egov.product.repository.ProductVariantRepository;
//import org.egov.product.web.models.ProductVariantSearch;
//import org.egov.product.web.models.ProductVariantSearchRequest;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyBoolean;
//import static org.mockito.ArgumentMatchers.anyList;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//class ProductVariantServiceSearchTest {
//
//    @InjectMocks
//    private ProductVariantService productVariantService;
//
//    @Mock
//    private ProductVariantRepository productVariantRepository;
//
//    private ArrayList<ProductVariant> productVariants;
//
//    @BeforeEach
//    void setUp() throws QueryBuilderException {
//        productVariants = new ArrayList<>();
//    }
//
//    @Test
//    @DisplayName("should not raise exception if no search results are found")
//    void shouldNotRaiseExceptionIfNoProductsFound() throws Exception {
//        when(productVariantRepository.find(any(ProductVariantSearch.class), any(Integer.class),
//                any(Integer.class), any(String.class), eq(null), any(Boolean.class))).thenReturn(Collections.emptyList());
//        ProductVariantSearch productVariantSearch = ProductVariantSearch.builder()
//                .id(Collections.singletonList("ID101")).variation("some-variation").build();
//        ProductVariantSearchRequest productVariantSearchRequest = ProductVariantSearchRequest.builder()
//                .productVariant(productVariantSearch).requestInfo(RequestInfoTestBuilder.builder()
//                        .withCompleteRequestInfo().build()).build();
//
//        assertDoesNotThrow(() -> productVariantService.search(productVariantSearchRequest, 10, 0, "default", null, false));
//    }
//
//    @Test
//    @DisplayName("should return products if search criteria is matched")
//    void shouldReturnProductsIfSearchCriteriaIsMatched() throws Exception {
//        when(productVariantRepository.find(any(ProductVariantSearch.class), any(Integer.class),
//                any(Integer.class), any(String.class), eq(null), any(Boolean.class))).thenReturn(productVariants);
//        productVariants.add(ProductVariantTestBuilder.builder().withId().withVariation().withAuditDetails().build());
//        ProductVariantSearch productVariantSearch = ProductVariantSearch.builder().id(Collections.singletonList("ID101"))
//                .variation("some-variation").build();
//        ProductVariantSearchRequest productVariantSearchRequest = ProductVariantSearchRequest.builder()
//                .productVariant(productVariantSearch).requestInfo(RequestInfoTestBuilder.builder()
//                        .withCompleteRequestInfo().build()).build();
//
//        List<ProductVariant> products = productVariantService.search(productVariantSearchRequest, 10, 0, "default", null, false);
//
//        assertEquals(1, products.size());
//    }
//
//    @Test
//    @DisplayName("should return from cache if search criteria has id only")
//    void shouldReturnFromCacheIfSearchCriteriaHasIdOnly() throws Exception {
//        ProductVariant productVariant = ProductVariantTestBuilder.builder().withId()
//                .withAuditDetails().build();
//        productVariant.setIsDeleted(false);
//        productVariants.add(productVariant);
//        ProductVariantSearch productVariantSearch = ProductVariantSearch.builder()
//                .id(Collections.singletonList("ID101")).build();
//        ProductVariantSearchRequest productVariantSearchRequest = ProductVariantSearchRequest.builder()
//                .productVariant(productVariantSearch).requestInfo(RequestInfoTestBuilder.builder()
//                        .withCompleteRequestInfo().build()).build();
//        when(productVariantRepository.findById(anyList(), anyBoolean())).thenReturn(productVariants);
//
//        List<ProductVariant> productVariants = productVariantService.search(productVariantSearchRequest,
//                10, 0, null, null, true);
//
//        assertEquals(1, productVariants.size());
//    }
//}
