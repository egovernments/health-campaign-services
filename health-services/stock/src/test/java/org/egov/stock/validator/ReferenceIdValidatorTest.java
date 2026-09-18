package org.egov.stock.validator;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.assertj.core.util.Arrays;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.stock.helper.StockBulkRequestTestBuilder;
import org.egov.stock.helper.StockReconciliationBulkRequestTestBuilder;
import org.egov.stock.service.FacilityService;
import org.egov.stock.validator.stock.SReferenceIdValidator;
import org.egov.stock.validator.stockreconciliation.SrReferenceIdValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceIdValidatorTest {

    @InjectMocks
    private SReferenceIdValidator sReferenceIdValidator;

    @InjectMocks
    private SrReferenceIdValidator srReferenceIdValidator;

    @Mock
    private FacilityService facilityService;

    private void mockEmptyResponse() {
        when(facilityService.validateProjectFacilityMappings(any(List.class),
                any(String.class),
                any(Map.class),
                any(RequestInfo.class))).thenReturn(Collections.emptyMap());
    }

    private void mockSomeResponse() {

    	List<String> facilityIds = new ArrayList<>();
    	facilityIds.add("sender-id");
    	
        when(facilityService.validateProjectFacilityMappings(any(List.class),
                any(String.class),
                any(Map.class),
                any(RequestInfo.class))).thenReturn(Collections.singletonMap("reference-id", facilityIds));
    }

    @Test
    @DisplayName("should add stock to error details if reference id not found")
    void shouldAddStockToErrorDetailsIfReferenceIdNotFound() {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        mockEmptyResponse();

        Map<Stock, List<Error>> errorDetailsMap = sReferenceIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add stock to error details if reference id found")
    void shouldAddStockToErrorDetailsIfReferenceIdFound() {
        StockBulkRequest request = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        mockSomeResponse();

        Map<Stock, List<Error>> errorDetailsMap = sReferenceIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 0);
    }

    @Test
    @DisplayName("should add stock reconciliation to error details if reference id not found")
    void shouldAddStockReconciliationToErrorDetailsReferenceIdNotFound() {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStock().withRequestInfo().build();

        mockEmptyResponse();

        Map<StockReconciliation, List<Error>> errorDetailsMap = srReferenceIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add stock reconciliation to error details if reference id found")
    void shouldAddStockReconciliationToErrorDetailsIfReferenceIdFound() {
        StockReconciliationBulkRequest request = StockReconciliationBulkRequestTestBuilder.builder()
                .withStock().withRequestInfo().build();

        mockSomeResponse();

        Map<StockReconciliation, List<Error>> errorDetailsMap = srReferenceIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 0);
    }
}
