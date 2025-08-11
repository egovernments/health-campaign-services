package org.egov.stock.validator;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
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
import org.egov.stock.validator.stock.SNonExistentValidator;
import org.egov.stock.validator.stockreconciliation.SrNonExistentValidator;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Slf4j
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
        try {
            when(stockRepository.find(any(), any(), any(), any(), any(), any(Boolean.class)))
                    .thenReturn(Collections.emptyList());
        } catch (QueryBuilderException | InvalidTenantIdException e) {
            log.error("Search failed for Stock with error: {}", e.getMessage(), e);
            throw new CustomException("STOCK_SEARCH_FAILED", "Search Failed for Stock, " + e.getMessage());
        }

        Map<Stock, List<Error>> errorDetailsMap = stockNonExistentValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error details map if entity found")
    void shouldNotAddToErrorDetailsMapIfEntityFound() {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStockId("some-id").withRequestInfo().build();
        try {
            when(stockRepository.find(any(), any(), any(), any(), any(), any(Boolean.class)))
                    .thenReturn(Collections.singletonList(StockTestBuilder.builder().withStock().withId("some-id").build()));
        } catch (QueryBuilderException | InvalidTenantIdException e) {
            log.error("Search failed for Stock with error: {}", e.getMessage(), e);
            throw new CustomException("STOCK_SEARCH_FAILED", "Search Failed for Stock, " + e.getMessage()); 
        }

        Map<Stock, List<Error>> errorDetailsMap = stockNonExistentValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }


    @Test
    @DisplayName("should add to error details map if reconciliation entity not found")
    void shouldAddToErrorDetailsMapIfReconciliationEntityNotFound() {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStockId("some-id").withRequestInfo().build();
        try {
            when(stockReconciliationRepository.find(any(), any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(Collections.emptyList());
        } catch (QueryBuilderException | InvalidTenantIdException e) {
            log.error("Search failed for StockReconciliation with error: {}", e.getMessage(), e);
            throw new CustomException("STOCK_RECONCILIANTION_SEARCH_FAILED", "Search Failed for StockReconciliation, " + e.getMessage()); 
        }

        Map<StockReconciliation, List<Error>> errorDetailsMap = stockReconciliationNonExistentValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error details map if reconciliation entity found")
    void shouldNotAddToErrorDetailsMapIfReconciliationEntityFound() {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStockId("some-id").withRequestInfo().build();
        try {
            when(stockReconciliationRepository.find(any(), any(), any(), any(), any(), any(Boolean.class)))
                    .thenReturn(Collections.singletonList(StockReconciliationTestBuilder.builder().withStock()
                            .withId("some-id").build()));
        } catch (QueryBuilderException | InvalidTenantIdException e) {
            log.error("Search failed for StockReconciliation with error: {}", e.getMessage(), e);
            throw new CustomException("STOCK_RECONCILIANTION_SEARCH_FAILED", "Search Failed for StockReconciliation, " + e.getMessage()); 
        }

        Map<StockReconciliation, List<Error>> errorDetailsMap = stockReconciliationNonExistentValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }
}
