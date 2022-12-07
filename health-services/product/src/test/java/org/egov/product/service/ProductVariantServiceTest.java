package org.egov.product.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.producer.Producer;
import org.egov.common.service.IdGenService;
import org.egov.product.helper.ProductVariantRequestTestBuilder;
import org.egov.product.web.models.ProductVariant;
import org.egov.product.web.models.ProductVariantRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceTest {

    @InjectMocks
    private ProductVariantService productVariantService;

    @Mock
    private IdGenService idGenService;

    @Mock
    private Producer producer;

    @Mock
    private ProductService productService;

    private ProductVariantRequest request;

    @BeforeEach
    void setUp() throws Exception {
        request = ProductVariantRequestTestBuilder.builder()
                .withOneProductVariantAndApiOperationNull()
                .build();
        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                any(String.class),
                eq("product.variant.id"), eq(""), anyInt()))
                .thenReturn(idList);
    }

    private void mockValidateProductId() {
        lenient().when(productService.validateProductId(any(List.class)))
                .thenReturn(Collections.singletonList("some-id"));
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
        verify(producer, times(1)).push(any(String.class),
                any(ProductVariantRequest.class));
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
        List<String> validProductIds = new ArrayList<>(Collections.singleton("some-id"));
        when(productService.validateProductId(any(List.class))).thenReturn(validProductIds);

        List<ProductVariant> productVariants = productVariantService.create(request);

        verify(productService, times(1)).validateProductId(any(List.class));
    }

    @Test
    @DisplayName("should throw exception for any invalid product id")
    void shouldThrowExceptionForAnyInvalidProductId() throws Exception {
        when(productService.validateProductId(any(List.class))).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> productVariantService.create(request));
    }

}