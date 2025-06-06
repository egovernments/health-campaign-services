package org.egov.product.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.product.ProductVariantRequest;
import org.egov.common.service.IdGenService;
import org.egov.product.config.ProductConfiguration;
import org.egov.product.helper.ProductVariantRequestTestBuilder;
import org.egov.product.repository.ProductVariantRepository;
import org.egov.tracer.model.CustomException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceCreateTest {

    @InjectMocks
    private ProductVariantService productVariantService;

    @Mock
    private IdGenService idGenService;

    @Mock
    private ProductService productService;

    @Mock
    private ProductVariantRepository productVariantRepository;

    private ProductVariantRequest request;

    @Mock
    private ProductConfiguration productConfiguration;

    @BeforeEach
    void setUp() throws Exception {
        request = ProductVariantRequestTestBuilder.builder()
                .withOneProductVariant()
                .build();
        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                any(String.class),
                eq("product.variant.id"), eq(""), anyInt()))
                .thenReturn(idList);
        lenient().when(productConfiguration.getCreateProductVariantTopic()).thenReturn("create-topic");
    }

    private void mockValidateProductId() throws InvalidTenantIdException {
        lenient().when(productService.validateProductId(anyString(), any(List.class)))
                .thenReturn(Collections.singletonList("some-product-id"));
    }

    @Test
    @DisplayName("should enrich the formatted id in product variants")
    void shouldEnrichTheFormattedIdInProductVariants() throws Exception {
        mockValidateProductId();

        List<ProductVariant> productVariants = productVariantService.create(request);

        assertEquals("some-id", productVariants.get(0).getId());
    }

    @Test
    @DisplayName("should send the enriched product variants to the kafka topic")
    void shouldSendTheEnrichedProductVariantsToTheKafkaTopic() throws Exception {
        mockValidateProductId();

        productVariantService.create(request);

        verify(idGenService, times(1)).getIdList(any(RequestInfo.class),
                any(String.class),
                eq("product.variant.id"), eq(""), anyInt());
        verify(productVariantRepository, times(1)).save(any(List.class), any(String.class));
    }

    @Test
    @DisplayName("should update audit details before pushing the product variants to kafka")
    void shouldUpdateAuditDetailsBeforePushingTheProductVariantsToKafka() throws Exception {
        mockValidateProductId();

        List<ProductVariant> productVariants = productVariantService.create(request);

        assertNotNull(productVariants.stream().findAny().get().getAuditDetails().getCreatedBy());
        assertNotNull(productVariants.stream().findAny().get().getAuditDetails().getCreatedTime());
        assertNotNull(productVariants.stream().findAny().get().getAuditDetails().getLastModifiedBy());
        assertNotNull(productVariants.stream().findAny().get().getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should set row version as 1 and deleted as false")
    void shouldSetRowVersionAs1AndDeletedAsFalse() throws Exception {
        mockValidateProductId();

        List<ProductVariant> productVariants = productVariantService.create(request);

        assertEquals(1, productVariants.stream().findAny().get().getRowVersion());
        assertFalse(productVariants.stream().findAny().get().getIsDeleted());
    }

    @Test
    @DisplayName("should validate correct product id")
    void shouldValidateCorrectProductId() throws Exception {
        List<String> validProductIds = new ArrayList<>(Collections.singleton("some-product-id"));
        when(productService.validateProductId(anyString(), any(List.class))).thenReturn(validProductIds);

        List<ProductVariant> productVariants = productVariantService.create(request);

        verify(productService, times(1)).validateProductId(anyString(), any(List.class));
    }

    @Test
    @DisplayName("should throw exception for any invalid product id")
    void shouldThrowExceptionForAnyInvalidProductId() throws Exception {
        when(productService.validateProductId(anyString(), any(List.class))).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> productVariantService.create(request));
    }

}