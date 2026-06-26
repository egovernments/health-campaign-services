package org.egov.excelingestion.service;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.producer.Producer;
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

    private GenerationService generationService;

    @BeforeEach
    void setUp() {
        generationService = new GenerationService(
                generatedFileRepository,
                producer,
                validationService,
                requestInfoConverter,
                kafkaTopicConfig);
    }

    @Test
    void shouldReturnValidResponseForBasicSearch() throws InvalidTenantIdException {
        GenerationSearchRequest request = createSearchRequest("dev");
        List<GenerateResource> mockData = Arrays.asList(
                createGenerateResource("id1", "dev", 100L),
                createGenerateResource("id2", "dev", 200L));

        when(generatedFileRepository.search(any())).thenReturn(mockData);

        GenerationSearchResponse response = generationService.searchGenerations(request);

        assertNotNull(response);
        assertEquals(2, response.getTotalCount());
        assertEquals(2, response.getGenerationDetails().size());
        // sorted by lastModifiedTime desc
        assertEquals("id2", response.getGenerationDetails().get(0).getId());
        assertEquals("id1", response.getGenerationDetails().get(1).getId());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    void shouldReturnEmptyResultsWhenNoDataFound() throws InvalidTenantIdException {
        GenerationSearchRequest request = createSearchRequest("dev");
        when(generatedFileRepository.search(any())).thenReturn(Collections.emptyList());

        GenerationSearchResponse response = generationService.searchGenerations(request);

        assertNotNull(response);
        assertEquals(0, response.getTotalCount());
        assertTrue(response.getGenerationDetails().isEmpty());
    }

    @Test
    void shouldThrowExceptionForInvalidTenantId() throws InvalidTenantIdException {
        GenerationSearchRequest request = createSearchRequest("invalid-tenant");
        when(generatedFileRepository.search(any())).thenThrow(new InvalidTenantIdException("Invalid tenant"));

        assertThrows(InvalidTenantIdException.class, () -> generationService.searchGenerations(request));
    }

    private GenerationSearchRequest createSearchRequest(String tenantId) {
        GenerationSearchCriteria criteria = GenerationSearchCriteria.builder()
                .tenantId(tenantId)
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

    private GenerateResource createGenerateResource(String id, String tenantId, long lastModifiedTime) {
        return GenerateResource.builder()
                .id(id)
                .tenantId(tenantId)
                .status("completed")
                .type("EXCEL")
                .createdTime(System.currentTimeMillis())
                .lastModifiedTime(lastModifiedTime)
                .build();
    }
}
