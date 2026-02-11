package org.egov.stock.validator;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.stock.helper.StockBulkRequestTestBuilder;
import org.egov.stock.helper.StockReconciliationBulkRequestTestBuilder;
import org.egov.stock.helper.StockReconciliationTestBuilder;
import org.egov.stock.helper.StockTestBuilder;
import org.egov.stock.repository.StockReconciliationRepository;
import org.egov.stock.repository.StockRepository;
import org.egov.stock.validator.stock.SRowVersionValidator;
import org.egov.stock.validator.stockreconciliation.SrRowVersionValidator;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RowVersionValidatorTest {

    @InjectMocks
    private SRowVersionValidator stockRowVersionValidator;

    @InjectMocks
    private SrRowVersionValidator stockReconciliationRowVersionValidator;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private StockReconciliationRepository stockReconciliationRepository;

    @Test
    @DisplayName("should add to error if row version mismatch found")
    void shouldAddToErrorDetailsIfRowVersionMismatchFound() throws InvalidTenantIdException {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStockId("some-id").withRequestInfo().build();
        request.getStock().get(0).setRowVersion(2);
        when(stockRepository.findById(anyString(), anyList(), anyBoolean(), anyString()))
                .thenReturn(Collections.singletonList(StockTestBuilder.builder().withStock().withId("some-id").build()));

        Map<Stock, List<Error>> errorDetailsMap = stockRowVersionValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error if row version is similar")
    void shouldNotAddToErrorDetailsIfRowVersionSimilar() throws InvalidTenantIdException {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStockId("some-id").withRequestInfo().build();
        when(stockRepository.findById(anyString(), anyList(), anyBoolean(), anyString()))
                .thenReturn(Collections.singletonList(StockTestBuilder.builder().withStock().withId("some-id").build()));

        Map<Stock, List<Error>> errorDetailsMap = stockRowVersionValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }


    @Test
    @DisplayName("should add to error if row version mismatch found reconciliation")
    void shouldAddToErrorDetailsIfRowVersionMismatchFoundReconciliation() throws InvalidTenantIdException {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStockId("some-id").withRequestInfo().build();
        request.getStockReconciliation().get(0).setRowVersion(2);
        when(stockReconciliationRepository.findById(anyString(), anyList(), anyBoolean(), anyString()))
                .thenReturn(Collections.singletonList(StockReconciliationTestBuilder.builder()
                        .withStock().withId("some-id").build()));

        Map<StockReconciliation, List<Error>> errorDetailsMap = stockReconciliationRowVersionValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error if row version is similar reconciliation")
    void shouldNotAddToErrorDetailsIfRowVersionSimilarReconciliation() throws InvalidTenantIdException {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStockId("some-id").withRequestInfo().build();
        when(stockReconciliationRepository.findById( anyString(), anyList(), anyBoolean(), anyString()))
                .thenReturn(Collections.singletonList(StockReconciliationTestBuilder.builder()
                        .withStock().withId("some-id").build()));

        Map<StockReconciliation, List<Error>> errorDetailsMap = stockReconciliationRowVersionValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }
}
