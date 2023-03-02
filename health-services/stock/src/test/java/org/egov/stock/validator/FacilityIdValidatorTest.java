package org.egov.stock.validator;


import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.stock.helper.StockBulkRequestTestBuilder;
import org.egov.stock.helper.StockReconciliationBulkRequestTestBuilder;
import org.egov.stock.service.FacilityService;
import org.egov.stock.validator.stock.SFacilityIdValidator;
import org.egov.stock.validator.stockreconciliation.SrFacilityIdValidator;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityIdValidatorTest {

    @InjectMocks
    private SFacilityIdValidator sFacilityIdValidator;

    @InjectMocks
    private SrFacilityIdValidator srFacilityIdValidator;

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
                any(RequestInfo.class))).thenReturn(Collections.singletonList("facility-id"));
    }

    @Test
    @DisplayName("should add stock to error details if facility id not found")
    void shouldAddStockToErrorDetailsIfFacilityIdNotFound() {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        mockEmptyResponse();

        Map<Stock, List<Error>> errorDetailsMap = sFacilityIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add stock to error details if facility id found")
    void shouldAddStockToErrorDetailsIfFacilityIdFound() {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        mockSomeResponse();

        Map<Stock, List<Error>> errorDetailsMap = sFacilityIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 0);
    }

    @Test
    @DisplayName("should add stock reconciliation to error details if facility id not found")
    void shouldAddStockReconciliationToErrorDetailsFacilityIdNotFound() {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStock().withRequestInfo().build();

        mockEmptyResponse();

        Map<StockReconciliation, List<Error>> errorDetailsMap = srFacilityIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add stock reconciliation to error details if facility id found")
    void shouldAddStockReconciliationToErrorDetailsIfFacilityIdFound() {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStock().withRequestInfo().build();

        mockSomeResponse();

        Map<StockReconciliation, List<Error>> errorDetailsMap = srFacilityIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 0);
    }
}
