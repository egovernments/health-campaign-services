package org.egov.product.service;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.product.ApiOperation;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.product.ProductVariantRequest;
import org.egov.common.service.IdGenService;
import org.egov.product.config.ProductConfiguration;
import org.egov.product.helper.ProductVariantRequestTestBuilder;
import org.egov.product.helper.ProductVariantTestBuilder;
import org.egov.product.repository.ProductVariantRepository;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceUpdateTest {

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

    private ProductVariantRequest request;

    private List<String> productVariantIds;

    @BeforeEach
    void setUp() throws Exception {
        request = ProductVariantRequestTestBuilder.builder()
                .withOneProductVariantHavingIdAndRowVersion()
                .build();
        request.setApiOperation(ApiOperation.UPDATE);
        productVariantIds = request.getProductVariant().stream().map(ProductVariant::getId)
                .collect(Collectors.toList());
        lenient().when(productConfiguration.getUpdateProductVariantTopic()).thenReturn("update-topic");
    }

    private void mockFindById() throws InvalidTenantIdException {
        when(productVariantRepository.findById(anyString(), eq(productVariantIds))).thenReturn(request.getProductVariant());
    }

    private void mockValidateProuctId() throws InvalidTenantIdException {
        when(productService.validateProductId(anyString(), any(List.class)))
                .thenReturn(Collections.singletonList("some-product-id"));
    }

    @Test
    @DisplayName("should update the lastModifiedTime in the result")
    void shouldUpdateTheLastModifiedTimeInTheResult() throws InvalidTenantIdException {
        Long time = request.getProductVariant().get(0).getAuditDetails().getLastModifiedTime();
        mockValidateProuctId();
        mockFindById();

        List<ProductVariant> result = productVariantService.update(request);

        assertNotEquals(time,
                result.get(0).getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should update the row version in the result")
    void shouldUpdateTheRowVersionInTheResult() throws InvalidTenantIdException {
        Integer rowVersion = request.getProductVariant().get(0).getRowVersion();
        mockValidateProuctId();
        mockFindById();

        List<ProductVariant> result = productVariantService.update(request);

        assertEquals(rowVersion,
                result.get(0).getRowVersion() - 1);
    }

    @Test
    @DisplayName("should check if the request has valid product ids")
    void shouldCheckIfTheRequestHasValidProductIds() throws InvalidTenantIdException {
        when(productService.validateProductId(anyString(), any(List.class))).thenReturn(Collections
                .singletonList(request.getProductVariant().get(0).getProductId()));
        mockFindById();

        productVariantService.update(request);

        verify(productService, times(1)).validateProductId(anyString(), any(List.class));
    }

    @Test
    @DisplayName("should throw exception for any invalid product id")
    void shouldThrowExceptionForAnyInvalidProductId() throws Exception {
        when(productService.validateProductId(anyString(), any(List.class))).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> productVariantService.update(request));
    }

    @Test
    @DisplayName("should fetch existing records using id")
    void shouldFetchExistingRecordsUsingId() throws InvalidTenantIdException {
        mockValidateProuctId();
        mockFindById();

        productVariantService.update(request);

        verify(productVariantRepository, times(1)).findById(anyString(), anyList());
    }

    @Test
    @DisplayName("should throw exception if fetched records count doesn't match the count in request")
    void shouldThrowExceptionIfFetchedRecordsCountDoesntMatchTheCountInRequest() throws InvalidTenantIdException {
        mockValidateProuctId();
        when(productVariantRepository.findById(anyString(), anyList())).thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> productVariantService.update(request));
    }

    @Test
    @DisplayName("should send the updates to kafka")
    void shouldSendTheUpdatesToKafka() throws InvalidTenantIdException {
        mockValidateProuctId();
        mockFindById();
        when(productVariantRepository.save(anyList(), anyString())).thenReturn(request.getProductVariant());

        List<ProductVariant> productVariants = productVariantService.update(request);

        assertEquals(request.getProductVariant(), productVariants);
    }

    @Test
    @DisplayName("Should throw exception for row versions mismatch")
    void shouldThrowExceptionIfRowVersionIsNotSimilar() throws InvalidTenantIdException {
        ProductVariant productVariant = ProductVariantTestBuilder.builder().withId().build();
        productVariant.setRowVersion(123);
        ProductVariantRequest productVariantRequest = ProductVariantRequestTestBuilder.builder()
                .withOneProductVariantHavingId().build();
        mockValidateProuctId();
        when(productVariantRepository.findById(anyString(), eq(productVariantIds)))
                .thenReturn(Collections.singletonList(productVariant));

        assertThrows(Exception.class, () -> productVariantService.update(productVariantRequest));
    }

    @Test
    @DisplayName("Should throw exception If Ids are null")
    void shouldThrowExceptionIfIdsAreNull() {
        ProductVariantRequest productVariantRequest = ProductVariantRequestTestBuilder.builder()
                .withOneProductVariantHavingId().build();

        assertThrows(Exception.class, () -> productVariantService.update(productVariantRequest));
    }

}
