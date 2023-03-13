package org.egov.stock.service;

import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.models.stock.StockRequest;
import org.egov.common.validator.Validator;
import org.egov.stock.config.StockConfiguration;
import org.egov.stock.helper.StockBulkRequestTestBuilder;
import org.egov.stock.helper.StockRequestTestBuilder;
import org.egov.stock.repository.StockRepository;
import org.egov.stock.service.enrichment.StockEnrichmentService;
import org.egov.stock.validator.stock.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Spy
    @InjectMocks
    private StockService stockService;

    @Mock
    private StockRepository repository;

    @Mock
    private SIsDeletedValidator stockIsDeletedValidator;

    @Mock
    private SNonExistentValidator stockNonExistentValidator;

    @Mock
    private SNullIdValidator stockNullIdValidator;

    @Mock
    private SProductVariantIdValidator stockProductVariantIdValidator;

    @Mock
    private SRowVersionValidator stockRwoVersionValidator;

    @Mock
    private SUniqueEntityValidator stockUniqueEntityValidator;

    @Mock
    private StockEnrichmentService enrichmentService;

    @Mock
    private StockConfiguration configuration;

    List<Validator<StockBulkRequest, Stock>> validators;

    @BeforeEach
    void setUp() {
        validators = Arrays.asList(stockIsDeletedValidator, stockNonExistentValidator, stockNullIdValidator,
                stockProductVariantIdValidator, stockRwoVersionValidator, stockUniqueEntityValidator);
        ReflectionTestUtils.setField(stockService, "validators", validators);

        ReflectionTestUtils.setField(stockService, "isApplicableForCreate",
                (Predicate<Validator<StockBulkRequest, Stock>>) validator ->
                        validator.getClass().equals(SProductVariantIdValidator.class));

        lenient().when(configuration.getCreateStockTopic()).thenReturn("create-stock-topic");
        lenient().when(configuration.getUpdateStockTopic()).thenReturn("update-stock-topic");
        lenient().when(configuration.getDeleteStockTopic()).thenReturn("delete-stock-topic");
    }


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
    @DisplayName("should call delete with isBulk false")
    void shouldCallDeleteWithIsBulkFalse() {
        StockRequest request = StockRequestTestBuilder.builder().withStock().withRequestInfo().build();
        doReturn(Collections.singletonList(request.getStock()))
                .when(stockService).delete(any(StockBulkRequest.class), anyBoolean());

        stockService.delete(request);

        verify(stockService, times(1)).delete(any(StockBulkRequest.class), eq(false));
    }

    @Test
    @DisplayName("should not call kafka topic if no valid stock found for create")
    void shouldNotCallKafkaTopicCreate() {
        StockBulkRequest stockBulkRequest = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();
        stockBulkRequest.getStock().get(0).setHasErrors(true);

        List<Stock> stock = stockService.create(stockBulkRequest, false);

        assertEquals(0, stock.size());
        verify(repository, times(0)).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should call kafka topic if valid stock found for create")
    void shouldCallKafkaTopicCreate() {
        StockBulkRequest stockBulkRequest = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        List<Stock> stock = stockService.create(stockBulkRequest, false);

        assertEquals(1, stock.size());
        verify(repository, times(1)).save(anyList(), eq("create-stock-topic"));
    }

    @Test
    @DisplayName("should not call kafka topic if no valid stock found for update")
    void shouldNotCallKafkaTopicUpdate() {
        StockBulkRequest stockBulkRequest = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();
        stockBulkRequest.getStock().get(0).setHasErrors(true);

        List<Stock> stock = stockService.update(stockBulkRequest, false);

        assertEquals(0, stock.size());
        verify(repository, times(0)).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should call kafka topic if valid stock found for update")
    void shouldCallKafkaTopicUpdate() {
        StockBulkRequest stockBulkRequest = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        List<Stock> stock = stockService.update(stockBulkRequest, false);

        assertEquals(1, stock.size());
        verify(repository, times(1)).save(anyList(), eq("update-stock-topic"));
    }

    @Test
    @DisplayName("should not call kafka topic if no valid stock found for delete")
    void shouldNotCallKafkaTopicDelete() {
        StockBulkRequest stockBulkRequest = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();
        stockBulkRequest.getStock().get(0).setHasErrors(true);

        List<Stock> stock = stockService.delete(stockBulkRequest, false);

        assertEquals(0, stock.size());
        verify(repository, times(0)).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should call kafka topic if valid stock found for delete")
    void shouldCallKafkaTopicDelete() {
        StockBulkRequest stockBulkRequest = StockBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        List<Stock> stock = stockService.delete(stockBulkRequest, false);

        assertEquals(1, stock.size());
        verify(repository, times(1)).save(anyList(), eq("delete-stock-topic"));
    }
}
