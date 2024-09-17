package org.egov.stock.validator;

import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.product.ProductVariant;
import org.egov.common.models.product.ProductVariantResponse;
import org.egov.common.models.product.ProductVariantSearchRequest;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.stock.config.StockConfiguration;
import org.egov.stock.config.StockReconciliationConfiguration;
import org.egov.stock.helper.StockBulkRequestTestBuilder;
import org.egov.stock.helper.StockReconciliationBulkRequestTestBuilder;
import org.egov.stock.validator.stock.SProductVariantIdValidator;
import org.egov.stock.validator.stockreconciliation.SrProductVariantIdValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantIdValidatorTest {

    @InjectMocks
    private SProductVariantIdValidator stockProductIdValidator;

    @InjectMocks
    private SrProductVariantIdValidator stockReconciliationProductIdValidator;

    @Mock
    private ServiceRequestClient client;

    @Mock
    private StockConfiguration stockConfiguration;

    @Mock
    private StockReconciliationConfiguration stockReconciliationConfiguration;

    @BeforeEach
    void setUp() {
        lenient().when(stockConfiguration.getMdmsHost()).thenReturn("http://localhost:8080/");
        lenient().when(stockConfiguration.getMdmsSearchEndpoint()).thenReturn("/some-url");

        lenient().when(stockReconciliationConfiguration.getProductHost()).thenReturn("http://localhost:8080/");
        lenient().when(stockReconciliationConfiguration.getProductVariantSearchUrl()).thenReturn("/some-url");
    }

    @Test
    @DisplayName("should add to error details if product variant id not found")
    void shouldAddToErrorDetailsIfProductVariantNotFound() throws Exception {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();
        when(client.fetchResult(any(StringBuilder.class),
                any(ProductVariantSearchRequest.class),
                eq(ProductVariantResponse.class))).thenReturn(emptyResponse());

        Map<Stock, List<Error>> errorDetailsMap = stockProductIdValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error details if product variant id found")
    void shouldNotAddToErrorDetailsIfProductVariantFound() throws Exception {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();
        request.getStock().get(0).setProductVariantId("some-id");

        when(client.fetchResult(any(StringBuilder.class),
                any(ProductVariantSearchRequest.class),
                eq(ProductVariantResponse.class))).thenReturn(someResponse());

        Map<Stock, List<Error>> errorDetailsMap = stockProductIdValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }


    @Test
    @DisplayName("should add to error details if product variant id not found reconciliation")
    void shouldAddToErrorDetailsIfProductVariantNotFoundReconciliation() throws Exception {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStock().withRequestInfo().build();
        when(client.fetchResult(any(StringBuilder.class),
                any(ProductVariantSearchRequest.class),
                eq(ProductVariantResponse.class))).thenReturn(emptyResponse());

        Map<StockReconciliation, List<Error>> errorDetailsMap = stockReconciliationProductIdValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error details if product variant id found reconciliation")
    void shouldNotAddToErrorDetailsIfProductVariantFoundReconciliation() throws Exception {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStock().withRequestInfo().build();
        request.getStockReconciliation().get(0).setProductVariantId("some-id");

        when(client.fetchResult(any(StringBuilder.class),
                any(ProductVariantSearchRequest.class),
                eq(ProductVariantResponse.class))).thenReturn(someResponse());

        Map<StockReconciliation, List<Error>> errorDetailsMap = stockReconciliationProductIdValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }

    private ProductVariantResponse someResponse() {
        return ProductVariantResponse.builder().productVariant(Collections
                        .singletonList(ProductVariant.builder().id("some-id").build())).build();
    }

    private ProductVariantResponse emptyResponse() {
        return ProductVariantResponse.builder().productVariant(Collections.emptyList()).build();
    }

}
