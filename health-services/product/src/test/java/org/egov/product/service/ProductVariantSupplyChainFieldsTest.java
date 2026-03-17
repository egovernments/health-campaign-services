package org.egov.product.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.product.ProductVariantRequest;
import org.egov.common.service.IdGenService;
import org.egov.product.config.ProductConfiguration;
import org.egov.product.helper.ProductVariantTestBuilder;
import org.egov.product.repository.ProductVariantRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantSupplyChainFieldsTest {

    @InjectMocks
    private ProductVariantService productVariantService;

    @Mock
    private IdGenService idGenService;

    @Mock
    private ProductService productService;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductConfiguration productConfiguration;

    @BeforeEach
    void setUp() throws Exception {
        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                any(String.class),
                eq("product.variant.id"), eq(""), anyInt()))
                .thenReturn(idList);
        lenient().when(productConfiguration.getCreateProductVariantTopic()).thenReturn("create-topic");
        lenient().when(productService.validateProductId(anyString(), any(List.class)))
                .thenReturn(Collections.singletonList("some-product-id"));
    }

    @Test
    @DisplayName("should create product variant with supply chain fields")
    void shouldCreateProductVariantWithSupplyChainFields() throws Exception {
        ProductVariant productVariant = ProductVariantTestBuilder.builder()
                .withId().withVariation().withSupplyChainFields().build();
        List<ProductVariant> productVariants = new ArrayList<>();
        productVariants.add(productVariant);

        ProductVariantRequest request = ProductVariantRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .productVariant(productVariants)
                .build();

        List<ProductVariant> result = productVariantService.create(request);

        assertEquals("12345678901234", result.get(0).getGtin());
        assertEquals("BATCH-001", result.get(0).getBatchNumber());
        assertEquals("SERIAL-001", result.get(0).getSerialNumber());
        assertEquals(1735689600000L, result.get(0).getExpiryDate());
        assertEquals("TAB", result.get(0).getBaseUnit());
        assertEquals(100L, result.get(0).getNetContent());
    }

    @Test
    @DisplayName("should create product variant with null supply chain fields")
    void shouldCreateProductVariantWithNullSupplyChainFields() throws Exception {
        ProductVariant productVariant = ProductVariantTestBuilder.builder()
                .withId().withVariation().build();
        List<ProductVariant> productVariants = new ArrayList<>();
        productVariants.add(productVariant);

        ProductVariantRequest request = ProductVariantRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .productVariant(productVariants)
                .build();

        List<ProductVariant> result = productVariantService.create(request);

        assertNull(result.get(0).getGtin());
        assertNull(result.get(0).getBatchNumber());
        assertNull(result.get(0).getSerialNumber());
        assertNull(result.get(0).getExpiryDate());
        assertNull(result.get(0).getBaseUnit());
        assertNull(result.get(0).getNetContent());
    }

    @Test
    @DisplayName("should create product variant with only baseUnit and netContent")
    void shouldCreateProductVariantWithOnlyBaseUnitAndNetContent() throws Exception {
        ProductVariant productVariant = ProductVariantTestBuilder.builder()
                .withId().withVariation()
                .withBaseUnit("mL")
                .withNetContent(500L)
                .build();
        List<ProductVariant> productVariants = new ArrayList<>();
        productVariants.add(productVariant);

        ProductVariantRequest request = ProductVariantRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .productVariant(productVariants)
                .build();

        List<ProductVariant> result = productVariantService.create(request);

        assertEquals("mL", result.get(0).getBaseUnit());
        assertEquals(500L, result.get(0).getNetContent());
        assertNull(result.get(0).getGtin());
    }

    @Test
    @DisplayName("should preserve supply chain fields on update")
    void shouldPreserveSupplyChainFieldsOnUpdate() {
        ProductVariant productVariant = ProductVariantTestBuilder.builder()
                .withId().withVariation().withSupplyChainFields().build();

        assertNotNull(productVariant.getGtin());
        assertNotNull(productVariant.getBatchNumber());
        assertNotNull(productVariant.getSerialNumber());
        assertNotNull(productVariant.getExpiryDate());
        assertNotNull(productVariant.getBaseUnit());
        assertNotNull(productVariant.getNetContent());

        assertEquals("12345678901234", productVariant.getGtin());
        assertEquals("BATCH-001", productVariant.getBatchNumber());
        assertEquals("SERIAL-001", productVariant.getSerialNumber());
        assertEquals(1735689600000L, productVariant.getExpiryDate());
        assertEquals("TAB", productVariant.getBaseUnit());
        assertEquals(100L, productVariant.getNetContent());
    }
}
