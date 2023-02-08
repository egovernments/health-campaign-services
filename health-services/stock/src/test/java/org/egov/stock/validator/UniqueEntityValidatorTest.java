package org.egov.stock.validator;

import org.egov.common.models.Error;
import org.egov.stock.helper.StockBulkRequestTestBuilder;
import org.egov.stock.validator.stock.SuniqueEntityValidator;
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
class UniqueEntityValidatorTest {

    @InjectMocks
    private SuniqueEntityValidator stockUniqueEntityValidator;

    @Test
    @DisplayName("should add to error if duplicate entity is found")
    void shouldAddErrorDetailsIfDuplicateFound() {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withStock().withRequestInfo().build();
        request.getStock().get(0).setId("some-id");
        request.getStock().get(1).setId("some-id");

        Map<Stock, List<Error>> errorDetailsMap = stockUniqueEntityValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error if duplicate entity is not found")
    void shouldNotAddErrorDetailsIfDuplicateNotFound() {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withStock().withRequestInfo().build();
        request.getStock().get(0).setId("some-id");
        request.getStock().get(1).setId("some-other-id");

        Map<Stock, List<Error>> errorDetailsMap = stockUniqueEntityValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }

}
