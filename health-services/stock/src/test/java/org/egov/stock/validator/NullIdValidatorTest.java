package org.egov.stock.validator;

import org.egov.common.models.Error;
import org.egov.stock.helper.StockBulkRequestTestBuilder;
import org.egov.stock.helper.StockReconciliationBulkRequestTestBuilder;
import org.egov.stock.validator.stock.SNullIdValidator;
import org.egov.stock.validator.stockreconciliation.SrNullIdValidator;
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
class NullIdValidatorTest {

    @InjectMocks
    private SNullIdValidator stockNullIdValidator;

    @InjectMocks
    private SrNullIdValidator stockReconciliationNullIdValidator;

    @Test
    @DisplayName("should add to error details if id is null")
    void shouldAddErrorDetailsIfIdNull() {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        Map<Stock, List<Error>> errorDetailsMap = stockNullIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add to error details if id is not  null")
    void shouldNotAddErrorDetailsIfIdNotNull() {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();
        request.getStock().get(0).setId("some-id");

        Map<Stock, List<Error>> errorDetailsMap = stockNullIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 0);
    }

    @Test
    @DisplayName("should add to error details if reconciliation id is null")
    void shouldAddErrorDetailsIfReconciliationIdNull() {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStock().withRequestInfo().build();

        Map<StockReconciliation, List<Error>> errorDetailsMap = stockReconciliationNullIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add to error details if reconciliation id is not  null")
    void shouldNotAddErrorDetailsIfReconciliationIdNotNull() {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStockId("some-id").withRequestInfo().build();

        Map<StockReconciliation, List<Error>> errorDetailsMap = stockReconciliationNullIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 0);
    }
}
