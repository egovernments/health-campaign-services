package org.egov.excelingestion.service;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.producer.Producer;
import org.egov.excelingestion.cache.GenerationCacheService;
import org.egov.excelingestion.config.KafkaTopicConfig;
import org.egov.excelingestion.repository.GeneratedFileRepository;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.web.models.*;
import org.egov.common.contract.request.RequestInfo;
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

    @Mock private GeneratedFileRepository generatedFileRepository;
    @Mock private Producer producer;
    @Mock private ExcelGenerationValidationService validationService;
    @Mock private RequestInfoConverter requestInfoConverter;
    @Mock private KafkaTopicConfig kafkaTopicConfig;
    @Mock private GenerationCacheService generationCacheService;

    private GenerationService generationService;

    @BeforeEach
    void setUp() {
        generationService = new GenerationService(
                generatedFileRepository,
                producer,
                validationService,
                requestInfoConverter,
                kafkaTopicConfig,
                generationCacheService);
    }

    @Test
    void shouldReturnValidResponseForBasicSearch() throws InvalidTenantIdException {
        GenerationSearchRequest request = createSearchRequest("dev", null, null);
        List<GenerateResource> mockData = Arrays.asList(
                createGenerateResource("id1", "dev"),
                createGenerateResource("id2", "dev"));

        when(generatedFileRepository.search(any())).thenReturn(mockData);

        GenerationSearchResponse response = generationService.searchGenerations(request);

        assertNotNull(response);
        assertEquals(2, response.getTotalCount());
        assertEquals(2, response.getGenerationDetails().size());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    void shouldReturnEmptyResultsWhenNoDataFound() throws InvalidTenantIdException {
        GenerationSearchRequest request = createSearchRequest("dev", null, null);
        when(generatedFileRepository.search(any())).thenReturn(Collections.emptyList());

        GenerationSearchResponse response = generationService.searchGenerations(request);

        assertNotNull(response);
        assertEquals(0, response.getTotalCount());
        assertTrue(response.getGenerationDetails().isEmpty());
    }

    @Test
    void shouldHitCacheForSingleReferenceLookup() throws InvalidTenantIdException {
        GenerationSearchCriteria criteria = GenerationSearchCriteria.builder()
                .tenantId("dev")
                .referenceIds(Collections.singletonList("ref-1"))
                .build();
        RequestInfo requestInfo = RequestInfo.builder().apiId("a").ver("1").ts(0L).build();
        GenerationSearchRequest request = GenerationSearchRequest.builder()
                .requestInfo(requestInfo)
                .generationSearchCriteria(criteria)
                .build();

        List<GenerateResource> cached = Arrays.asList(
                createGenerateResource("id1", "dev"),
                createGenerateResource("id2", "dev"));
        when(generationCacheService.getByReference("dev", "ref-1")).thenReturn(cached);

        GenerationSearchResponse response = generationService.searchGenerations(request);

        assertEquals(2, response.getGenerationDetails().size());
        verify(generatedFileRepository, never()).search(any());
    }

    @Test
    void shouldFallThroughToDbAndPopulateCacheOnMiss() throws InvalidTenantIdException {
        GenerationSearchCriteria criteria = GenerationSearchCriteria.builder()
                .tenantId("dev")
                .referenceIds(Collections.singletonList("ref-2"))
                .build();
        RequestInfo requestInfo = RequestInfo.builder().apiId("a").ver("1").ts(0L).build();
        GenerationSearchRequest request = GenerationSearchRequest.builder()
                .requestInfo(requestInfo)
                .generationSearchCriteria(criteria)
                .build();

        when(generationCacheService.getByReference("dev", "ref-2")).thenReturn(null);
        when(generatedFileRepository.search(any())).thenReturn(
                Collections.singletonList(createGenerateResource("id1", "dev")));

        GenerationSearchResponse response = generationService.searchGenerations(request);

        assertEquals(1, response.getGenerationDetails().size());
        verify(generationCacheService).putByReference(eq("dev"), eq("ref-2"), any());
    }

    @Test
    void shouldThrowExceptionForInvalidTenantId() throws InvalidTenantIdException {
        GenerationSearchRequest request = createSearchRequest("invalid-tenant", null, null);
        when(generatedFileRepository.search(any())).thenThrow(new InvalidTenantIdException("Invalid tenant"));

        assertThrows(InvalidTenantIdException.class, () -> generationService.searchGenerations(request));
    }

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

    private GenerateResource createGenerateResource(String id, String tenantId) {
        return GenerateResource.builder()
                .id(id)
                .tenantId(tenantId)
                .status("completed")
                .type("EXCEL")
                .createdTime(System.currentTimeMillis())
                .lastModifiedTime(System.currentTimeMillis())
                .build();
    }
}
