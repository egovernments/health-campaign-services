package org.egov.stock.validator;


import org.egov.common.models.Error;
import org.egov.stock.helper.StockBulkRequestTestBuilder;
import org.egov.stock.helper.StockReconciliationBulkRequestTestBuilder;
import org.egov.stock.validator.stock.SIsDeletedValidator;
import org.egov.stock.validator.stockreconciliation.SrIsDeletedValidator;
import org.egov.stock.web.models.Stock;
import org.egov.stock.web.models.StockBulkRequest;
import org.egov.stock.web.models.StockReconciliation;
import org.egov.stock.web.models.StockReconciliationBulkRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class isDeletedValidatorTest {

    @InjectMocks
    private SIsDeletedValidator stockIsDeletedValidator;

    @InjectMocks
    private SrIsDeletedValidator stockReconciliationIsDeletedValidator;

    @Test
    @DisplayName("should add stock to error details if is Deleted is true")
    void shouldAddStockToErrorDetailsIfIsDeletedIsTrue() {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();
        request.getStock().get(0).setIsDeleted(true);

        Map<Stock, List<Error>> errorDetailsMap = stockIsDeletedValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add stock to error details if is Deleted is false")
    void shouldAddStockToErrorDetailsIfIsDeletedIsFalse() {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        Map<Stock, List<Error>> errorDetailsMap = stockIsDeletedValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 0);
    }

    @Test
    @DisplayName("should add stock reconciliation to error details if is Deleted is true")
    void shouldAddStockReconciliationToErrorDetailsIfIsDeletedIsTrue() {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStock().withRequestInfo().build();
        request.getStockReconciliation().get(0).setIsDeleted(true);

        Map<StockReconciliation, List<Error>> errorDetailsMap = stockReconciliationIsDeletedValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add stock reconciliation to error details if is Deleted is false")
    void shouldAddStockReconciliationToErrorDetailsIfIsDeletedIsFalse() {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStock().withRequestInfo().build();


        Map<StockReconciliation, List<Error>> errorDetailsMap = stockReconciliationIsDeletedValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 0);
    }
}
