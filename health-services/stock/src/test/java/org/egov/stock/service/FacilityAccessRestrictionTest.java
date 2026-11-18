package org.egov.stock.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.stock.SenderReceiverType;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.common.models.stock.StockReconciliationSearch;
import org.egov.common.models.stock.StockReconciliationSearchRequest;
import org.egov.common.models.stock.StockSearch;
import org.egov.common.models.stock.StockSearchRequest;
import org.egov.common.models.stock.TransactionType;
import org.egov.stock.config.StockConfiguration;
import org.egov.stock.config.StockReconciliationConfiguration;
import org.egov.stock.repository.StockReconciliationRepository;
import org.egov.stock.repository.StockRepository;
import org.egov.stock.service.enrichment.StockEnrichmentService;
import org.egov.stock.service.enrichment.StockReconciliationEnrichmentService;
import org.egov.stock.validator.stock.SAllowedFacilityValidator;
import org.egov.stock.validator.stockreconciliation.SrAllowedFacilityValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityAccessRestrictionTest {

    private static final String TENANT = "t1";

    @Mock
    private FacilityAccessService facilityAccessService;

    @Mock
    private StockRepository stockRepository;

    private final RequestInfo requestInfo = RequestInfo.builder().build();

    private Stock stock(TransactionType type, String senderId, String receiverId) {
        return Stock.builder().tenantId(TENANT).transactionType(type)
                .senderType(SenderReceiverType.WAREHOUSE).senderId(senderId)
                .receiverType(SenderReceiverType.WAREHOUSE).receiverId(receiverId).build();
    }

    private Map<Stock, List<Error>> validateStock(Stock stock) {
        return new SAllowedFacilityValidator(facilityAccessService).validate(
                StockBulkRequest.builder().requestInfo(requestInfo).stock(Collections.singletonList(stock)).build());
    }

    @Test
    void should_not_lookup_or_fail_when_user_is_unrestricted() {
        when(facilityAccessService.isRestricted(requestInfo)).thenReturn(false);

        assertTrue(validateStock(stock(TransactionType.DISPATCHED, "W1", "W2")).isEmpty());
        verify(facilityAccessService, never()).allowedFacilityIds(any(), anyString());
    }

    @Test
    void should_reject_dispatch_from_non_allowed_sender() {
        when(facilityAccessService.isRestricted(requestInfo)).thenReturn(true);
        when(facilityAccessService.allowedFacilityIds(requestInfo, TENANT)).thenReturn(Set.of("W1"));

        assertEquals(1, validateStock(stock(TransactionType.DISPATCHED, "W9", "W1")).size());
    }

    @Test
    void should_allow_dispatch_from_allowed_sender_to_non_allowed_receiver() {
        when(facilityAccessService.isRestricted(requestInfo)).thenReturn(true);
        when(facilityAccessService.allowedFacilityIds(requestInfo, TENANT)).thenReturn(Set.of("W1"));

        assertTrue(validateStock(stock(TransactionType.DISPATCHED, "W1", "W2")).isEmpty());
    }

    @Test
    void should_reject_reconciliation_for_non_allowed_facility() {
        when(facilityAccessService.isRestricted(requestInfo)).thenReturn(true);
        when(facilityAccessService.allowedFacilityIds(requestInfo, TENANT)).thenReturn(Set.of("W1"));
        StockReconciliation reconciliation = StockReconciliation.builder().tenantId(TENANT).facilityId("W2").build();

        Map<StockReconciliation, List<Error>> errors = new SrAllowedFacilityValidator(facilityAccessService)
                .validate(StockReconciliationBulkRequest.builder().requestInfo(requestInfo)
                        .stockReconciliation(Collections.singletonList(reconciliation)).build());

        assertEquals(1, errors.size());
    }

    @Test
    void should_return_empty_search_without_query_when_restricted_user_has_no_facilities() throws Exception {
        when(facilityAccessService.isRestricted(requestInfo)).thenReturn(true);
        when(facilityAccessService.allowedFacilityIds(requestInfo, TENANT)).thenReturn(Collections.emptySet());
        StockService service = new StockService(stockRepository, Collections.emptyList(), mock(StockConfiguration.class),
                mock(StockEnrichmentService.class), facilityAccessService);

        SearchResponse<Stock> response = service.search(searchRequest(), 10, 0, TENANT, null, false);

        assertEquals(0L, response.getTotalCount());
        assertTrue(response.getResponse().isEmpty());
        verifyNoInteractions(stockRepository);
    }

    @Test
    void should_search_with_unchanged_repository_call_when_user_is_unrestricted() throws Exception {
        when(facilityAccessService.isRestricted(requestInfo)).thenReturn(false);
        StockService service = new StockService(stockRepository, Collections.emptyList(), mock(StockConfiguration.class),
                mock(StockEnrichmentService.class), facilityAccessService);
        StockSearchRequest request = searchRequest();
        when(stockRepository.findWithCount(any(), anyInt(), anyInt(), anyString(), any(), anyBoolean()))
                .thenReturn(SearchResponse.<Stock>builder().response(Collections.emptyList()).totalCount(0L).build());

        service.search(request, 10, 0, TENANT, null, false);

        verify(stockRepository).findWithCount(eq(request.getStock()), eq(10), eq(0), eq(TENANT), eq(null), eq(false));
        verify(facilityAccessService, never()).allowedFacilityIds(any(), anyString());
    }

    @Test
    void should_return_empty_reconciliation_search_without_query_when_restricted_user_has_no_facilities() throws Exception {
        StockReconciliationRepository repository = mock(StockReconciliationRepository.class);
        when(facilityAccessService.isRestricted(requestInfo)).thenReturn(true);
        when(facilityAccessService.allowedFacilityIds(requestInfo, TENANT)).thenReturn(Collections.emptySet());
        StockReconciliationService service = new StockReconciliationService(repository, Collections.emptyList(),
                mock(StockReconciliationConfiguration.class), mock(StockReconciliationEnrichmentService.class), facilityAccessService);
        StockReconciliationSearchRequest request = StockReconciliationSearchRequest.builder()
                .requestInfo(requestInfo).stockReconciliation(StockReconciliationSearch.builder().build()).build();

        SearchResponse<StockReconciliation> response = service.search(request, 10, 0, TENANT, null, false);

        assertEquals(0L, response.getTotalCount());
        verifyNoInteractions(repository);
    }

    private StockSearchRequest searchRequest() {
        return StockSearchRequest.builder().requestInfo(requestInfo).stock(StockSearch.builder().build()).build();
    }
}
