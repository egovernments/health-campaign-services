package org.egov.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.product.Product;
import org.egov.product.helper.ProductTestBuilder;
import org.egov.product.web.models.Mdms;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceSearchTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private MdmsV2Service mdmsV2Service;

    private List<Product> products;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        products = new ArrayList<>();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("should not raise exception if no search results are found")
    void shouldNotRaiseExceptionIfNoProductsFound() throws Exception {
        ProductSearch productSearch = ProductSearch.builder()
                .id(Collections.singletonList("ID101")).name("Product").build();
        ProductSearchRequest productSearchRequest = ProductSearchRequest.builder()
                .product(productSearch)
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        // Mocking mdmsV2Service to return an empty list
        when(mdmsV2Service.fetchMdmsData(any(), anyString(), anyBoolean(), anyList(), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>());

        assertDoesNotThrow(() -> productService.search(productSearchRequest, 10, 0, "default", null, false));
    }

    @Test
    @DisplayName("should return products if search criteria is matched")
    void shouldReturnProductsIfSearchCriteriaIsMatched() throws Exception {
        // Step 1: Create a Product object
        Product product = ProductTestBuilder.builder().goodProduct().withId("ID101").build();
        JsonNode jsonNode = objectMapper.valueToTree(product); // Convert Product to JsonNode

        // Step 2: Create an Mdms object with the JsonNode
        Mdms mdms = Mdms.builder()
                .id("ID101")
                .tenantId("tenantId")
                .schemaCode("productSchema")
                .data(jsonNode)  // Set the JsonNode into the Mdms object
                .isActive(true)
                .auditDetails(new AuditDetails())
                .build();

        // Step 3: Mock the mdmsV2Service to return list with the Mdms object
        when(mdmsV2Service.fetchMdmsData(any(), anyString(), anyBoolean(), anyList(), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(mdms));

        // Step 4: Define search criteria and the request
        ProductSearch productSearch = ProductSearch.builder()
                .id(Collections.singletonList("ID101")).name("Product").build();
        ProductSearchRequest productSearchRequest = ProductSearchRequest.builder()
                .product(productSearch)
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        // Step 5: Call the search method
        List<Product> products = productService.search(productSearchRequest, 10, 0, "default", null, false);

        // Step 6: Validate the result
        assertEquals(1, products.size());  // Ensure that 1 product is returned
        assertEquals("ID101", products.get(0).getId());  // Ensure that the ID matches
    }

}
