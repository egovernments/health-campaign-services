package org.egov.excelingestion.service;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.excelingestion.repository.ProcessingRepository;
import org.egov.excelingestion.web.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessingServiceTest {

    @Mock
    private ProcessingRepository processingRepository;

    private ProcessingService processingService;

    @BeforeEach
    void setUp() {
        processingService = new ProcessingService(processingRepository, null, null);
    }

    @Test
    void shouldReturnValidResponseForBasicProcessSearch() throws InvalidTenantIdException {
        // Given
        ProcessingSearchRequest request = createSearchRequest("dev", null, null);
        List<ProcessResource> mockData = Arrays.asList(
                createProcessResource("id1", "dev", "pending"),
                createProcessResource("id2", "dev", "completed")
        );
        
        when(processingRepository.search(any())).thenReturn(mockData);
        when(processingRepository.getCount(any())).thenReturn(2L);

        // When
        ProcessingSearchResponse response = processingService.searchProcessing(request);

        // Then
        assertNotNull(response);
        assertEquals(2, response.getTotalCount());
        assertEquals(2, response.getProcessingDetails().size());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    void shouldReturnEmptyResultsWhenNoProcessingDataFound() throws InvalidTenantIdException {
        // Given
        ProcessingSearchRequest request = createSearchRequest("dev", null, null);
        
        when(processingRepository.search(any())).thenReturn(Collections.emptyList());
        when(processingRepository.getCount(any())).thenReturn(0L);

        // When
        ProcessingSearchResponse response = processingService.searchProcessing(request);

        // Then
        assertNotNull(response);
        assertEquals(0, response.getTotalCount());
        assertTrue(response.getProcessingDetails().isEmpty());
    }

    @Test
    void shouldSetDefaultPaginationForProcessSearch() throws InvalidTenantIdException {
        // Given
        ProcessingSearchRequest request = createSearchRequest("dev", null, null);
        
        when(processingRepository.search(any())).thenReturn(Collections.emptyList());
        when(processingRepository.getCount(any())).thenReturn(0L);

        // When
        processingService.searchProcessing(request);

        // Then
        verify(processingRepository).search(argThat(criteria -> 
            criteria.getLimit() == 50 && criteria.getOffset() == 0
        ));
    }

    @Test
    void shouldFilterByProcessingStatus() throws InvalidTenantIdException {
        // Given
        ProcessingSearchRequest request = createSearchRequestWithStatus("dev", 
            Arrays.asList("completed", "failed"));
        
        when(processingRepository.search(any())).thenReturn(Collections.emptyList());
        when(processingRepository.getCount(any())).thenReturn(0L);

        // When
        processingService.searchProcessing(request);

        // Then
        verify(processingRepository).search(argThat(criteria -> 
            criteria.getTenantId().equals("dev") &&
            criteria.getStatuses().size() == 2 &&
            criteria.getStatuses().contains("completed") &&
            criteria.getStatuses().contains("failed")
        ));
    }

    @Test
    void shouldSearchBySpecificProcessingIds() throws InvalidTenantIdException {
        // Given
        ProcessingSearchRequest request = createSearchRequestWithIds("dev", 
            Arrays.asList("proc-id-1", "proc-id-2"));
        List<ProcessResource> mockData = Arrays.asList(
                createProcessResource("proc-id-1", "dev", "completed"),
                createProcessResource("proc-id-2", "dev", "pending")
        );
        
        when(processingRepository.search(any())).thenReturn(mockData);
        when(processingRepository.getCount(any())).thenReturn(2L);

        // When
        ProcessingSearchResponse response = processingService.searchProcessing(request);

        // Then
        assertEquals(2, response.getTotalCount());
        assertEquals(2, response.getProcessingDetails().size());
        verify(processingRepository).search(argThat(criteria -> 
            criteria.getIds().size() == 2 &&
            criteria.getIds().contains("proc-id-1") &&
            criteria.getIds().contains("proc-id-2")
        ));
    }

    @Test
    void shouldReturnConsistentCountAndDataForProcessing() throws InvalidTenantIdException {
        // Given
        ProcessingSearchRequest request = createSearchRequest("dev", 5, 0);
        List<ProcessResource> mockData = Arrays.asList(
                createProcessResource("id1", "dev", "completed"),
                createProcessResource("id2", "dev", "pending")
        );
        
        when(processingRepository.search(any())).thenReturn(mockData);
        when(processingRepository.getCount(any())).thenReturn(25L); // Total 25, showing 2

        // When
        ProcessingSearchResponse response = processingService.searchProcessing(request);

        // Then
        assertEquals(25, response.getTotalCount()); // Total count
        assertEquals(2, response.getProcessingDetails().size()); // Current page data
        // Verify both repository calls use same criteria
        verify(processingRepository, times(1)).search(any());
        verify(processingRepository, times(1)).getCount(any());
    }

    @Test
    void shouldThrowExceptionForInvalidTenantInProcessSearch() throws InvalidTenantIdException {
        // Given
        ProcessingSearchRequest request = createSearchRequest("invalid-tenant", null, null);
        
        when(processingRepository.search(any())).thenThrow(new InvalidTenantIdException("Invalid tenant"));

        // When & Then
        assertThrows(InvalidTenantIdException.class, () -> 
            processingService.searchProcessing(request)
        );
    }

    // Helper methods
    private ProcessingSearchRequest createSearchRequest(String tenantId, Integer limit, Integer offset) {
        ProcessingSearchCriteria criteria = ProcessingSearchCriteria.builder()
                .tenantId(tenantId)
                .limit(limit)
                .offset(offset)
                .build();
        
        RequestInfo requestInfo = RequestInfo.builder()
                .apiId("test-api")
                .ver("1.0")
                .ts(System.currentTimeMillis())
                .build();
        
        return ProcessingSearchRequest.builder()
                .requestInfo(requestInfo)
                .processingSearchCriteria(criteria)
                .build();
    }

    private ProcessingSearchRequest createSearchRequestWithStatus(String tenantId, List<String> statuses) {
        ProcessingSearchCriteria criteria = ProcessingSearchCriteria.builder()
                .tenantId(tenantId)
                .statuses(statuses)
                .build();
        
        RequestInfo requestInfo = RequestInfo.builder()
                .apiId("test-api")
                .ver("1.0")
                .ts(System.currentTimeMillis())
                .build();
        
        return ProcessingSearchRequest.builder()
                .requestInfo(requestInfo)
                .processingSearchCriteria(criteria)
                .build();
    }

    private ProcessingSearchRequest createSearchRequestWithIds(String tenantId, List<String> ids) {
        ProcessingSearchCriteria criteria = ProcessingSearchCriteria.builder()
                .tenantId(tenantId)
                .ids(ids)
                .build();
        
        RequestInfo requestInfo = RequestInfo.builder()
                .apiId("test-api")
                .ver("1.0")
                .ts(System.currentTimeMillis())
                .build();
        
        return ProcessingSearchRequest.builder()
                .requestInfo(requestInfo)
                .processingSearchCriteria(criteria)
                .build();
    }

    private ProcessResource createProcessResource(String id, String tenantId, String status) {
        return ProcessResource.builder()
                .id(id)
                .tenantId(tenantId)
                .status(status)
                .type("EXCEL")
                .hierarchyType("ADMIN")
                .referenceId("ref-" + id)
                .fileStoreId("file-" + id)
                .build();
    }
}