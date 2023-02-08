package org.egov.stock.validator;


import org.egov.common.models.Error;
import org.egov.stock.helper.StockBulkRequestTestBuilder;
import org.egov.stock.validator.stock.SisDeletedValidator;
import org.egov.stock.web.models.Stock;
import org.egov.stock.web.models.StockBulkRequest;
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
    private SisDeletedValidator stockIsDeletedValidator;

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
}
