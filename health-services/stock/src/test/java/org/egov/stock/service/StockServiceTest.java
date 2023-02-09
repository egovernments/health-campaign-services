package org.egov.stock.service;

import org.egov.common.validator.Validator;
import org.egov.stock.helper.StockRequestTestBuilder;
import org.egov.stock.repository.StockRepository;
import org.egov.stock.web.models.Stock;
import org.egov.stock.web.models.StockBulkRequest;
import org.egov.stock.web.models.StockRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Spy
    @InjectMocks
    private StockService stockService;

    @Mock
    private StockRepository repository;

    @Mock
    List<Validator<StockBulkRequest, Stock>> validators;

    @Test
    @DisplayName("should call create with isBulk false")
    void shouldCallCreateWithIsBulkFalse() {
        StockRequest request = StockRequestTestBuilder.builder().withStock().withRequestInfo().build();
        doReturn(Collections.singletonList(request.getStock()))
                .when(stockService).create(any(StockBulkRequest.class), anyBoolean());

        stockService.create(request);
        verify(stockService, times(1)).create(any(StockBulkRequest.class), eq(false));
    }

    @Test
    @DisplayName("should call update with isBulk false")
    void shouldCallUpdateWithIsBulkFalse() {
        StockRequest request = StockRequestTestBuilder.builder().withStock().withRequestInfo().build();
        doReturn(Collections.singletonList(request.getStock()))
                .when(stockService).update(any(StockBulkRequest.class), anyBoolean());

        stockService.update(request);
        verify(stockService, times(1)).update(any(StockBulkRequest.class), eq(false));
    }

    @Test
    @DisplayName("should call update with isBulk false")
    void shouldCallDeleteWithIsBulkFalse() {
        StockRequest request = StockRequestTestBuilder.builder().withStock().withRequestInfo().build();
        doReturn(Collections.singletonList(request.getStock()))
                .when(stockService).delete(any(StockBulkRequest.class), anyBoolean());

        stockService.delete(request);
        verify(stockService, times(1)).delete(any(StockBulkRequest.class), eq(false));
    }
}
