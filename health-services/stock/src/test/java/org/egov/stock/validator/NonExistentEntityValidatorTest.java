package org.egov.stock.validator;

import org.egov.common.models.Error;
import org.egov.stock.helper.StockBulkRequestTestBuilder;
import org.egov.stock.helper.StockReconciliationBulkRequestTestBuilder;
import org.egov.stock.helper.StockReconciliationTestBuilder;
import org.egov.stock.helper.StockTestBuilder;
import org.egov.stock.repository.StockReconciliationRepository;
import org.egov.stock.repository.StockRepository;
import org.egov.stock.validator.stock.SNonExistentValidator;
import org.egov.stock.validator.stockreconciliation.SrNonExistentValidator;
import org.egov.stock.web.models.Stock;
import org.egov.stock.web.models.StockBulkRequest;
import org.egov.stock.web.models.StockReconciliation;
import org.egov.stock.web.models.StockReconciliationBulkRequest;
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
class NonExistentEntityValidatorTest {

    @InjectMocks
    private SNonExistentValidator stockNonExistentValidator;

    @InjectMocks
    private SrNonExistentValidator stockReconciliationNonExistentValidator;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private StockReconciliationRepository stockReconciliationRepository;

    @Test
    @DisplayName("should add to error details map if entity not found")
    void shouldAddToErrorDetailsMapIfEntityNotFound() {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStockId("some-id").withRequestInfo().build();
        when(stockRepository.findById(anyList(), anyBoolean(), anyString()))
                .thenReturn(Collections.emptyList());

        Map<Stock, List<Error>> errorDetailsMap = stockNonExistentValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error details map if entity found")
    void shouldNotAddToErrorDetailsMapIfEntityFound() {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStockId("some-id").withRequestInfo().build();
        when(stockRepository.findById(anyList(), anyBoolean(), anyString()))
                .thenReturn(Collections.singletonList(StockTestBuilder.builder().withStock().withId("some-id").build()));

        Map<Stock, List<Error>> errorDetailsMap = stockNonExistentValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }


    @Test
    @DisplayName("should add to error details map if reconciliation entity not found")
    void shouldAddToErrorDetailsMapIfReconciliationEntityNotFound() {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStockId("some-id").withRequestInfo().build();
        when(stockReconciliationRepository.findById(anyList(), anyBoolean(), anyString()))
                .thenReturn(Collections.emptyList());

        Map<StockReconciliation, List<Error>> errorDetailsMap = stockReconciliationNonExistentValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error details map if reconciliation entity found")
    void shouldNotAddToErrorDetailsMapIfReconciliationEntityFound() {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStockId("some-id").withRequestInfo().build();
        when(stockReconciliationRepository.findById(anyList(), anyBoolean(), anyString()))
                .thenReturn(Collections.singletonList(StockReconciliationTestBuilder.builder().withStock()
                        .withId("some-id").build()));

        Map<StockReconciliation, List<Error>> errorDetailsMap = stockReconciliationNonExistentValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }
}
