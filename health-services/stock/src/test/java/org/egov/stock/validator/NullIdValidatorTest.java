package org.egov.stock.validator;

import org.egov.common.models.Error;
import org.egov.stock.helper.StockBulkRequestTestBuilder;
import org.egov.stock.validator.stock.SNullIdValidator;
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
class NullIdValidatorTest {

    @InjectMocks
    private SNullIdValidator stockNullIdValidator;

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
}
