package org.egov.stock.validator;


import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.stock.helper.StockBulkRequestTestBuilder;
import org.egov.stock.service.FacilityService;
import org.egov.stock.validator.stock.STransactingPartyIdValidator;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactingPartyIdValidatorTest {

    @InjectMocks
    private STransactingPartyIdValidator sTransactingPartyIdValidator;

    @Mock
    private FacilityService facilityService;

    private void mockEmptyResponse() {
        when(facilityService.validateFacilityIds(any(List.class),
                any(List.class),
                any(String.class),
                any(Map.class),
                any(RequestInfo.class))).thenReturn(Collections.emptyList());
    }

    private void mockSomeResponse() {
        when(facilityService.validateFacilityIds(any(List.class),
                any(List.class),
                any(String.class),
                any(Map.class),
                any(RequestInfo.class))).thenReturn(Collections.singletonList("transaction-party-id"));
    }

    @Test
    @DisplayName("should add stock to error details if transacting party id not found")
    void shouldAddStockToErrorDetailsIfTransactingPartyIdNotFound() {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        mockEmptyResponse();

        Map<Stock, List<Error>> errorDetailsMap = sTransactingPartyIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add stock to error details if transacting party id found")
    void shouldAddStockToErrorDetailsIfTransactingPartyIdFound() {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        mockSomeResponse();

        Map<Stock, List<Error>> errorDetailsMap = sTransactingPartyIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 0);
    }
}
