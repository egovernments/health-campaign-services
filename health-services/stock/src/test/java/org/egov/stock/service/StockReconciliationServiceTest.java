package org.egov.stock.service;

import org.egov.common.validator.Validator;
import org.egov.stock.config.StockReconciliationConfiguration;
import org.egov.stock.helper.StockReconciliationBulkRequestTestBuilder;
import org.egov.stock.helper.StockReconciliationRequestTestBuilder;
import org.egov.stock.repository.StockReconciliationRepository;
import org.egov.stock.service.enrichment.StockReconciliationEnrichmentService;
import org.egov.stock.validator.stock.SProductVariantIdValidator;
import org.egov.stock.validator.stockreconciliation.SrIsDeletedValidator;
import org.egov.stock.validator.stockreconciliation.SrNonExistentValidator;
import org.egov.stock.validator.stockreconciliation.SrNullIdValidator;
import org.egov.stock.validator.stockreconciliation.SrProductVariantIdValidator;
import org.egov.stock.validator.stockreconciliation.SrRowVersionValidator;
import org.egov.stock.validator.stockreconciliation.SrUniqueEntityValidator;
import org.egov.stock.web.models.Stock;
import org.egov.stock.web.models.StockReconciliation;
import org.egov.stock.web.models.StockReconciliationBulkRequest;
import org.egov.stock.web.models.StockReconciliationRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockReconciliationServiceTest {

    @Spy
    @InjectMocks
    private StockReconciliationService stockService;

    @Mock
    private StockReconciliationRepository repository;

    @Mock
    private SrIsDeletedValidator stockIsDeletedValidator;

    @Mock
    private SrNonExistentValidator stockNonExistentValidator;

    @Mock
    private SrNullIdValidator stockNullIdValidator;

    @Mock
    private SrProductVariantIdValidator stockProductVariantIdValidator;

    @Mock
    private SrRowVersionValidator stockRwoVersionValidator;

    @Mock
    private SrUniqueEntityValidator stockUniqueEntityValidator;

    @Mock
    private StockReconciliationEnrichmentService enrichmentService;

    @Mock
    private StockReconciliationConfiguration configuration;

    List<Validator<StockReconciliationBulkRequest, StockReconciliation>> validators;

    @BeforeEach
    void setUp() {
        validators = Arrays.asList(stockIsDeletedValidator, stockNonExistentValidator, stockNullIdValidator,
                stockProductVariantIdValidator, stockRwoVersionValidator, stockUniqueEntityValidator);
        ReflectionTestUtils.setField(stockService, "validators", validators);

        ReflectionTestUtils.setField(stockService, "isApplicableForCreate",
                (Predicate<Validator<StockReconciliationBulkRequest, Stock>>) validator ->
                        validator.getClass().equals(SProductVariantIdValidator.class));

        lenient().when(configuration.getCreateStockReconciliationTopic()).thenReturn("create-stock-topic");
        lenient().when(configuration.getUpdateStockReconciliationTopic()).thenReturn("update-stock-topic");
        lenient().when(configuration.getDeleteStockReconciliationTopic()).thenReturn("delete-stock-topic");
    }


    @Test
    @DisplayName("should call create with isBulk false")
    void shouldCallCreateWithIsBulkFalse() {
        StockReconciliationRequest request = StockReconciliationRequestTestBuilder.builder().withReconciliation().withRequestInfo().build();
        doReturn(Collections.singletonList(request.getStockReconciliation()))
                .when(stockService).create(any(StockReconciliationBulkRequest.class), anyBoolean());

        stockService.create(request);

        verify(stockService, times(1)).create(any(StockReconciliationBulkRequest.class), eq(false));
    }

    @Test
    @DisplayName("should call update with isBulk false")
    void shouldCallUpdateWithIsBulkFalse() {
        StockReconciliationRequest request = StockReconciliationRequestTestBuilder.builder().withReconciliation().withRequestInfo().build();
        doReturn(Collections.singletonList(request.getStockReconciliation()))
                .when(stockService).update(any(StockReconciliationBulkRequest.class), anyBoolean());

        stockService.update(request);

        verify(stockService, times(1)).update(any(StockReconciliationBulkRequest.class), eq(false));
    }

    @Test
    @DisplayName("should call update with isBulk false")
    void shouldCallDeleteWithIsBulkFalse() {
        StockReconciliationRequest request = StockReconciliationRequestTestBuilder.builder().withReconciliation().withRequestInfo().build();
        doReturn(Collections.singletonList(request.getStockReconciliation()))
                .when(stockService).delete(any(StockReconciliationBulkRequest.class), anyBoolean());

        stockService.delete(request);

        verify(stockService, times(1)).delete(any(StockReconciliationBulkRequest.class), eq(false));
    }

    @Test
    @DisplayName("should not call kafka topic if no valid stock found for create")
    void shouldNotCallKafkaTopicCreate() {
        StockReconciliationBulkRequest StockReconciliationBulkRequest = StockReconciliationBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();
        StockReconciliationBulkRequest.getStockReconciliation().get(0).setHasErrors(true);

        List<StockReconciliation> stock = stockService.create(StockReconciliationBulkRequest, false);

        assertEquals(0, stock.size());
        verify(repository, times(0)).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should call kafka topic if valid stock found for create")
    void shouldCallKafkaTopicCreate() {
        StockReconciliationBulkRequest StockReconciliationBulkRequest = StockReconciliationBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        List<StockReconciliation> stock = stockService.create(StockReconciliationBulkRequest, false);

        assertEquals(1, stock.size());
        verify(repository, times(1)).save(anyList(), eq("create-stock-topic"));
    }

    @Test
    @DisplayName("should not call kafka topic if no valid stock found for update")
    void shouldNotCallKafkaTopicUpdate() {
        StockReconciliationBulkRequest StockReconciliationBulkRequest = StockReconciliationBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();
        StockReconciliationBulkRequest.getStockReconciliation().get(0).setHasErrors(true);

        List<StockReconciliation> stock = stockService.update(StockReconciliationBulkRequest, false);

        assertEquals(0, stock.size());
        verify(repository, times(0)).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should call kafka topic if valid stock found for update")
    void shouldCallKafkaTopicUpdate() {
        StockReconciliationBulkRequest StockReconciliationBulkRequest = StockReconciliationBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        List<StockReconciliation> stock = stockService.update(StockReconciliationBulkRequest, false);

        assertEquals(1, stock.size());
        verify(repository, times(1)).save(anyList(), eq("update-stock-topic"));
    }

    @Test
    @DisplayName("should not call kafka topic if no valid stock found for delete")
    void shouldNotCallKafkaTopicDelete() {
        StockReconciliationBulkRequest StockReconciliationBulkRequest = StockReconciliationBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();
        StockReconciliationBulkRequest.getStockReconciliation().get(0).setHasErrors(true);
        
        List<StockReconciliation> stock = stockService.delete(StockReconciliationBulkRequest, false);

        assertEquals(0, stock.size());
        verify(repository, times(0)).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should call kafka topic if valid stock found for delete")
    void shouldCallKafkaTopicDelete() {
        StockReconciliationBulkRequest StockReconciliationBulkRequest = StockReconciliationBulkRequestTestBuilder.builder().withStock().withRequestInfo().build();

        List<StockReconciliation> stock = stockService.delete(StockReconciliationBulkRequest, false);

        assertEquals(1, stock.size());
        verify(repository, times(1)).save(anyList(), eq("delete-stock-topic"));
    }
}
