package org.egov.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.product.ProductVariant;
import org.egov.product.helper.ProductVariantTestBuilder;
import org.egov.product.web.models.Mdms;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceSearchTest {

    @InjectMocks
    private ProductVariantService productVariantService;

    @Mock
    private MdmsV2Service mdmsV2Service; // Mock the MDMS service

    private ArrayList<ProductVariant> productVariants;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        productVariants = new ArrayList<>();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("should not raise exception if no search results are found")
    void shouldNotRaiseExceptionIfNoProductsFound() throws Exception {
        // Arrange
        when(mdmsV2Service.fetchMdmsData(any(), anyString(), anyBoolean(), anyList(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        ProductVariantSearch productVariantSearch = ProductVariantSearch.builder()
                .id(Collections.singletonList("ID101"))
                .variation("some-variation")
                .build();

        ProductVariantSearchRequest productVariantSearchRequest = ProductVariantSearchRequest.builder()
                .productVariant(productVariantSearch)
                .requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo()
                        .build())
                .build();

        // Act & Assert
        assertDoesNotThrow(() -> productVariantService.search(productVariantSearchRequest, 10, 0, "default", null, false));
    }

    @Test
    @DisplayName("should return products if search criteria is matched")
    void shouldReturnProductsIfSearchCriteriaIsMatched() throws Exception {
        // Step 1: Create a Product object
        ProductVariant productVariant = ProductVariantTestBuilder.builder().withIdNull().withId("ID101").build();
        JsonNode jsonNode = objectMapper.valueToTree(productVariant); // Convert ProductVarient to JsonNode

        // Step 2: Create an Mdms object with the JsonNode
        Mdms mdms = Mdms.builder()
                .id("ID101")
                .tenantId("some-tenant-id")
                .schemaCode("productSchema")
                .data(jsonNode)  // Set the JsonNode into the Mdms object
                .isActive(true)
                .auditDetails(new AuditDetails())
                .build();


        when(mdmsV2Service.fetchMdmsData(any(), anyString(), anyBoolean(), anyList(), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(mdms));

        ProductVariantSearch productVariantSearch = ProductVariantSearch.builder()
                .id(Collections.singletonList("ID101"))
                .build();

        ProductVariantSearchRequest productVariantSearchRequest = ProductVariantSearchRequest.builder()
                .productVariant(productVariantSearch)
                .requestInfo(RequestInfoTestBuilder.builder()
                        .withCompleteRequestInfo()
                        .build())
                .build();

        // Act
        List<ProductVariant> products = productVariantService.search(productVariantSearchRequest, 10, 0, "default", null, false);

        // Assert
        assertEquals(1, products.size());
        assertEquals("ID101", products.get(0).getId());
    }
}
