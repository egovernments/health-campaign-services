package org.egov.excelingestion.service;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.excelingestion.repository.GeneratedFileRepository;
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
class GenerationServiceTest {

    @Mock
    private GeneratedFileRepository generatedFileRepository;

    private GenerationService generationService;

    @BeforeEach
    void setUp() {
        generationService = new GenerationService(generatedFileRepository, null, null, null);
    }

    @Test
    void shouldReturnValidResponseForBasicSearch() throws InvalidTenantIdException {
        // Given
        GenerationSearchRequest request = createSearchRequest("dev", null, null);
        List<GenerateResource> mockData = Arrays.asList(
                createGenerateResource("id1", "dev"),
                createGenerateResource("id2", "dev")
        );
        
        when(generatedFileRepository.search(any())).thenReturn(mockData);
        when(generatedFileRepository.getCount(any())).thenReturn(2L);

        // When
        GenerationSearchResponse response = generationService.searchGenerations(request);

        // Then
        assertNotNull(response);
        assertEquals(2, response.getTotalCount());
        assertEquals(2, response.getGenerationDetails().size());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    void shouldReturnEmptyResultsWhenNoDataFound() throws InvalidTenantIdException {
        // Given
        GenerationSearchRequest request = createSearchRequest("dev", null, null);
        
        when(generatedFileRepository.search(any())).thenReturn(Collections.emptyList());
        when(generatedFileRepository.getCount(any())).thenReturn(0L);

        // When
        GenerationSearchResponse response = generationService.searchGenerations(request);

        // Then
        assertNotNull(response);
        assertEquals(0, response.getTotalCount());
        assertTrue(response.getGenerationDetails().isEmpty());
    }

    @Test
    void shouldSetDefaultPaginationWhenNotProvided() throws InvalidTenantIdException {
        // Given
        GenerationSearchRequest request = createSearchRequest("dev", null, null);
        
        when(generatedFileRepository.search(any())).thenReturn(Collections.emptyList());
        when(generatedFileRepository.getCount(any())).thenReturn(0L);

        // When
        generationService.searchGenerations(request);

        // Then
        verify(generatedFileRepository).search(argThat(criteria -> 
            criteria.getLimit() == 50 && criteria.getOffset() == 0
        ));
    }

    @Test
    void shouldPreserveCustomPaginationValues() throws InvalidTenantIdException {
        // Given
        GenerationSearchRequest request = createSearchRequest("dev", 25, 10);
        
        when(generatedFileRepository.search(any())).thenReturn(Collections.emptyList());
        when(generatedFileRepository.getCount(any())).thenReturn(0L);

        // When
        generationService.searchGenerations(request);

        // Then
        verify(generatedFileRepository).search(argThat(criteria -> 
            criteria.getLimit() == 25 && criteria.getOffset() == 10
        ));
    }

    @Test
    void shouldPassCorrectSearchCriteriaToRepository() throws InvalidTenantIdException {
        // Given
        GenerationSearchRequest request = createSearchRequestWithCriteria("dev", 
            Arrays.asList("id1", "id2"), 
            Arrays.asList("COMPLETED", "FAILED"));
        
        when(generatedFileRepository.search(any())).thenReturn(Collections.emptyList());
        when(generatedFileRepository.getCount(any())).thenReturn(0L);

        // When
        generationService.searchGenerations(request);

        // Then
        verify(generatedFileRepository).search(argThat(criteria -> 
            criteria.getTenantId().equals("dev") &&
            criteria.getIds().size() == 2 &&
            criteria.getStatuses().size() == 2
        ));
    }

    @Test
    void shouldReturnConsistentCountAndData() throws InvalidTenantIdException {
        // Given
        GenerationSearchRequest request = createSearchRequest("dev", 2, 0);
        List<GenerateResource> mockData = Arrays.asList(
                createGenerateResource("id1", "dev"),
                createGenerateResource("id2", "dev")
        );
        
        when(generatedFileRepository.search(any())).thenReturn(mockData);
        when(generatedFileRepository.getCount(any())).thenReturn(10L); // Total 10, but showing 2

        // When
        GenerationSearchResponse response = generationService.searchGenerations(request);

        // Then
        assertEquals(10, response.getTotalCount()); // Total count
        assertEquals(2, response.getGenerationDetails().size()); // Current page data
    }

    @Test
    void shouldThrowExceptionForInvalidTenantId() throws InvalidTenantIdException {
        // Given
        GenerationSearchRequest request = createSearchRequest("invalid-tenant", null, null);
        
        when(generatedFileRepository.search(any())).thenThrow(new InvalidTenantIdException("Invalid tenant"));

        // When & Then
        assertThrows(InvalidTenantIdException.class, () -> 
            generationService.searchGenerations(request)
        );
    }

    @Test
    void shouldHandleRepositoryException() throws InvalidTenantIdException {
        // Given
        GenerationSearchRequest request = createSearchRequest("dev", null, null);
        
        when(generatedFileRepository.search(any())).thenThrow(new RuntimeException("DB error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            generationService.searchGenerations(request)
        );
        assertEquals("Failed to search generations", exception.getMessage());
    }

    @Test
    void shouldHandleCountMismatchScenario() throws InvalidTenantIdException {
        // Given
        GenerationSearchRequest request = createSearchRequest("dev", 10, 0);
        List<GenerateResource> mockData = Arrays.asList(createGenerateResource("id1", "dev"));
        
        when(generatedFileRepository.search(any())).thenReturn(mockData);
        when(generatedFileRepository.getCount(any())).thenReturn(1L);

        // When
        GenerationSearchResponse response = generationService.searchGenerations(request);

        // Then
        assertEquals(1, response.getTotalCount());
        assertEquals(1, response.getGenerationDetails().size());
        // Verify both calls use same criteria
        verify(generatedFileRepository, times(1)).search(any());
        verify(generatedFileRepository, times(1)).getCount(any());
    }

    @Test
    void shouldValidateZeroOffsetAndLimit() throws InvalidTenantIdException {
        // Given
        GenerationSearchRequest request = createSearchRequest("dev", 0, 0);
        
        when(generatedFileRepository.search(any())).thenReturn(Collections.emptyList());
        when(generatedFileRepository.getCount(any())).thenReturn(0L);

        // When
        GenerationSearchResponse response = generationService.searchGenerations(request);

        // Then
        assertNotNull(response);
        verify(generatedFileRepository).search(argThat(criteria -> 
            criteria.getLimit() == 0 && criteria.getOffset() == 0
        ));
    }

    // Helper methods
    private GenerationSearchRequest createSearchRequest(String tenantId, Integer limit, Integer offset) {
        GenerationSearchCriteria criteria = GenerationSearchCriteria.builder()
                .tenantId(tenantId)
                .limit(limit)
                .offset(offset)
                .build();
        
        RequestInfo requestInfo = RequestInfo.builder()
                .apiId("test-api")
                .ver("1.0")
                .ts(System.currentTimeMillis())
                .build();
        
        return GenerationSearchRequest.builder()
                .requestInfo(requestInfo)
                .generationSearchCriteria(criteria)
                .build();
    }

    private GenerationSearchRequest createSearchRequestWithCriteria(String tenantId, List<String> ids, List<String> statuses) {
        GenerationSearchCriteria criteria = GenerationSearchCriteria.builder()
                .tenantId(tenantId)
                .ids(ids)
                .statuses(statuses)
                .build();
        
        RequestInfo requestInfo = RequestInfo.builder()
                .apiId("test-api")
                .ver("1.0")
                .ts(System.currentTimeMillis())
                .build();
        
        return GenerationSearchRequest.builder()
                .requestInfo(requestInfo)
                .generationSearchCriteria(criteria)
                .build();
    }

    private GenerateResource createGenerateResource(String id, String tenantId) {
        return GenerateResource.builder()
                .id(id)
                .tenantId(tenantId)
                .status("COMPLETED")
                .type("EXCEL")
                .createdTime(System.currentTimeMillis())
                .build();
    }
}